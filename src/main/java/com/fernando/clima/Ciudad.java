package com.fernando.clima;

import java.util.Objects;

/**
 * Una ciudad localizada, con sus coordenadas.
 *
 * @param nombre    nombre de la ciudad
 * @param pais      pais, o cadena vacia si la API no lo trae
 * @param latitud   entre -90 y 90
 * @param longitud  entre -180 y 180
 */
public final class Ciudad {

    private final String nombre;
    private final String pais;
    private final double latitud;
    private final double longitud;

    public Ciudad(String nombre, String pais, double latitud, double longitud) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la ciudad no puede estar vacio");
        }
        if (latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitud);
        }
        if (longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitud);
        }
        this.nombre = nombre;
        this.pais = pais;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String nombre() {
        return nombre;
    }

    public String pais() {
        return pais;
    }

    public double latitud() {
        return latitud;
    }

    public double longitud() {
        return longitud;
    }

    /**
     * Construye la ciudad a partir del JSON de geocodificacion de Open-Meteo.
     *
     * <p>Todo lo que pueda fallar sale como {@link ClimaException}. La version
     * anterior dejaba escapar {@code Json.JsonException} — una clase anidada del
     * parser — asi que quien usaba esta clase tenia que atrapar un detalle de
     * implementacion para protegerse. Ver {@link ClimaException}.
     *
     * @param json respuesta de la API de geocodificacion
     * @return la primera ciudad del resultado
     * @throws ClimaException si el JSON esta mal formado, no trae resultados, o
     *                        le falta algun campo
     */
    public static Ciudad desdeJson(String json) {
        try {
            Object raiz = Json.parsear(json);
            Object resultados = Json.buscar(raiz, "results");
            if (!(resultados instanceof java.util.List<?>) || ((java.util.List<?>) resultados).isEmpty()) {
                throw new ClimaException("No se encontro ninguna ciudad con ese nombre");
            }
            Object pais = Json.buscar(raiz, "results.0.country");
            return new Ciudad(
                    Json.buscarTexto(raiz, "results.0.name"),
                    pais instanceof String ? (String) pais : "",
                    Json.buscarNumero(raiz, "results.0.latitude"),
                    Json.buscarNumero(raiz, "results.0.longitude"));
        } catch (JsonException e) {
            throw new ClimaException("La respuesta de geocodificacion no tiene el formato esperado: "
                    + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ClimaException("La API devolvio una ciudad con datos invalidos: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ciudad)) {
            return false;
        }
        Ciudad ciudad = (Ciudad) o;
        return Double.compare(ciudad.latitud, latitud) == 0
                && Double.compare(ciudad.longitud, longitud) == 0
                && Objects.equals(nombre, ciudad.nombre)
                && Objects.equals(pais, ciudad.pais);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nombre, pais, latitud, longitud);
    }

    @Override
    public String toString() {
        return pais.isBlank() ? nombre : nombre + ", " + pais;
    }
}
