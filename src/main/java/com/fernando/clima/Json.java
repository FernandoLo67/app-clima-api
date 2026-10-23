package com.fernando.clima;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de JSON minimo escrito a mano, por descenso recursivo.
 *
 * <p>El proyecto no usa Gson ni Jackson a proposito: la idea es mostrar como se
 * construye un parser real y mantener el jar sin dependencias externas.
 *
 * <p>Tipos que devuelve: {@code Map<String,Object>}, {@code List<Object>},
 * {@code String}, {@code Double}, {@code Boolean} y {@code null}.
 *
 * <h2>El defecto: moria con {@code StackOverflowError}</h2>
 *
 * Un parser por descenso recursivo gasta un marco de pila por cada nivel de
 * anidamiento. Sin limite, la profundidad de la entrada decide cuanta pila se
 * consume:
 *
 * <pre>
 *   1000 niveles  ->  OK
 *   5000 niveles  ->  StackOverflowError
 * </pre>
 *
 * <p>{@code "[".repeat(5000) + "]".repeat(5000)} son diez kilobytes de texto.
 * Cualquiera que pueda responder a una peticion HTTP puede mandarlos.
 *
 * <p>Y {@link StackOverflowError} <b>no es una excepcion</b>: es un {@link Error}.
 * Ningun {@code catch (Exception e)} lo atrapa, la pila se desenrolla entera, y
 * el estado en el que queda el programa no esta garantizado — el desbordamiento
 * puede haber ocurrido en medio de cualquier operacion.
 *
 * <p>Es la diferencia entre una entrada que <b>se rechaza</b> y una entrada que
 * <b>tumba el proceso</b>. La correccion es la estandar: contar la profundidad y
 * rechazar pasado {@value #PROFUNDIDAD_MAXIMA} niveles, que es mucho mas de lo
 * que anida cualquier API real y muy poco comparado con lo que la pila aguanta.
 *
 * <h2>El escaner de numeros era demasiado permisivo</h2>
 *
 * La version anterior recogia cualquier secuencia de digitos y simbolos y se la
 * daba a {@code Double.parseDouble}. Eso aceptaba cosas que JSON no permite:
 *
 * <pre>
 *   {"x":1.}   ->  1.0        {"x":007}  ->  7.0
 *   {"x":.5}   ->  0.5        {"x":+5}   ->  5.0
 * </pre>
 *
 * <p>Un parser laxo parece amable y es lo contrario: acepta archivos que ningun
 * otro programa va a aceptar, asi que el error aparece mas tarde y en otro
 * sitio. Ahora se sigue la gramatica de JSON al pie de la letra.
 */
public final class Json {

    /**
     * Cuantos niveles de anidamiento se admiten.
     *
     * <p>200 es mucho mas de lo que anida cualquier respuesta de API real — las
     * de Open-Meteo llegan a 4 — y muy poco comparado con lo que aguanta la pila
     * de la JVM, asi que el limite corta el ataque sin estorbar al uso legitimo.
     */
    public static final int PROFUNDIDAD_MAXIMA = 200;

    private final String texto;
    private int posicion;
    private int profundidad;

    private Json(String texto) {
        this.texto = texto;
    }

    /**
     * @param json texto a leer
     * @return el valor: {@code Map}, {@code List}, {@code String}, {@code Double},
     *         {@code Boolean} o {@code null}
     * @throws JsonException si el texto es nulo, esta mal formado, o anida mas de
     *                       {@value #PROFUNDIDAD_MAXIMA} niveles
     */
    public static Object parsear(String json) {
        if (json == null) {
            throw new JsonException("El JSON no puede ser nulo");
        }
        Json parser = new Json(json);
        parser.saltarEspacios();
        Object valor = parser.leerValor();
        parser.saltarEspacios();
        if (!parser.finDelTexto()) {
            throw new JsonException("Contenido sobrante en la posicion " + parser.posicion);
        }
        return valor;
    }

    /**
     * Navega por una ruta con puntos e indices: {@code "results.0.latitude"}.
     *
     * @param raiz valor devuelto por {@link #parsear}
     * @param ruta ruta separada por puntos
     * @return el valor encontrado, o {@code null} si algun tramo no existe
     * @throws JsonException si la ruta es nula
     */
    @SuppressWarnings("unchecked")
    public static Object buscar(Object raiz, String ruta) {
        if (ruta == null) {
            throw new JsonException("La ruta no puede ser nula");
        }
        Object actual = raiz;
        for (String tramo : ruta.split("\\.")) {
            if (actual == null) {
                return null;
            }
            if (actual instanceof Map<?, ?>) {
                actual = ((Map<String, Object>) actual).get(tramo);
            } else if (actual instanceof List<?>) {
                List<?> lista = (List<?>) actual;
                int indice;
                try {
                    indice = Integer.parseInt(tramo);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (indice < 0 || indice >= lista.size()) {
                    return null;
                }
                actual = lista.get(indice);
            } else {
                return null;
            }
        }
        return actual;
    }

    /**
     * @param raiz valor parseado
     * @param ruta ruta al numero
     * @return el numero
     * @throws JsonException si en esa ruta no hay un numero
     */
    public static double buscarNumero(Object raiz, String ruta) {
        Object valor = buscar(raiz, ruta);
        if (valor instanceof Number) {
            return ((Number) valor).doubleValue();
        }
        throw new JsonException("No se encontro un numero en la ruta '" + ruta + "'");
    }

    /**
     * @param raiz valor parseado
     * @param ruta ruta al texto
     * @return el texto
     * @throws JsonException si en esa ruta no hay un texto
     */
    public static String buscarTexto(Object raiz, String ruta) {
        Object valor = buscar(raiz, ruta);
        if (valor instanceof String) {
            return (String) valor;
        }
        throw new JsonException("No se encontro un texto en la ruta '" + ruta + "'");
    }

    // ---------------------------------------------------------------
    //  Descenso recursivo
    // ---------------------------------------------------------------

    private Object leerValor() {
        saltarEspacios();
        if (finDelTexto()) {
            throw new JsonException("JSON incompleto");
        }
        char c = texto.charAt(posicion);
        switch (c) {
            case '{':
                return leerObjeto();
            case '[':
                return leerArreglo();
            case '"':
                return leerTexto();
            case 't':
            case 'f':
                return leerBooleano();
            case 'n':
                return leerNulo();
            default:
                return leerNumero();
        }
    }

    /**
     * Se llama al entrar en un objeto o un arreglo. Es el unico punto donde la
     * recursion puede crecer, asi que basta con contar aqui.
     */
    private void entrar() {
        if (++profundidad > PROFUNDIDAD_MAXIMA) {
            throw new JsonException("El JSON anida mas de " + PROFUNDIDAD_MAXIMA
                    + " niveles en la posicion " + posicion);
        }
    }

    private void salir() {
        profundidad--;
    }

    private Map<String, Object> leerObjeto() {
        entrar();
        Map<String, Object> objeto = new LinkedHashMap<>();
        consumir('{');
        saltarEspacios();
        if (mirar() == '}') {
            consumir('}');
            salir();
            return objeto;
        }
        while (true) {
            saltarEspacios();
            if (mirar() != '"') {
                throw new JsonException("Se esperaba una clave entre comillas en la posicion " + posicion);
            }
            String clave = leerTexto();
            saltarEspacios();
            consumir(':');
            objeto.put(clave, leerValor());
            saltarEspacios();
            char siguiente = mirar();
            if (siguiente == ',') {
                consumir(',');
            } else if (siguiente == '}') {
                consumir('}');
                salir();
                return objeto;
            } else {
                throw new JsonException("Se esperaba ',' o '}' en la posicion " + posicion);
            }
        }
    }

    private List<Object> leerArreglo() {
        entrar();
        List<Object> arreglo = new ArrayList<>();
        consumir('[');
        saltarEspacios();
        if (mirar() == ']') {
            consumir(']');
            salir();
            return arreglo;
        }
        while (true) {
            arreglo.add(leerValor());
            saltarEspacios();
            char siguiente = mirar();
            if (siguiente == ',') {
                consumir(',');
            } else if (siguiente == ']') {
                consumir(']');
                salir();
                return arreglo;
            } else {
                throw new JsonException("Se esperaba ',' o ']' en la posicion " + posicion);
            }
        }
    }

    private String leerTexto() {
        consumir('"');
        StringBuilder resultado = new StringBuilder();
        while (true) {
            if (finDelTexto()) {
                throw new JsonException("Cadena sin cerrar");
            }
            char c = texto.charAt(posicion++);
            if (c == '"') {
                return resultado.toString();
            }
            if (c == '\\') {
                resultado.append(leerEscape());
            } else if (c < 0x20) {
                // JSON prohibe los caracteres de control sin escapar. Antes se
                // colaban tal cual, asi que un salto de linea crudo dentro de una
                // cadena producia un dato que ningun otro parser aceptaria.
                throw new JsonException(String.format(
                        "Caracter de control sin escapar (U+%04X) en la posicion %d", (int) c, posicion - 1));
            } else {
                resultado.append(c);
            }
        }
    }

    private char leerEscape() {
        if (finDelTexto()) {
            throw new JsonException("Escape incompleto");
        }
        char escape = texto.charAt(posicion++);
        switch (escape) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                return leerEscapeUnicode();
            default:
                throw new JsonException("Escape desconocido: \\" + escape);
        }
    }

    /**
     * Lee los cuatro digitos hexadecimales de un {@code \\uXXXX}.
     *
     * <p>Antes usaba {@code Integer.parseInt(hex, 16)} sin proteger, asi que un
     * {@code \\uZZZZ} salia como {@link NumberFormatException} cruda: una
     * excepcion del JDK escapandose de un parser, sin decir donde estaba el
     * problema.
     */
    private char leerEscapeUnicode() {
        if (posicion + 4 > texto.length()) {
            throw new JsonException("Escape unicode incompleto en la posicion " + posicion);
        }
        String hex = texto.substring(posicion, posicion + 4);
        posicion += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new JsonException("Escape unicode invalido: \\u" + hex, e);
        }
    }

    /**
     * Lee un numero siguiendo la gramatica de JSON:
     *
     * <pre>
     *   -? ( 0 | [1-9][0-9]* ) ( \.[0-9]+ )? ( [eE][+-]?[0-9]+ )?
     * </pre>
     *
     * <p>Nada de ceros a la izquierda, nada de {@code +} inicial, nada de
     * {@code 1.} ni {@code .5}. El escaner anterior recogia cualquier mezcla de
     * digitos y simbolos y dejaba que {@code Double.parseDouble} decidiera, que
     * es mas permisivo que JSON.
     */
    private Double leerNumero() {
        int inicio = posicion;

        if (!finDelTexto() && texto.charAt(posicion) == '-') {
            posicion++;
        }
        leerParteEntera();
        if (!finDelTexto() && texto.charAt(posicion) == '.') {
            posicion++;
            leerAlMenosUnDigito("Se esperaban decimales despues del punto");
        }
        if (!finDelTexto() && (texto.charAt(posicion) == 'e' || texto.charAt(posicion) == 'E')) {
            posicion++;
            if (!finDelTexto() && (texto.charAt(posicion) == '+' || texto.charAt(posicion) == '-')) {
                posicion++;
            }
            leerAlMenosUnDigito("Se esperaba el exponente");
        }

        String crudo = texto.substring(inicio, posicion);
        try {
            return Double.parseDouble(crudo);
        } catch (NumberFormatException e) {
            throw new JsonException("Numero invalido: '" + crudo + "'", e);
        }
    }

    private void leerParteEntera() {
        if (finDelTexto() || !Character.isDigit(texto.charAt(posicion))) {
            throw new JsonException("Se esperaba un numero en la posicion " + posicion);
        }
        if (texto.charAt(posicion) == '0') {
            posicion++;
            if (!finDelTexto() && Character.isDigit(texto.charAt(posicion))) {
                throw new JsonException("JSON no admite ceros a la izquierda, posicion " + posicion);
            }
            return;
        }
        while (!finDelTexto() && Character.isDigit(texto.charAt(posicion))) {
            posicion++;
        }
    }

    private void leerAlMenosUnDigito(String queFalta) {
        if (finDelTexto() || !Character.isDigit(texto.charAt(posicion))) {
            throw new JsonException(queFalta + " en la posicion " + posicion);
        }
        while (!finDelTexto() && Character.isDigit(texto.charAt(posicion))) {
            posicion++;
        }
    }

    private Boolean leerBooleano() {
        if (texto.startsWith("true", posicion)) {
            posicion += 4;
            return Boolean.TRUE;
        }
        if (texto.startsWith("false", posicion)) {
            posicion += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("Booleano invalido en la posicion " + posicion);
    }

    private Object leerNulo() {
        if (texto.startsWith("null", posicion)) {
            posicion += 4;
            return null;
        }
        throw new JsonException("Valor invalido en la posicion " + posicion);
    }

    private char mirar() {
        if (finDelTexto()) {
            throw new JsonException("JSON incompleto");
        }
        return texto.charAt(posicion);
    }

    private void consumir(char esperado) {
        if (finDelTexto() || texto.charAt(posicion) != esperado) {
            throw new JsonException("Se esperaba '" + esperado + "' en la posicion " + posicion);
        }
        posicion++;
    }

    private void saltarEspacios() {
        while (!finDelTexto() && Character.isWhitespace(texto.charAt(posicion))) {
            posicion++;
        }
    }

    private boolean finDelTexto() {
        return posicion >= texto.length();
    }
}
