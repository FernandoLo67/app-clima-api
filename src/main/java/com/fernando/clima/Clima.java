package com.fernando.clima;

import java.util.Map;
import java.util.Objects;

/**
 * El clima actual de una ciudad.
 *
 * @param ciudad        nombre a mostrar
 * @param temperaturaC  temperatura en grados Celsius
 * @param vientoKmh     velocidad del viento
 * @param codigoTiempo  codigo WMO de la condicion
 * @param esDeDia       si en ese momento es de dia
 */
public final class Clima {

    private final String ciudad;
    private final double temperaturaC;
    private final double vientoKmh;
    private final int codigoTiempo;
    private final boolean esDeDia;

    /** Codigos WMO que usa Open-Meteo. */
    private static final Map<Integer, String> DESCRIPCIONES = Map.ofEntries(
            Map.entry(0, "Despejado"),
            Map.entry(1, "Mayormente despejado"),
            Map.entry(2, "Parcialmente nublado"),
            Map.entry(3, "Nublado"),
            Map.entry(45, "Neblina"),
            Map.entry(48, "Neblina con escarcha"),
            Map.entry(51, "Llovizna ligera"),
            Map.entry(53, "Llovizna moderada"),
            Map.entry(55, "Llovizna intensa"),
            Map.entry(61, "Lluvia ligera"),
            Map.entry(63, "Lluvia moderada"),
            Map.entry(65, "Lluvia fuerte"),
            Map.entry(71, "Nieve ligera"),
            Map.entry(73, "Nieve moderada"),
            Map.entry(75, "Nieve intensa"),
            Map.entry(80, "Chubascos ligeros"),
            Map.entry(81, "Chubascos moderados"),
            Map.entry(82, "Chubascos violentos"),
            Map.entry(95, "Tormenta"),
            Map.entry(96, "Tormenta con granizo ligero"),
            Map.entry(99, "Tormenta con granizo fuerte"));

    public Clima(String ciudad, double temperaturaC, double vientoKmh, int codigoTiempo, boolean esDeDia) {
        this.ciudad = ciudad;
        this.temperaturaC = temperaturaC;
        this.vientoKmh = vientoKmh;
        this.codigoTiempo = codigoTiempo;
        this.esDeDia = esDeDia;
    }

    public String ciudad() {
        return ciudad;
    }

    public double temperaturaC() {
        return temperaturaC;
    }

    public double vientoKmh() {
        return vientoKmh;
    }

    public int codigoTiempo() {
        return codigoTiempo;
    }

    public boolean esDeDia() {
        return esDeDia;
    }

    /** @return la condicion en palabras, o un texto con el codigo si no se conoce */
    public String descripcion() {
        return DESCRIPCIONES.getOrDefault(codigoTiempo, "Condicion desconocida (codigo " + codigoTiempo + ")");
    }

    /** @return la misma temperatura en grados Fahrenheit */
    public double temperaturaF() {
        return temperaturaC * 9.0 / 5.0 + 32.0;
    }

    /**
     * Construye el clima a partir del JSON de pronostico actual de Open-Meteo.
     *
     * <p>Todo lo que pueda fallar sale como {@link ClimaException}: ver esa
     * clase para el motivo.
     *
     * @param ciudad nombre a mostrar
     * @param json   respuesta de la API de pronostico
     * @return el clima actual
     * @throws ClimaException si el JSON esta mal formado o le falta algun campo
     */
    public static Clima desdeJson(String ciudad, String json) {
        try {
            Object raiz = Json.parsear(json);
            if (Json.buscar(raiz, "current_weather") == null) {
                throw new ClimaException("La respuesta no contiene datos de clima actual");
            }
            return new Clima(
                    ciudad,
                    Json.buscarNumero(raiz, "current_weather.temperature"),
                    Json.buscarNumero(raiz, "current_weather.windspeed"),
                    (int) Json.buscarNumero(raiz, "current_weather.weathercode"),
                    Json.buscarNumero(raiz, "current_weather.is_day") == 1.0);
        } catch (JsonException e) {
            throw new ClimaException("La respuesta del pronostico no tiene el formato esperado: "
                    + e.getMessage(), e);
        }
    }

    /** @return el clima en varias lineas, listo para imprimir */
    public String resumen() {
        return String.format("%s\n  %.1f C (%.1f F) - %s\n  Viento: %.1f km/h\n  Momento del dia: %s",
                ciudad, temperaturaC, temperaturaF(), descripcion(), vientoKmh, esDeDia ? "dia" : "noche");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Clima)) {
            return false;
        }
        Clima clima = (Clima) o;
        return Double.compare(clima.temperaturaC, temperaturaC) == 0
                && Double.compare(clima.vientoKmh, vientoKmh) == 0
                && codigoTiempo == clima.codigoTiempo
                && esDeDia == clima.esDeDia
                && Objects.equals(ciudad, clima.ciudad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ciudad, temperaturaC, vientoKmh, codigoTiempo, esDeDia);
    }

    @Override
    public String toString() {
        return "Clima[ciudad=" + ciudad + ", temperaturaC=" + temperaturaC + ", vientoKmh=" + vientoKmh
                + ", codigoTiempo=" + codigoTiempo + ", esDeDia=" + esDeDia + "]";
    }
}
