package com.fernando.clima;

/**
 * Algo salio mal al consultar el clima: la red, la API, o una respuesta que no
 * tiene la forma esperada.
 *
 * <h2>El defecto: la fachada filtraba excepciones del parser</h2>
 *
 * {@code Ciudad.desdeJson} y {@code Clima.desdeJson} dejaban escapar
 * {@code Json.JsonException} — y en un caso hasta una
 * {@link NumberFormatException} cruda, de un {@code Integer.parseInt} sobre un
 * escape unicode mal formado.
 *
 * <pre>
 * temperatura no numerica  ->  com.fernando.clima.Json$JsonException
 * ciudad sin nombre        ->  com.fernando.clima.Json$JsonException
 * json invalido            ->  com.fernando.clima.Json$JsonException
 * escape \\uZZZZ            ->  java.lang.NumberFormatException
 * </pre>
 *
 * <p>Quien llama a {@link ClienteClima} pide el clima de una ciudad. No sabe que
 * por dentro hay un parser JSON escrito a mano, y no tiene por que: para
 * protegerse tendria que atrapar tres jerarquias distintas, dos de ellas
 * detalles de implementacion que pueden cambiar manana.
 *
 * <p><b>Una abstraccion que filtra las excepciones de sus dependencias no es una
 * abstraccion.</b> Ahora todo lo que sale de la fachada es {@code ClimaException},
 * con la causa original encadenada para que no se pierda informacion al
 * depurar.
 */
public class ClimaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ClimaException(String mensaje) {
        super(mensaje);
    }

    public ClimaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
