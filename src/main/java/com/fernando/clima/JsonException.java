package com.fernando.clima;

/**
 * Error al leer un texto JSON.
 *
 * <h2>Por que dejo de ser una clase anidada de {@link Json}</h2>
 *
 * Antes era {@code Json.JsonException}, y eso obligaba a escribir
 * {@code catch (Json.JsonException e)} — una forma que <b>nombra al parser</b>
 * en el codigo de quien solo queria leer un dato. Como clase propia se atrapa
 * por su nombre y el parser puede cambiar sin arrastrar a nadie.
 *
 * <p>Y sobre todo: esta excepcion <b>no debe salir de la fachada</b>. Quien usa
 * {@link ClienteClima} pide el clima de una ciudad, no parsea JSON; lo que reciba
 * si algo falla tiene que ser {@link ClimaException}. Ver esa clase.
 */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonException(String mensaje) {
        super(mensaje);
    }

    public JsonException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
