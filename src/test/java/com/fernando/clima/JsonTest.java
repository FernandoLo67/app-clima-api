package com.fernando.clima;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parseaUnObjetoPlano() {
        Object raiz = Json.parsear("{\"a\":1,\"b\":\"hola\"}");
        assertInstanceOf(Map.class, raiz);
        assertEquals(1.0, Json.buscarNumero(raiz, "a"), 0.0001);
        assertEquals("hola", Json.buscarTexto(raiz, "b"));
    }

    @Test
    void parseaObjetosAnidados() {
        Object raiz = Json.parsear("{\"externo\":{\"interno\":{\"valor\":42}}}");
        assertEquals(42.0, Json.buscarNumero(raiz, "externo.interno.valor"), 0.0001);
    }

    @Test
    void parseaArreglosYPermiteIndexarlos() {
        Object raiz = Json.parsear("{\"items\":[{\"n\":10},{\"n\":20}]}");
        assertEquals(20.0, Json.buscarNumero(raiz, "items.1.n"), 0.0001);
    }

    @Test
    void parseaUnArregloEnLaRaiz() {
        Object raiz = Json.parsear("[1,2,3]");
        assertInstanceOf(List.class, raiz);
        assertEquals(3, ((List<?>) raiz).size());
    }

    @Test
    void parseaObjetosYArreglosVacios() {
        assertTrue(((Map<?, ?>) Json.parsear("{}")).isEmpty());
        assertTrue(((List<?>) Json.parsear("[]")).isEmpty());
    }

    @Test
    void parseaNumerosNegativosYDecimales() {
        Object raiz = Json.parsear("{\"lat\":-14.6349,\"lon\":90.5069}");
        assertEquals(-14.6349, Json.buscarNumero(raiz, "lat"), 0.00001);
        assertEquals(90.5069, Json.buscarNumero(raiz, "lon"), 0.00001);
    }

    @Test
    void parseaNotacionCientifica() {
        assertEquals(1500.0, Json.buscarNumero(Json.parsear("{\"n\":1.5e3}"), "n"), 0.0001);
    }

    @Test
    void parseaBooleanosYNulos() {
        Object raiz = Json.parsear("{\"si\":true,\"no\":false,\"nada\":null}");
        assertEquals(Boolean.TRUE, Json.buscar(raiz, "si"));
        assertEquals(Boolean.FALSE, Json.buscar(raiz, "no"));
        assertNull(Json.buscar(raiz, "nada"));
    }

    @Test
    void interpretaLosEscapesDeCadena() {
        Object raiz = Json.parsear("{\"t\":\"linea1\\nlinea2\\t\\\"citado\\\"\"}");
        assertEquals("linea1\nlinea2\t\"citado\"", Json.buscarTexto(raiz, "t"));
    }

    @Test
    void interpretaEscapesUnicode() {
        assertEquals("Mañana", Json.buscarTexto(Json.parsear("{\"t\":\"Ma\\u00f1ana\"}"), "t"));
    }

    @Test
    void ignoraLosEspaciosEnBlanco() {
        Object raiz = Json.parsear("  {  \"a\" :  [ 1 , 2 ]  }  ");
        assertEquals(2.0, Json.buscarNumero(raiz, "a.1"), 0.0001);
    }

    @Test
    void buscarDevuelveNullSiLaRutaNoExiste() {
        Object raiz = Json.parsear("{\"a\":1}");
        assertNull(Json.buscar(raiz, "b"));
        assertNull(Json.buscar(raiz, "a.b.c"));
        assertNull(Json.buscar(raiz, "a.99"));
    }

    @Test
    void objetoSinCerrarLanzaJsonException() {
        assertThrows(JsonException.class, () -> Json.parsear("{\"a\":1"));
    }

    @Test
    void cadenaSinCerrarLanzaJsonException() {
        assertThrows(JsonException.class, () -> Json.parsear("{\"a\":\"sin fin}"));
    }

    @Test
    void contenidoSobranteLanzaJsonException() {
        assertThrows(JsonException.class, () -> Json.parsear("{\"a\":1} basura"));
    }

    @Test
    void jsonNuloLanzaJsonException() {
        assertThrows(JsonException.class, () -> Json.parsear(null));
    }

    @Test
    void buscarNumeroSobreUnTextoLanzaJsonException() {
        Object raiz = Json.parsear("{\"a\":\"no soy numero\"}");
        assertThrows(JsonException.class, () -> Json.buscarNumero(raiz, "a"));
    }

    // ---------------------------------------------------------------
    //  Profundidad: donde estaba el defecto grave
    // ---------------------------------------------------------------

    /**
     * LA PRUEBA QUE DOCUMENTA EL DEFECTO CORREGIDO.
     *
     * <p>Un parser por descenso recursivo gasta un marco de pila por nivel. Sin
     * limite, la profundidad de la entrada decide cuanta pila se consume:
     *
     * <pre>
     *   1000 niveles  ->  OK
     *   5000 niveles  ->  StackOverflowError
     * </pre>
     *
     * <p>Y {@code StackOverflowError} <b>no es una excepcion</b>: es un
     * {@code Error}. Ningun {@code catch (Exception e)} lo atrapa, y el estado en
     * el que queda el programa no esta garantizado.
     *
     * <p>Diez kilobytes de corchetes bastan, y cualquiera que pueda responder a
     * una peticion HTTP puede mandarlos.
     */
    @Test
    void unJsonDemasiadoAnidadoSeRechazaEnVezDeTumbarLaPila() {
        String bomba = "[".repeat(5000) + "]".repeat(5000);

        JsonException e = assertThrows(JsonException.class, () -> Json.parsear(bomba));
        assertTrue(e.getMessage().contains("anida mas de"), e.getMessage());
    }

    /** Objetos anidados, la otra forma de la misma bomba. */
    @Test
    void laProfundidadTambienSeCuentaEnLosObjetos() {
        String bomba = "{\"a\":".repeat(5000) + "1" + "}".repeat(5000);
        assertThrows(JsonException.class, () -> Json.parsear(bomba));
    }

    /** El limite no estorba: cualquier respuesta de API real anida mucho menos. */
    @Test
    void loQueAnidaMenosDelLimiteSeParseaSinProblema() {
        int niveles = Json.PROFUNDIDAD_MAXIMA;
        String profundo = "[".repeat(niveles) + "]".repeat(niveles);

        assertDoesNotThrow(() -> Json.parsear(profundo));
        assertThrows(JsonException.class,
                () -> Json.parsear("[".repeat(niveles + 1) + "]".repeat(niveles + 1)));
    }

    /**
     * La profundidad se cuenta, no se acumula: mil arreglos hermanos no suman
     * mil niveles.
     */
    @Test
    void losHermanosNoSumanProfundidad() {
        String hermanos = "[" + "[],".repeat(999) + "[]]";
        assertDoesNotThrow(() -> Json.parsear(hermanos));
    }

    // ---------------------------------------------------------------
    //  Numeros: la gramatica de JSON, no la de Double.parseDouble
    // ---------------------------------------------------------------

    /**
     * EL SEGUNDO DEFECTO.
     *
     * <p>El escaner anterior recogia cualquier mezcla de digitos y simbolos y se
     * la daba a {@code Double.parseDouble}, que es mas permisivo que JSON:
     *
     * <pre>
     *   {"x":1.}  -> 1.0      {"x":007} -> 7.0
     *   {"x":.5}  -> 0.5      {"x":+5}  -> 5.0
     * </pre>
     *
     * <p>Un parser laxo parece amable y es lo contrario: acepta archivos que
     * ningun otro programa va a aceptar, asi que el error aparece mas tarde y en
     * otro sitio.
     */
    @Test
    void losNumerosQueJsonNoPermiteSeRechazan() {
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":1.}"), "1.");
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":.5}"), ".5");
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":007}"), "ceros a la izquierda");
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":+5}"), "signo mas");
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":1e}"), "exponente vacio");
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":-}"), "solo el signo");
    }

    /** Y los que si permite se leen bien, incluidos los raros. */
    @Test
    void losNumerosValidosDeJsonSeLeenBien() {
        assertEquals(0.0, Json.buscarNumero(Json.parsear("{\"x\":0}"), "x"));
        assertEquals(-0.5, Json.buscarNumero(Json.parsear("{\"x\":-0.5}"), "x"));
        assertEquals(1.5e10, Json.buscarNumero(Json.parsear("{\"x\":1.5e10}"), "x"));
        assertEquals(1.5e-10, Json.buscarNumero(Json.parsear("{\"x\":1.5E-10}"), "x"));
        assertEquals(-90.5, Json.buscarNumero(Json.parsear("{\"x\":-90.5}"), "x"));
    }

    // ---------------------------------------------------------------
    //  Cadenas
    // ---------------------------------------------------------------

    /**
     * JSON prohibe los caracteres de control sin escapar. Antes se colaban tal
     * cual y producian un dato que ningun otro parser aceptaria.
     */
    @Test
    void losCaracteresDeControlSinEscaparSeRechazan() {
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":\"a\nb\"}"));
        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":\"a\tb\"}"));

        assertEquals("a\nb", Json.buscarTexto(Json.parsear("{\"x\":\"a\\nb\"}"), "x"),
                "escapado si vale");
    }

    /**
     * Un escape unicode invalido salia como {@code NumberFormatException} cruda:
     * una excepcion del JDK escapandose del parser, sin decir donde estaba el
     * problema.
     */
    @Test
    void unEscapeUnicodeInvalidoDaJsonException() {
        JsonException e = assertThrows(JsonException.class,
                () -> Json.parsear("{\"x\":\"\\uZZZZ\"}"));
        assertTrue(e.getMessage().contains("unicode"), e.getMessage());

        assertThrows(JsonException.class, () -> Json.parsear("{\"x\":\"\\uAB\"}"), "incompleto");
    }

    @Test
    void unaClaveSinComillasSeRechaza() {
        assertThrows(JsonException.class, () -> Json.parsear("{x:1}"));
    }

    @Test
    void laRutaNulaDaJsonExceptionYnoNullPointer() {
        Object raiz = Json.parsear("{\"a\":1}");
        assertThrows(JsonException.class, () -> Json.buscar(raiz, null));
    }
}
