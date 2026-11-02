package es.matrix.configuracion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demostración de la rotación de logs: escribe suficientes líneas para superar el límite de tamaño
 * configurado en log4j2.xml y muestra el contenido de logs/runtime/ (log activo + históricos .gz).
 */
public class DemoRotacionLogsMain {
    public static void main(String[] args) throws IOException {
        RunLogging.iniciar("rotacion");
        Logger logger = LogManager.getLogger(DemoRotacionLogsMain.class);
        int lineas = args.length > 0 ? Integer.parseInt(args[0]) : 150;
        for (int i = 1; i <= lineas; i++) {
            logger.info("Línea de demostración {} para forzar el rollover por tamaño del log técnico", i);
        }
        System.out.println("\nContenido de logs/runtime/:");
        try (DirectoryStream<Path> ficheros = Files.newDirectoryStream(Path.of("logs", "runtime"))) {
            for (Path f : ficheros) System.out.printf("  %-45s %6d bytes%n", f.getFileName(), Files.size(f));
        }
    }
}
