// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package es.matrix.datos;

import java.nio.file.Path;
import java.util.Scanner;

public class SimuladorMain {
    public SimuladorMain() {
    }

    public static void main(String[] args) {
        Path carpetaDatos = Path.of("datos");

        try {
            Throwable var2 = null;
            Object var3 = null;

            try {
                Scanner teclado = new Scanner(System.in);

                try {
                    System.out.println("=== MATRIX: SIMULADOR ===");
                    System.out.println("1. Simulación normal");
                    System.out.println("2. Modo de pruebas (preparado para elegir incidente en la fase 3)");
                    System.out.print("Selecciona el modo: ");
                    String opcion = teclado.nextLine().trim();
                    boolean modoPruebas;
                    if (opcion.equals("1")) {
                        modoPruebas = false;
                    } else {
                        if (!opcion.equals("2")) {
                            System.out.println("Opción no válida. No se ha iniciado la simulación.");
                            return;
                        }

                        modoPruebas = true;
                        System.out.println("Modo de pruebas seleccionado. La inyección de incidentes se añadirá en la fase 3.");
                    }

                    SimuladorMatrix simulador = new SimuladorMatrix(carpetaDatos, 30, modoPruebas);
                    simulador.ejecutar();
                } finally {
                    if (teclado != null) {
                        teclado.close();
                    }

                }
            } catch (Throwable var15) {
                if (var2 == null) {
                    var2 = var15;
                } else if (var2 != var15) {
                    var2.addSuppressed(var15);
                }

                throw var2;
            }
        } catch (Throwable e) {
            System.err.println("No se pudo ejecutar el simulador: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
