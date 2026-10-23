# App del Clima (API)

Consulta el clima actual de cualquier ciudad usando Open-Meteo, una API publica y gratuita que no pide clave. **Sin dependencias externas**: el cliente HTTP es el del JDK y el parser de JSON esta escrito a mano.

El defecto principal es el mas serio de todo el portafolio, porque la entrada viene de la red.

## El defecto: el parser moria con `StackOverflowError`

Un parser por descenso recursivo gasta un marco de pila por cada nivel de anidamiento. Sin limite, **la profundidad de la entrada decide cuanta pila se consume**:

```
  100 niveles  ->  OK
 1000 niveles  ->  OK
 5000 niveles  ->  StackOverflowError
```

`"[".repeat(5000) + "]".repeat(5000)` son diez kilobytes de texto. Cualquiera que pueda responder a una peticion HTTP puede mandarlos.

Y `StackOverflowError` **no es una excepcion**: es un `Error`.

```java
try {
    return cliente.buscarCiudad(nombre);
} catch (Exception e) {          // no lo atrapa
    ...
}
```

Ningun `catch (Exception e)` lo detiene, la pila se desenrolla entera, y el estado en el que queda el programa no esta garantizado — el desbordamiento puede ocurrir en medio de cualquier operacion, con estructuras a medio construir.

Es la diferencia entre una entrada que **se rechaza** y una entrada que **tumba el proceso**.

La correccion es la estandar: contar la profundidad y rechazar pasado un limite.

```java
public static final int PROFUNDIDAD_MAXIMA = 200;

private void entrar() {
    if (++profundidad > PROFUNDIDAD_MAXIMA) {
        throw new JsonException("El JSON anida mas de " + PROFUNDIDAD_MAXIMA + " niveles...");
    }
}
```

200 es mucho mas de lo que anida cualquier API real — las respuestas de Open-Meteo llegan a 4 — y muy poco comparado con lo que aguanta la pila. El limite corta el ataque sin estorbar al uso legitimo.

Hay una prueba que lo comprueba desde donde de verdad importa: `ClienteClimaTest` le pasa la bomba como respuesta del servidor y verifica que sale una `ClimaException` normal.

## Segundo defecto: la abstraccion filtraba las excepciones del parser

```
temperatura no numerica  ->  com.fernando.clima.Json$JsonException
ciudad sin nombre        ->  com.fernando.clima.Json$JsonException
json invalido            ->  com.fernando.clima.Json$JsonException
escape \uZZZZ            ->  java.lang.NumberFormatException
```

Quien llama a `ClienteClima` pide el clima de una ciudad. No sabe que por dentro hay un parser JSON escrito a mano, y no tiene por que — pero para protegerse tenia que atrapar **tres jerarquias distintas**, dos de ellas detalles de implementacion que pueden cambiar manana. Y una era `NumberFormatException`, que se escapaba cruda de un `Integer.parseInt` sobre un escape unicode mal formado.

**Una abstraccion que filtra las excepciones de sus dependencias no es una abstraccion.**

Ahora todo lo que sale de la fachada es `ClimaException`, con la causa original encadenada para no perder informacion al depurar. Hay una prueba que comprueba las dos mitades: que lo que sale es `ClimaException` y que `getCause()` sigue siendo la `JsonException` original.

`JsonException` ademas dejo de ser una clase anidada. Escribir `catch (Json.JsonException e)` obliga a nombrar al parser en el codigo de quien solo queria leer un dato.

## Tercer defecto: el escaner de numeros era demasiado permisivo

El escaner recogia cualquier mezcla de digitos y simbolos y se la daba a `Double.parseDouble`, que acepta mas cosas que JSON:

| entrada | antes | JSON lo permite |
|---|---|---|
| `{"x":1.}` | `1.0` | no |
| `{"x":.5}` | `0.5` | no |
| `{"x":007}` | `7.0` | no |
| `{"x":+5}` | `5.0` | no |

**Un parser laxo parece amable y es lo contrario.** Acepta archivos que ningun otro programa va a aceptar, asi que el error no desaparece: aparece mas tarde, en otro sistema, y sin la informacion de donde se genero. La gramatica ahora se sigue al pie de la letra:

```
-? ( 0 | [1-9][0-9]* ) ( \.[0-9]+ )? ( [eE][+-]?[0-9]+ )?
```

Lo mismo con las cadenas: JSON prohibe los caracteres de control sin escapar, y antes un salto de linea crudo se colaba tal cual.

## Cuarto defecto: el cliente HTTP no tenia ninguna prueba

Era la unica clase del proyecto sin cubrir, y por un motivo entendible: probarla parecia obligar a llamar a Open-Meteo de verdad. Eso seria lento, dependeria de que haya internet, y daria resultados distintos cada vez.

La salida es que el `HttpClient` **ya entraba por el constructor** — solo que nadie lo estaba usando para eso. Un doble que devuelve respuestas fijas permite comprobar lo que en realidad importa, que no necesita ningun servidor:

- que la URL se arma bien, incluida la codificacion de `"Ciudad de Mexico"` a `name=Ciudad+de+Mexico`
- que el atajo hace las dos peticiones en el orden correcto
- que un 404 se convierte en `ClimaException`
- que un cuerpo mal formado tambien
- que un `IOException` de red tambien, con la causa encadenada
- que los argumentos invalidos se rechazan **antes** de salir a la red

Un detalle que vale la pena mirar: `HttpClient` es una **clase abstracta**, no una interfaz, asi que el doble tiene que implementar una docena de metodos que no se usan (`sslParameters`, `authenticator`, `version`, `executor`...). Es el precio de heredar de una clase en vez de implementar una interfaz, y es la razon por la que las bibliotecas modernas exponen interfaces para lo que se va a sustituir en pruebas.

## Por que el parser esta escrito a mano

No es por evitar Gson o Jackson por gusto: es que **Maven Central no es accesible desde el entorno donde se compilo este portafolio**, y todos los proyectos tienen que funcionar solo con el JDK. La restriccion resulto util: un parser de JSON por descenso recursivo son 300 lineas, se lee entero de una sentada, y es donde se ven los dos problemas que este README documenta — la profundidad y la laxitud — que en una biblioteca ya resuelta no se verian nunca.

`Json.buscar(raiz, "results.0.latitude")` navega por rutas con puntos e indices, que es lo unico que hace falta para leer una respuesta de API sin generar clases.

## Las 48 pruebas

**`JsonTest` (27)**

| grupo | que cubre |
|---|---|
| **Profundidad** | El defecto principal, en arreglos y en objetos; el limite exacto; los hermanos no suman |
| **Numeros** | Los que JSON no permite; los que si, incluidos exponentes y negativos |
| Cadenas | Caracteres de control sin escapar; escapes unicode invalidos e incompletos |
| Estructura | Objetos, arreglos, anidamiento, claves sin comillas, rutas, ruta nula |

**`ClienteClimaTest` (10)** — la URL, el atajo, la traduccion de errores, la bomba de anidamiento, las validaciones.

**`ClimaTest` (11)** — el mapeo del JSON de la API a `Ciudad` y `Clima`, los codigos WMO y la conversion a Fahrenheit.

## Estructura

```
src/main/java/com/fernando/clima/
├── Json.java            Parser por descenso recursivo, con limite de profundidad.
├── JsonException.java   Error de parseo. No sale de la fachada.
├── ClimaException.java  Lo unico que sale de la fachada.
├── Ciudad.java          Record; se construye desde el JSON de geocodificacion.
├── Clima.java           Record; codigos WMO y conversion a Fahrenheit.
├── ClienteClima.java    HTTP con el cliente del JDK, inyectable.
└── Main.java            Solo lee del teclado y escribe en pantalla.
src/test/java/com/fernando/clima/
├── JsonTest.java          27 pruebas.
├── ClienteClimaTest.java  10 pruebas, sin tocar la red.
└── ClimaTest.java         11 pruebas.
```

## Como ejecutar

```bash
mvn package
java -jar target/app-clima-api-1.0.0.jar
```

Necesita conexion a internet: consulta `open-meteo.com`.

## Como correr las pruebas

```bash
mvn test
```

Las pruebas **no** necesitan conexion.
