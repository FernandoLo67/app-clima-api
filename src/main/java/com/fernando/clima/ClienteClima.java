package com.fernando.clima;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente HTTP de Open-Meteo, una API publica y gratuita que no pide clave.
 *
 * <p>Se apoya en {@link HttpClient}, que forma parte del JDK desde Java 11, asi
 * que el proyecto sigue sin dependencias externas — igual que el parser JSON,
 * que esta escrito a mano en {@link Json}.
 *
 * <h2>Todo lo que sale de aqui es {@link ClimaException}</h2>
 *
 * Quien usa esta clase pide el clima de una ciudad. No sabe que por dentro hay
 * un parser JSON, y no tiene por que: si tuviera que atrapar tambien
 * {@link JsonException} estaria acoplado a un detalle que puede cambiar manana.
 * La causa original va encadenada, asi que no se pierde nada al depurar.
 *
 * <h2>El {@link HttpClient} se inyecta</h2>
 *
 * Es lo que permite probar esta clase sin red: un {@code HttpClient} de mentira
 * devuelve respuestas fijas y las pruebas comprueban que se arma bien la URL,
 * que un 404 se convierte en {@code ClimaException} y que un cuerpo mal formado
 * tambien. Sin la inyeccion, la unica forma de probarlo seria llamar a la API de
 * verdad — lento, dependiente de la red, y con resultados que cambian.
 */
public class ClienteClima {

    private static final String URL_GEOCODIFICACION =
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&format=json&name=";
    private static final String URL_PRONOSTICO =
            "https://api.open-meteo.com/v1/forecast?current_weather=true";

    private final HttpClient http;

    public ClienteClima() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /**
     * @param http cliente HTTP a usar; las pruebas inyectan uno de mentira
     * @throws IllegalArgumentException si es nulo
     */
    public ClienteClima(HttpClient http) {
        if (http == null) {
            throw new IllegalArgumentException("El cliente HTTP no puede ser nulo");
        }
        this.http = http;
    }

    /**
     * @param nombre nombre de la ciudad; se codifica para la URL, asi que los
     *               espacios y los acentos funcionan
     * @return la primera coincidencia
     * @throws IllegalArgumentException si el nombre esta vacio
     * @throws ClimaException           si falla la red o la respuesta no sirve
     */
    public Ciudad buscarCiudad(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la ciudad no puede estar vacio");
        }
        String url = URL_GEOCODIFICACION + URLEncoder.encode(nombre.trim(), StandardCharsets.UTF_8);
        return Ciudad.desdeJson(pedir(url));
    }

    /**
     * @param ciudad ciudad ya localizada
     * @return su clima actual
     * @throws IllegalArgumentException si la ciudad es nula
     * @throws ClimaException           si falla la red o la respuesta no sirve
     */
    public Clima obtenerClima(Ciudad ciudad) {
        if (ciudad == null) {
            throw new IllegalArgumentException("La ciudad no puede ser nula");
        }
        String url = String.format("%s&latitude=%s&longitude=%s",
                URL_PRONOSTICO, ciudad.latitud(), ciudad.longitud());
        return Clima.desdeJson(ciudad.toString(), pedir(url));
    }

    /**
     * Atajo: del nombre de la ciudad directo al clima. Hace dos peticiones.
     *
     * @param nombreCiudad nombre de la ciudad
     * @return su clima actual
     */
    public Clima obtenerClima(String nombreCiudad) {
        return obtenerClima(buscarCiudad(nombreCiudad));
    }

    private String pedir(String url) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new ClimaException("La API respondio con codigo " + respuesta.statusCode());
            }
            return respuesta.body();
        } catch (IOException e) {
            throw new ClimaException("No se pudo conectar con la API del clima: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClimaException("La consulta fue interrumpida", e);
        }
    }
}
