package es.matrix.datos;

import es.matrix.modelo.Evento;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Lectura y escritura del histórico secuencial operaciones.txt. */
public class TextEventRepository {
    private final Path archivo;

    public TextEventRepository(Path archivo) throws IOException {
        this.archivo = archivo;
        Path padre = archivo.toAbsolutePath().getParent();
        if (padre != null) Files.createDirectories(padre);
        if (Files.notExists(archivo)) Files.createFile(archivo);
    }

    public void guardar(Evento evento) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(evento.aLinea());
            writer.newLine();
        }
    }

    public List<Evento> leerTodos() throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            int numeroLinea = 0;
            while ((linea = reader.readLine()) != null) {
                numeroLinea++;
                if (linea.isBlank()) continue;
                try {
                    eventos.add(Evento.desdeLinea(linea));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Error en operaciones.txt, línea " + numeroLinea + ": " + e.getMessage(), e);
                }
            }
        }
        return eventos;
    }

    public Path getArchivo() {
        return archivo;
    }
}
