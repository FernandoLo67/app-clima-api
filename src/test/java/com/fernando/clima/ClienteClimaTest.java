package com.fernando.clima;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link ClienteClima} <b>sin tocar la red</b>.
 *
 * <p>Esta clase era la unica del proyecto sin ninguna prueba, y por un motivo
 * entendible: probarla parecia obligar a llamar a Open-Meteo de verdad. Eso
 * seria lento, dependeria de que haya internet, y daria resultados distintos
 * cada vez — la temperatura de Guatemala cambia.
 *
 * <p>La salida es que {@link HttpClient} entra por el constructor. Aqui se le
 * pasa uno de mentira que devuelve respuestas fijas, y con eso se puede
 * comprobar lo que en realidad importa: <b>que la URL se arma bien y que los
 * errores se traducen</b>. Nada de eso necesita un servidor.
 *
 * <p>{@code HttpClient} es una clase abstracta, no una interfaz, asi que el
 * doble tiene que implementar una docena de metodos que no se usan. Es el precio
 * de heredar de una clase en vez de implementar una interfaz, y es la razon por
 * la que las bibliotecas modernas exponen interfaces.
 */
class ClienteClimaTest {

    private static final String JSON_CIUDAD =
            "{\"results\":[{\"name\":\"Guatemala\",\"country\":\"Guatemala\",\n"
            + "             \"latitude\":14.625,\"longitude\":-90.5}]}";

    private static final String JSON_PRONOSTICO =
            "{\"current_weather\":{\"temperature\":23.4,\"windspeed\":11.2,\n"
            + "                    \"weathercode\":61,\"is_day\":1}}";

    // ---------------------------------------------------------------
    //  El doble de HttpClient
    // ---------------------------------------------------------------

    /** Devuelve una respuesta fija y anota las URLs que le pidieron. */
    private static class HttpFalso extends HttpClient {

        private final int codigo;
        private final Function<String, String> cuerpoSegunUrl;
        final List<String> urlesPedidas = new ArrayList<>();

        HttpFalso(int codigo, Function<String, String> cuerpoSegunUrl) {
            this.codigo = codigo;
            this.cuerpoSegunUrl = cuerpoSegunUrl;
        }

        static HttpFalso queDevuelve(String cuerpo) {
            return new HttpFalso(200, url -> cuerpo);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest peticion, HttpResponse.BodyHandler<T> manejador)
                throws IOException, InterruptedException {
            String url = peticion.uri().toString();
            urlesPedidas.add(url);
            return (HttpResponse<T>) new RespuestaFalsa(codigo, cuerpoSegunUrl.apply(url), peticion.uri());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest peticion, HttpResponse.BodyHandler<T> manejador) {
            try {
                return CompletableFuture.completedFuture(send(peticion, manejador));
            } catch (IOException | InterruptedException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest peticion, HttpResponse.BodyHandler<T> manejador,
                HttpResponse.PushPromiseHandler<T> push) {
            return sendAsync(peticion, manejador);
        }

        // Lo que hay que implementar por heredar de una clase abstracta.
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    /** Respuesta fija: solo importan el codigo y el cuerpo. */
    private static final class RespuestaFalsa implements HttpResponse<String> {
        private final int codigo;
        private final String cuerpo;
        private final URI uri;

        RespuestaFalsa(int codigo, String cuerpo, URI uri) {
            this.codigo = codigo;
            this.cuerpo = cuerpo;
            this.uri = uri;
        }

        public int codigo() {
            return codigo;
        }

        public String cuerpo() {
            return cuerpo;
        }

        public URI uri() {
            return uri;
        }

        @Override public int statusCode() { return codigo; }
        @Override public String body() { return cuerpo; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RespuestaFalsa)) {
                return false;
            }
            RespuestaFalsa that = (RespuestaFalsa) o;
            return codigo == that.codigo
                    && java.util.Objects.equals(cuerpo, that.cuerpo)
                    && java.util.Objects.equals(uri, that.uri);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(codigo, cuerpo, uri);
        }

        @Override
        public String toString() {
            return "RespuestaFalsa[codigo=" + codigo + ", cuerpo=" + cuerpo + ", uri=" + uri + "]";
        }
    }

    // ---------------------------------------------------------------
    //  La URL
    // ---------------------------------------------------------------

    @Test
    void buscarCiudadLeeLaRespuestaDeGeocodificacion() {
        ClienteClima cliente = new ClienteClima(HttpFalso.queDevuelve(JSON_CIUDAD));

        Ciudad ciudad = cliente.buscarCiudad("Guatemala");

        assertEquals("Guatemala", ciudad.nombre());
        assertEquals(14.625, ciudad.latitud());
        assertEquals(-90.5, ciudad.longitud());
    }

    /**
     * Los espacios y los acentos se codifican para la URL. Sin
     * {@code URLEncoder}, "Ciudad de Mexico" produciria una URI invalida y la
     * peticion fallaria antes de salir.
     */
    @Test
    void elNombreDeLaCiudadSeCodificaParaLaUrl() {
        HttpFalso http = HttpFalso.queDevuelve(JSON_CIUDAD);
        new ClienteClima(http).buscarCiudad("  Ciudad de Mexico  ");

        String url = http.urlesPedidas.get(0);
        assertTrue(url.contains("name=Ciudad+de+Mexico"), url);
        assertFalse(url.contains("  "), "y los espacios sobrantes se recortan antes");
    }

    @Test
    void laUrlDelPronosticoLlevaLasCoordenadas() {
        HttpFalso http = HttpFalso.queDevuelve(JSON_PRONOSTICO);
        Ciudad ciudad = new Ciudad("Guatemala", "Guatemala", 14.625, -90.5);

        new ClienteClima(http).obtenerClima(ciudad);

        String url = http.urlesPedidas.get(0);
        assertTrue(url.contains("latitude=14.625"), url);
        assertTrue(url.contains("longitude=-90.5"), url);
        assertTrue(url.contains("current_weather=true"), url);
    }

    /** El atajo hace dos peticiones: primero geocodifica y despues consulta. */
    @Test
    void elAtajoHaceLasDosPeticionesEnOrden() {
        HttpFalso http = new HttpFalso(200,
                url -> url.contains("geocoding") ? JSON_CIUDAD : JSON_PRONOSTICO);

        Clima clima = new ClienteClima(http).obtenerClima("Guatemala");

        assertEquals(2, http.urlesPedidas.size());
        assertTrue(http.urlesPedidas.get(0).contains("geocoding"));
        assertTrue(http.urlesPedidas.get(1).contains("forecast"));
        assertEquals(23.4, clima.temperaturaC());
        assertEquals("Guatemala, Guatemala", clima.ciudad());
    }

    // ---------------------------------------------------------------
    //  Los errores se traducen
    // ---------------------------------------------------------------

    @Test
    void unCodigoDistintoDe200EsClimaException() {
        ClienteClima cliente = new ClienteClima(new HttpFalso(404, url -> ""));

        ClimaException e = assertThrows(ClimaException.class, () -> cliente.buscarCiudad("Nada"));
        assertTrue(e.getMessage().contains("404"), e.getMessage());
    }

    /**
     * LA PRUEBA QUE DOCUMENTA EL DEFECTO CORREGIDO.
     *
     * <p>Un cuerpo mal formado salia como {@code Json.JsonException} — una clase
     * anidada del parser. Quien usa este cliente pide el clima de una ciudad: si
     * para protegerse tiene que atrapar excepciones del parser JSON, la
     * abstraccion no esta abstrayendo nada.
     */
    @Test
    void unaRespuestaMalFormadaEsClimaExceptionYnoUnaDelParser() {
        ClienteClima cliente = new ClienteClima(HttpFalso.queDevuelve("esto no es json"));

        ClimaException e = assertThrows(ClimaException.class, () -> cliente.buscarCiudad("Guatemala"));
        assertNotNull(e.getCause(), "pero la causa original se conserva");
        assertInstanceOf(JsonException.class, e.getCause());
    }

    @Test
    void unaRespuestaSinLosCamposEsperadosEsClimaException() {
        assertThrows(ClimaException.class,
                () -> new ClienteClima(HttpFalso.queDevuelve("{\"results\":[]}")).buscarCiudad("Nada"));
        assertThrows(ClimaException.class,
                () -> new ClienteClima(HttpFalso.queDevuelve("{\"otra\":1}"))
                        .obtenerClima(new Ciudad("X", "Y", 0, 0)));
    }

    /**
     * Una respuesta anidada a lo bestia se rechaza en vez de tumbar la pila. Es
     * el defecto de {@link Json} visto desde donde de verdad importa: la entrada
     * viene de la red.
     */
    @Test
    void unaRespuestaConAnidamientoAbusivoNoTumbaElProceso() {
        String bomba = "[".repeat(5000) + "]".repeat(5000);
        ClienteClima cliente = new ClienteClima(HttpFalso.queDevuelve(bomba));

        assertThrows(ClimaException.class, () -> cliente.buscarCiudad("Guatemala"));
    }

    /** Un fallo de red se traduce igual, con la causa encadenada. */
    @Test
    void unFalloDeRedEsClimaException() {
        HttpClient roto = new HttpFalso(200, url -> "") {
            @Override
            public <T> HttpResponse<T> send(HttpRequest p, HttpResponse.BodyHandler<T> m) throws IOException {
                throw new IOException("Network is unreachable");
            }
        };

        ClimaException e = assertThrows(ClimaException.class,
                () -> new ClienteClima(roto).buscarCiudad("Guatemala"));
        assertTrue(e.getMessage().contains("No se pudo conectar"), e.getMessage());
        assertInstanceOf(IOException.class, e.getCause());
    }

    // ---------------------------------------------------------------
    //  Validaciones
    // ---------------------------------------------------------------

    @Test
    void losArgumentosInvalidosSeRechazanAntesDeSalirALaRed() {
        HttpFalso http = HttpFalso.queDevuelve(JSON_CIUDAD);
        ClienteClima cliente = new ClienteClima(http);

        assertThrows(IllegalArgumentException.class, () -> cliente.buscarCiudad("  "));
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarCiudad(null));
        assertThrows(IllegalArgumentException.class, () -> cliente.obtenerClima((Ciudad) null));
        assertThrows(IllegalArgumentException.class, () -> new ClienteClima(null));

        assertTrue(http.urlesPedidas.isEmpty(), "no se hizo ninguna peticion");
    }
}
