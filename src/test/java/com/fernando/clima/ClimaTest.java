package com.fernando.clima;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Estas pruebas no tocan la red: usan respuestas JSON de ejemplo con la misma
 * forma que devuelve Open-Meteo, para validar el mapeo y las reglas propias.
 */
class ClimaTest {

    private static final String JSON_PRONOSTICO =
            "{\n"
            + "  \"latitude\": 14.625,\n"
            + "  \"longitude\": -90.5,\n"
            + "  \"current_weather\": {\n"
            + "    \"temperature\": 23.4,\n"
            + "    \"windspeed\": 11.2,\n"
            + "    \"winddirection\": 120,\n"
            + "    \"weathercode\": 61,\n"
            + "    \"is_day\": 1,\n"
            + "    \"time\": \"2026-08-22T12:00\"\n"
            + "  }\n"
            + "}";

    private static final String JSON_CIUDAD =
            "{\n"
            + "  \"results\": [\n"
            + "    {\n"
            + "      \"id\": 3598132,\n"
            + "      \"name\": \"Ciudad de Guatemala\",\n"
            + "      \"latitude\": 14.64072,\n"
            + "      \"longitude\": -90.51327,\n"
            + "      \"country\": \"Guatemala\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    @Test
    void mapeaElClimaDesdeLaRespuestaDeLaApi() {
        Clima clima = Clima.desdeJson("Ciudad de Guatemala", JSON_PRONOSTICO);

        assertEquals("Ciudad de Guatemala", clima.ciudad());
        assertEquals(23.4, clima.temperaturaC(), 0.001);
        assertEquals(11.2, clima.vientoKmh(), 0.001);
        assertEquals(61, clima.codigoTiempo());
        assertTrue(clima.esDeDia());
    }

    @Test
    void traduceElCodigoWmoADescripcion() {
        assertEquals("Lluvia ligera", Clima.desdeJson("X", JSON_PRONOSTICO).descripcion());
    }

    @Test
    void codigoDesconocidoNoRompeLaDescripcion() {
        Clima clima = new Clima("X", 20, 5, 777, true);
        assertTrue(clima.descripcion().contains("777"));
    }

    @Test
    void convierteCelsiusAFahrenheit() {
        assertEquals(32.0, new Clima("X", 0, 0, 0, true).temperaturaF(), 0.001);
        assertEquals(212.0, new Clima("X", 100, 0, 0, true).temperaturaF(), 0.001);
    }

    @Test
    void detectaLaNoche() {
        String jsonNoche = JSON_PRONOSTICO.replace("\"is_day\": 1", "\"is_day\": 0");
        assertFalse(Clima.desdeJson("X", jsonNoche).esDeDia());
    }

    @Test
    void respuestaSinClimaActualLanzaClimaException() {
        assertThrows(ClimaException.class, () -> Clima.desdeJson("X", "{\"latitude\":0}"));
    }

    @Test
    void elResumenIncluyeLosDatosPrincipales() {
        String resumen = Clima.desdeJson("Ciudad de Guatemala", JSON_PRONOSTICO).resumen();
        assertTrue(resumen.contains("Ciudad de Guatemala"));
        assertTrue(resumen.contains("23.4"));
        assertTrue(resumen.contains("Lluvia ligera"));
        assertTrue(resumen.contains("11.2"));
    }

    @Test
    void mapeaLaCiudadDesdeLaGeocodificacion() {
        Ciudad ciudad = Ciudad.desdeJson(JSON_CIUDAD);

        assertEquals("Ciudad de Guatemala", ciudad.nombre());
        assertEquals("Guatemala", ciudad.pais());
        assertEquals(14.64072, ciudad.latitud(), 0.00001);
        assertEquals(-90.51327, ciudad.longitud(), 0.00001);
        assertEquals("Ciudad de Guatemala, Guatemala", ciudad.toString());
    }

    @Test
    void ciudadSinResultadosLanzaClimaException() {
        assertThrows(ClimaException.class, () -> Ciudad.desdeJson("{\"generationtime_ms\":0.5}"));
        assertThrows(ClimaException.class, () -> Ciudad.desdeJson("{\"results\":[]}"));
    }

    @Test
    void coordenadasFueraDeRangoSonRechazadas() {
        assertThrows(IllegalArgumentException.class, () -> new Ciudad("X", "Y", 91, 0));
        assertThrows(IllegalArgumentException.class, () -> new Ciudad("X", "Y", 0, 181));
    }

    @Test
    void buscarCiudadConNombreVacioEsRechazado() {
        ClienteClima cliente = new ClienteClima();
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarCiudad("  "));
    }
}
