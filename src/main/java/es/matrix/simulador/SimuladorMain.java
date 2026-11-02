package es.matrix.simulador;

import es.matrix.configuracion.RunLogging;
import es.matrix.incidentes.EventoCriticoException;
import es.matrix.incidentes.IncidenteMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * Punto de entrada del simulador Matrix.
 *
 * <p>Sin argumentos muestra un menú interactivo. Con argumentos se ejecuta directamente:
 * <pre>--modo=normal|pruebas  --incidente=1..6 (solo en pruebas)  --duracion=segundos  --datos=carpeta</pre>
 */
public class SimuladorMain {
    private static final int DURACION_POR_DEFECTO = 30;

    public static void main(String[] args) {
        if (args.length == 0) {
            try (Scanner teclado = new Scanner(System.in)) {
                ejecutar(teclado);
            }
            return;
        }
        ejecutarConArgumentos(args);
    }

    /** Ejecución no interactiva a partir de argumentos (útil para scripts y demostraciones). */
    static void ejecutarConArgumentos(String[] args) {
        boolean modoPruebas = false;
        IncidenteMatrix incidente = null;
        int duracion = DURACION_POR_DEFECTO;
        Path datos = Path.of("datos");
        try {
            for (String arg : args) {
                if (arg.startsWith("--modo=")) {
                    String modo = arg.substring("--modo=".length());
                    if (modo.equalsIgnoreCase("pruebas")) modoPruebas = true;
                    else if (!modo.equalsIgnoreCase("normal")) throw new IllegalArgumentException("Modo no válido: " + modo);
                } else if (arg.startsWith("--incidente=")) {
                    incidente = IncidenteMatrix.desdeNumero(Integer.parseInt(arg.substring("--incidente=".length())));
                } else if (arg.startsWith("--duracion=")) {
                    duracion = Integer.parseInt(arg.substring("--duracion=".length()));
                } else if (arg.startsWith("--datos=")) {
                    datos = Path.of(arg.substring("--datos=".length()));
                } else {
                    throw new IllegalArgumentException("Argumento desconocido: " + arg);
                }
            }
            if (modoPruebas && incidente == null) throw new IllegalArgumentException("En modo pruebas indica --incidente=1..6");
            if (duracion <= 0) throw new IllegalArgumentException("La duración debe ser mayor que cero.");
        } catch (IllegalArgumentException e) {
            System.err.println("Argumentos no válidos: " + e.getMessage());
            System.err.println("Uso: SimuladorMain [--modo=normal|pruebas] [--incidente=1..6] [--duracion=segundos] [--datos=carpeta]");
            return;
        }
        if (!iniciarLogs()) return;
        Logger logger = LogManager.getLogger(SimuladorMain.class);
        Logger evidencia = LogManager.getLogger("EVIDENCIA");
        logger.info("Inicio del simulador por argumentos (modo={})", modoPruebas ? "PRUEBAS" : "NORMAL");
        evidencia.info("INICIO_EJECUCION | tipo=simulador");
        ejecutarSimulacion(datos, duracion, modoPruebas, incidente, logger, evidencia);
    }

    /** Menú interactivo. */
    public static void ejecutar(Scanner teclado) {
        if (!iniciarLogs()) return;
        Logger logger = LogManager.getLogger(SimuladorMain.class);
        Logger evidencia = LogManager.getLogger("EVIDENCIA");
        Path carpetaDatos = Path.of("datos");
        System.out.println("Archivo BIN: " + carpetaDatos.resolve("nodos.dat").toAbsolutePath());
        logger.info("Inicio del punto de entrada del simulador");
        evidencia.info("INICIO_EJECUCION | tipo=simulador");

        try {
            boolean continuarSimulando = true;
            while (continuarSimulando) {
                System.out.println("\n=== MATRIX: SIMULADOR ===");
                System.out.println("1. Simulación normal (puede aparecer un incidente aleatorio)");
                System.out.println("2. Modo de pruebas (elige el incidente)");
                System.out.println("Escribe 'S' para cerrar la aplicación.");
                System.out.print("Selecciona el modo: ");

                String opcion = teclado.nextLine().trim();
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
                    while (true) {
                        System.out.println("\n=== INCIDENTES DISPONIBLES ===");
                        for (IncidenteMatrix incidente : IncidenteMatrix.values()) {
                            System.out.println(incidente.numero() + ". " + incidente.descripcion());
                        }
                        System.out.print("Selecciona el incidente que quieres reproducir (o escribe 'volver' para regresar al menú): ");
                        String entrada = teclado.nextLine().trim();
                        if (entrada.equalsIgnoreCase("volver")) {
                            incidenteElegido = null;
                            break;
                        }
                        try {
                            incidenteElegido = IncidenteMatrix.desdeNumero(Integer.parseInt(entrada));
                            break;
                        } catch (NumberFormatException e) {
                            System.out.println("Dato no correcto. Debes introducir un número.");
                        } catch (IllegalArgumentException e) {
                            System.out.println("Número fuera de rango. Selecciona una opción de la lista.");
                        }
                    }
                    if (incidenteElegido == null) continue;
                } else {
                    System.out.println("Opción no válida. Introduce 1, 2 o 'S'.");
                    continue;
                }

                ejecutarSimulacion(carpetaDatos, DURACION_POR_DEFECTO, modoPruebas, incidenteElegido, logger, evidencia);
                System.out.println("\n--------------------------------------------------");

                while (true) {
                    System.out.print("¿Deseas realizar otro incidente/simulación? (S/N): ");
                    String respuesta = teclado.nextLine().trim();
                    if (respuesta.equalsIgnoreCase("S")) {
                        break;
                    } else if (respuesta.equalsIgnoreCase("N")) {
                        System.out.println("Saliendo del simulador...");
                        continuarSimulando = false;
                        break;
                    } else {
                        System.out.println("Dato no correcto. Introduce S para continuar o N para salir.");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error en el menú del simulador", e);
            evidencia.error("ERROR_EJECUCION | mensaje={}", e.getMessage());
            System.err.println("Error en el simulador: " + e.getMessage());
        }
    }

    /**
     * Ejecuta una simulación. Cuando aparece la situación crítica se captura la excepción de dominio,
     * se registra técnicamente con Log4j y se finaliza de forma controlada. Los datos ya están
     * persistidos antes de llegar aquí.
     */
    private static void ejecutarSimulacion(Path datos, int duracion, boolean modoPruebas, IncidenteMatrix incidente,
                                           Logger logger, Logger evidencia) {
        try {
            new SimuladorMatrix(datos, duracion, modoPruebas, incidente).ejecutar();
            logger.info("Simulación finalizada normalmente");
            evidencia.info("FIN_EJECUCION | resultado=FINALIZACION_NORMAL");
        } catch (EventoCriticoException e) {
            logger.error("Evento crítico capturado; parada controlada: {}", e.getMessage(), e);
            evidencia.error("INCIDENTE_CRITICO | descripcion={}", e.getMessage());
            System.err.println("\n[ALERTA] EVENTO CRÍTICO REGISTRADO: " + e.getMessage());
            System.err.println("La simulación se ha detenido de forma controlada.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Simulación interrumpida", e);
            System.err.println("Simulación interrumpida.");
        } catch (Exception e) {
            logger.error("No se pudo ejecutar el simulador", e);
            evidencia.error("ERROR_EJECUCION | mensaje={}", e.getMessage());
            System.err.println("No se pudo ejecutar el simulador: " + e.getMessage());
        }
    }

    private static boolean iniciarLogs() {
        try {
            RunLogging.iniciar("simulador");
            return true;
        } catch (Exception e) {
            System.err.println("No se pudieron preparar los logs: " + e.getMessage());
            return false;
        }
    }
}
