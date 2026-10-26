package es.matrix.datos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Prepara las carpetas y configura un identificador de log distinto por ejecución. */
public final class RunLogging {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private RunLogging() { }

    public static void iniciar(String tipoEjecucion) throws IOException {
        Files.createDirectories(Path.of("logs", "evidencias"));
        Files.createDirectories(Path.of("logs", "runtime"));
        String identificador = tipoEjecucion + "-" + LocalDateTime.now().format(FORMATO);
        System.setProperty("matrix.runId", identificador);
        LoggerContext contexto = (LoggerContext) LogManager.getContext(false);
        contexto.reconfigure();
    }
}