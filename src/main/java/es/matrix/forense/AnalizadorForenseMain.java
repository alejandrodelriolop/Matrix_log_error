package es.matrix.forense;

import es.matrix.configuracion.RunLogging;
import es.matrix.modelo.ResultadoForense;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * Punto de entrada independiente del analizador forense.
 *
 */
public class AnalizadorForenseMain {
    public static void main(String[] args) {
        Path datos = Path.of("datos");
        Path resultados = Path.of("resultados");
        boolean auto = false;
        for (String arg : args) {
            if (arg.equals("--auto")) auto = true;
            else if (arg.startsWith("--datos=")) datos = Path.of(arg.substring("--datos=".length()));
            else if (arg.startsWith("--resultados=")) resultados = Path.of(arg.substring("--resultados=".length()));
            else {
                System.err.println("Argumento desconocido: " + arg);
                System.err.println("Uso: AnalizadorForenseMain [--auto] [--datos=carpeta] [--resultados=carpeta]");
                return;
            }
        }
        try {
            RunLogging.iniciar("analizador");
        } catch (Exception e) {
            System.err.println("No se pudieron preparar los logs: " + e.getMessage());
            return;
        }
        Logger logger = LogManager.getLogger(AnalizadorForenseMain.class);
        Logger evidencia = LogManager.getLogger("EVIDENCIA");
        logger.info("Inicio del analizador forense");
        evidencia.info("INICIO_EJECUCION | tipo=analizador");

        System.out.println("=== MATRIX: ANALIZADOR FORENSE ===");
        System.out.println("Carpeta de datos: " + datos.toAbsolutePath());
        System.out.println("Se analizan únicamente los ficheros persistidos; no se consulta la memoria del simulador.");
        if (!auto && !confirmar()) {
            System.out.println("Análisis cancelado.");
            logger.info("Análisis cancelado por el usuario");
            return;
        }
        try {
            ResultadoForense resultado = new AnalizadorForense(datos, resultados).analizar();
            System.out.println("\nAnálisis terminado.");
            System.out.println("Incidente detectado: " + resultado.incidente() + " (certeza " + resultado.certeza() + ")");
            System.out.println("Conclusión: " + resultado.conclusion());
            System.out.println("Informe: " + resultados.resolve("informe.txt").toAbsolutePath());
            System.out.println("BIN de anomalías: " + resultados.resolve("anomalias.dat").toAbsolutePath());
            System.out.println("Entidades afectadas: " + resultados.resolve("entidades_afectadas.json").toAbsolutePath());
            logger.info("Análisis finalizado");
            evidencia.info("FIN_EJECUCION | resultado=FINALIZACION_NORMAL");
        } catch (Exception e) {
            logger.error("No se pudo completar el análisis forense", e);
            evidencia.error("ERROR_EJECUCION | mensaje={}", e.getMessage());
            System.err.println("No se pudo completar el análisis forense: " + e.getMessage());
        }
    }

    private static boolean confirmar() {
        try (Scanner teclado = new Scanner(System.in)) {
            while (true) {
                System.out.print("¿Iniciar análisis? (S/N): ");
                String respuesta = teclado.nextLine().trim();
                if (respuesta.equalsIgnoreCase("S")) return true;
                if (respuesta.equalsIgnoreCase("N")) return false;
                System.out.println("Dato no correcto. Introduce S o N.");
            }
        }
    }
}
