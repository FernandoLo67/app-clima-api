package com.fernando.clima;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheClimaTest {

    @Test
    void guardaYRecuperaClima() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(10, 60_000, () -> ahora[0]);
        Clima clima = climaDe("Guatemala", 25);

        cache.guardar("Guatemala", clima);

        Optional<Clima> resultado = cache.consultar("Guatemala");
        assertTrue(resultado.isPresent());
        assertEquals(25, resultado.get().temperaturaC());
    }

    @Test
    void devuelveVacioSiNoExiste() {
        CacheClima cache = new CacheClima(10, 60_000, System::currentTimeMillis);
        assertTrue(cache.consultar("Narnia").isEmpty());
    }

    @Test
    void expiraDespuesDelTtl() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(10, 60_000, () -> ahora[0]);

        cache.guardar("Guatemala", climaDe("Guatemala", 25));
        ahora[0] = 60_001;

        assertTrue(cache.consultar("Guatemala").isEmpty());
        assertEquals(0, cache.tamano());
    }

    @Test
    void noExpiraAntesDelTtl() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(10, 60_000, () -> ahora[0]);

        cache.guardar("Guatemala", climaDe("Guatemala", 25));
        ahora[0] = 59_999;

        assertTrue(cache.consultar("Guatemala").isPresent());
    }

    @Test
    void normalizaLaCiudad() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(10, 60_000, () -> ahora[0]);

        cache.guardar("Guatemala", climaDe("Guatemala", 25));

        assertTrue(cache.consultar("guatemala").isPresent());
        assertTrue(cache.consultar(" GUATEMALA ").isPresent());
        assertTrue(cache.consultar("Guatemala").isPresent());
    }

    @Test
    void descartaLaEntradaMenosUsadaAlLlenarseCapacidad() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(2, 60_000, () -> ahora[0]);

        cache.guardar("A", climaDe("A", 10));
        cache.guardar("B", climaDe("B", 20));
        cache.guardar("C", climaDe("C", 30));

        assertFalse(cache.contiene("A"), "A era la menos reciente y debio ser descartada");
        assertTrue(cache.contiene("B"));
        assertTrue(cache.contiene("C"));
    }

    @Test
    void limpiarVaciaElCache() {
        CacheClima cache = new CacheClima(10, 60_000, System::currentTimeMillis);
        cache.guardar("Guatemala", climaDe("Guatemala", 25));

        cache.limpiar();

        assertEquals(0, cache.tamano());
        assertFalse(cache.contiene("Guatemala"));
    }

    @Test
    void rechazaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheClima(0, 60_000, System::currentTimeMillis));
        assertThrows(IllegalArgumentException.class,
                () -> new CacheClima(10, 0, System::currentTimeMillis));
    }

    @Test
    void contieneReflejaTtl() {
        long[] ahora = {0L};
        CacheClima cache = new CacheClima(10, 5_000, () -> ahora[0]);

        cache.guardar("Lima", climaDe("Lima", 18));
        assertTrue(cache.contiene("Lima"));

        ahora[0] = 5_001;
        assertFalse(cache.contiene("Lima"));
    }

    private Clima climaDe(String ciudad, double temp) {
        return new Clima(ciudad, temp, 10, 0, true);
    }
}
