package es.matrix.main;

import es.matrix.simulador.InicializarDatosMain;
import es.matrix.simulador.SimuladorMain;

import java.util.Scanner;

/** Gestiona la presentación y navegación principal de Matrix. */
public class MenuMatrix {

    public void iniciar() {
        try (Scanner teclado = new Scanner(System.in)) {
            boolean ejecutando = true;

            mostrarPresentacion();

            while (ejecutando) {
                mostrarMenu();
                String opcion = teclado.nextLine().trim();

                switch (opcion) {
                    case "1" -> InicializarDatosMain.ejecutar();
                    case "2" -> SimuladorMain.ejecutar(teclado);
                    case "0" -> ejecutando = false;
                    default -> System.out.println("Opción no válida.");
                }

                if (ejecutando) {
                    System.out.println("\nPulsa ENTER para continuar...");
                    teclado.nextLine();
                }
            }
        }

        System.out.println("Conexión con Matrix finalizada.");
    }

    private void mostrarPresentacion() {
        System.out.println("========================================");
        System.out.println("              THE MATRIX                ");
        System.out.println("========================================");
        System.out.println("La simulación permanece estable... por ahora.");
        System.out.println("Tu misión es supervisar el sistema y detectar cualquier anomalía.");
    }

    private void mostrarMenu() {
        System.out.println("\n========== MENÚ PRINCIPAL ==========");
        System.out.println("1. Inicializar y comprobar datos");
        System.out.println("2. Iniciar simulador");
        System.out.println("0. Salir");
        System.out.print("Selecciona una opción: ");
    }
}
