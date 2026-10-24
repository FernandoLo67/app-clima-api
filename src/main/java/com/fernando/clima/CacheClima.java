package com.fernando.clima;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Cache en memoria con TTL para consultas de clima.
 *
 * <p>Evita golpear la API repetidamente por la misma ciudad en un periodo
 * corto. Las entradas expiran despues del TTL configurado, y el cache
 * tiene un tamano maximo para no crecer sin limite.
 *
 * <p>Usa un LinkedHashMap con orden de acceso como base del algoritmo LRU:
 * cuando el cache se llena, la entrada menos consultada recientemente se
 * descarta.
 *
 * <p>Las claves se normalizan a minusculas sin espacios extremos para que
 * "Guatemala", "guatemala" y " Guatemala " sean la misma consulta.
 */
public class CacheClima {

    private final long ttlMs;
    private final int capacidad;
    private final LongSupplier reloj;
    private final Map<String, Entrada> entradas;

    public CacheClima(int capacidad, long ttlMs, LongSupplier reloj) {
        if (capacidad < 1) {
            throw new IllegalArgumentException("La capacidad debe ser al menos 1");
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("El TTL debe ser positivo");
        }
        this.capacidad = capacidad;
        this.ttlMs = ttlMs;
        this.reloj = reloj;
        this.entradas = new LinkedHashMap<>(capacidad, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entrada> eldest) {
                return size() > capacidad;
            }
        };
    }

    public void guardar(String ciudad, Clima clima) {
        entradas.put(normalizar(ciudad), new Entrada(clima, reloj.getAsLong()));
    }

    public Optional<Clima> consultar(String ciudad) {
        Entrada entrada = entradas.get(normalizar(ciudad));
        if (entrada == null) {
            return Optional.empty();
        }
        if (reloj.getAsLong() - entrada.creadoEn > ttlMs) {
            entradas.remove(normalizar(ciudad));
            return Optional.empty();
        }
        return Optional.of(entrada.clima);
    }

    public int tamano() {
        return entradas.size();
    }

    public void limpiar() {
        entradas.clear();
    }

    public boolean contiene(String ciudad) {
        return consultar(ciudad).isPresent();
    }

    private String normalizar(String ciudad) {
        return ciudad.trim().toLowerCase();
    }

    private static class Entrada {
        final Clima clima;
        final long creadoEn;

        Entrada(Clima clima, long creadoEn) {
            this.clima = clima;
            this.creadoEn = creadoEn;
        }
    }
}
