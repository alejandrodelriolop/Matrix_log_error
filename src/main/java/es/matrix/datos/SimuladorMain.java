package es.matrix.datos;

import java.nio.file.Path;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Punto de entrada del simulador Matrix. */
public class SimuladorMain {
    public static void main(String[] args) {
        try {
            RunLogging.iniciar("simulador");
        } catch (Exception e) {
            System.err.println("No se pudieron preparar los logs: " + e.getMessage());
            return;
        }
        Logger logger = LogManager.getLogger(SimuladorMain.class);
        Logger evidencia = LogManager.getLogger("EVIDENCIA");
        Path carpetaDatos = Path.of("datos");
        logger.info("Inicio del punto de entrada del simulador");
        evidencia.info("INICIO_EJECUCION | tipo=simulador");

        try (Scanner teclado = new Scanner(System.in)) {
            boolean continuarSimulando = true;

            while (continuarSimulando) {
                System.out.println("\n=== MATRIX: SIMULADOR ===");
                System.out.println("1. Simulación normal (puede aparecer un incidente aleatorio)");
                System.out.println("2. Modo de pruebas (elige el incidente)");
                System.out.println("Escribe 'S' para cerrar la aplicación.");
                System.out.print("Selecciona el modo: ");

                String opcion = teclado.nextLine().trim();

                // Opción para salir directamente desde el menú principal
                if (opcion.equalsIgnoreCase("s")) {
                    System.out.println("Saliendo del simulador...");
                    break;
                }

                boolean modoPruebas;
                IncidenteMatrix incidenteElegido = null;

                if (opcion.equals("1")) {
                    modoPruebas = false;
                } else if (opcion.equals("2")) {
                    modoPruebas = true;

                    // Validación de la selección del incidente (del 1 al 6 o según enum)
                    while (true) {
                        System.out.println("\n=== INCIDENTES DISPONIBLES ===");
                        for (IncidenteMatrix incidente : IncidenteMatrix.values()) {
                            System.out.println(incidente.numero() + ". " + incidente.descripcion());
                        }
                        System.out.print("Selecciona el incidente que quieres reproducir (o escribe 'volver' para regresar al menú): ");
                        String entradaIncidente = teclado.nextLine().trim();

                        if (entradaIncidente.equalsIgnoreCase("volver")) {
                            incidenteElegido = null;
                            break;
                        }

                        try {
                            int numeroIncidente = Integer.parseInt(entradaIncidente);
                            incidenteElegido = IncidenteMatrix.desdeNumero(numeroIncidente);
                            break;
                        } catch (NumberFormatException e) {
                            System.out.println("Dato no correcto. Debes introducir un número.");
                        } catch (IllegalArgumentException e) {
                            System.out.println("Número fuera de rango o incidente no válido. Selecciona una opción de la lista.");
                        } catch (RuntimeException e) {
                            System.out.println("Número fuera de rango o incidente no válido. Selecciona una opción de la lista.");
                        }
                    }

                    // Si decidió volver al menú principal desde la lista de incidentes
                    if (incidenteElegido == null) {
                        continue;
                    }

                } else {
                    System.out.println("Opción no válida. Por favor, introduce 1, 2 o 'salir'.");
                    continue;
                }

                // Ejecución de la simulación
                try {
                    SimuladorMatrix simulador = new SimuladorMatrix(carpetaDatos, 30, modoPruebas, incidenteElegido);
                    simulador.ejecutar();
                    logger.info("Simulación finalizada normalmente");
                    evidencia.info("FIN_EJECUCION | resultado=FINALIZACION_NORMAL");
                } catch (EventoCriticoException e) {
                    logger.error("Incidente crítico; parada controlada: {}", e.getMessage());
                    evidencia.error("INCIDENTE_CRITICO | descripcion={}", e.getMessage());
                    System.err.println("\n[ALERTA] INCIDENTE CRÍTICO REGISTRADO: " + e.getMessage());
                    System.err.println("La simulación se ha detenido de forma controlada.");
                }

                // Separador visual para que los logs no se mezclen con el siguiente prompt
                System.out.println("\n--------------------------------------------------");

                // Pregunta si se desea realizar otro incidente o salir al terminar
                while (true) {
                    System.out.print("¿Deseas realizar otro incidente/simulación? (S/N): ");
                    String respuesta = teclado.nextLine().trim();

                    if (respuesta.equalsIgnoreCase("S")) {
                        break; // Vuelve al inicio del bucle principal (menú)
                    } else if (respuesta.equalsIgnoreCase("N")) {
                        System.out.println("Saliendo del simulador...");
                        continuarSimulando = false;
                        break;
                    } else {
                        System.out.println("Dato no correcto. Por favor, introduce S para continuar o N para salir.");
                    }
                }
            }

        } catch (Exception e) {
            logger.error("No se pudo ejecutar el simulador", e);
            evidencia.error("ERROR_EJECUCION | mensaje={}", e.getMessage());
            System.err.println("No se pudo ejecutar el simulador: " + e.getMessage());
            e.printStackTrace();
        }
    }
}