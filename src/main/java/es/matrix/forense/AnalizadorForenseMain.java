package es.matrix.forense;

import java.nio.file.Path;
import java.util.Scanner;

/** Punto de entrada independiente del analizador forense. */
public class AnalizadorForenseMain {
    public static void main(String[] args) {
        Path raiz = Path.of(System.getProperty("user.dir"));
        try (Scanner teclado = new Scanner(System.in)) {
            System.out.println("=== MATRIX: ANALIZADOR FORENSE ===");
            System.out.println("Carpeta de datos: " + raiz.resolve("datos").toAbsolutePath());
            System.out.println("Se analizarán los archivos persistidos; no se consulta la memoria del simulador.");
            String respuesta = "";
            // Bucle que se repite hasta que se introduzca S o N (ignorando mayúsculas/minúsculas)
            while (true) {
                System.out.print("¿Iniciar análisis? (S/N): ");
                respuesta = teclado.nextLine().trim();

                if (respuesta.equalsIgnoreCase("S")) {
                    break; // Sale del bucle y continúa con el análisis
                } else if (respuesta.equalsIgnoreCase("N")) {
                    System.out.println("Análisis cancelado.");
                    return; // Termina la ejecución del programa
                } else {
                    System.out.println("Dato no correcto. Por favor, introduce S o N.");
                }
            }
            ResultadoForense resultado = new AnalizadorForense(raiz.resolve("datos"), raiz.resolve("resultados")).analizar();
            System.out.println("\nAnálisis terminado.");
            System.out.println("Incidente detectado: " + resultado.incidente());
            System.out.println("Conclusión: " + resultado.conclusion());
            System.out.println("Informe: " + raiz.resolve("resultados/informe.txt").toAbsolutePath());
            System.out.println("BIN de anomalías: " + raiz.resolve("resultados/anomalias.dat").toAbsolutePath());
            System.out.println("Entidades afectadas: " + raiz.resolve("resultados/entidades_afectadas.json").toAbsolutePath());
        } catch (Exception e) {
            System.err.println("No se pudo completar el análisis forense: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
