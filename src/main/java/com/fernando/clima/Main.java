package com.fernando.clima;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        ClienteClima cliente = new ClienteClima();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== App del Clima (Open-Meteo) ===");
        System.out.println("Escribe el nombre de una ciudad, o 'salir' para terminar.");

        while (true) {
            System.out.print("\nCiudad: ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String entrada = scanner.nextLine().trim();
            if (entrada.equalsIgnoreCase("salir")) {
                break;
            }
            if (entrada.isBlank()) {
                continue;
            }
            try {
                Clima clima = cliente.obtenerClima(entrada);
                System.out.println();
                System.out.println(clima.resumen());
            } catch (ClimaException | IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Hasta luego!");
        scanner.close();
    }
}
