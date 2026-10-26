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
            System.out.println("=== MATRIX: SIMULADOR ===");
            System.out.println("1. Simulación normal (puede aparecer un incidente aleatorio)");
            System.out.println("2. Modo de pruebas (elige el incidente)");
            System.out.print("Selecciona el modo: ");
            String opcion = teclado.nextLine().trim();
            boolean modoPruebas;
            IncidenteMatrix incidenteElegido = null;

            if (opcion.equals("1")) {
                modoPruebas = false;
            } else if (opcion.equals("2")) {
                modoPruebas = true;
                System.out.println("\n=== INCIDENTES DISPONIBLES ===");
                for (IncidenteMatrix incidente : IncidenteMatrix.values()) {
                    System.out.println(incidente.numero() + ". " + incidente.descripcion());
                }
                System.out.print("Selecciona el incidente que quieres reproducir: ");
                try {
                    incidenteElegido = IncidenteMatrix.desdeNumero(Integer.parseInt(teclado.nextLine().trim()));
                } catch (RuntimeException e) {
                    System.out.println("Selección no válida. No se ha iniciado la simulación.");
                    return;
                }
            } else {
                System.out.println("Opción no válida. No se ha iniciado la simulación.");
                return;
            }

            SimuladorMatrix simulador = new SimuladorMatrix(carpetaDatos, 30, modoPruebas, incidenteElegido);
            simulador.ejecutar();
            logger.info("Simulación finalizada normalmente");
            evidencia.info("FIN_EJECUCION | resultado=FINALIZACION_NORMAL");
        } catch (EventoCriticoException e) {
            logger.error("Incidente crítico; parada controlada: {}", e.getMessage());
            evidencia.error("INCIDENTE_CRITICO | descripcion={}", e.getMessage());
            System.err.println("INCIDENTE CRÍTICO REGISTRADO: " + e.getMessage());
            System.err.println("La simulación se ha detenido de forma controlada.");
        } catch (Exception e) {
            logger.error("No se pudo ejecutar el simulador", e);
            evidencia.error("ERROR_EJECUCION | mensaje={}", e.getMessage());
            System.err.println("No se pudo ejecutar el simulador: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
