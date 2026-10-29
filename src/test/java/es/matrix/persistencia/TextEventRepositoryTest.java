package es.matrix.persistencia;

import es.matrix.modelo.Evento;
import es.matrix.persistencia.TextEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TextEventRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void guardaYLeeEventosEnOrden() throws Exception {
        TextEventRepository repo = new TextEventRepository(tempDir.resolve("operaciones.txt"));
        Evento uno = new Evento("E0001", LocalDateTime.parse("2026-10-27T10:00:00"),
                "S001", "MOVIMIENTO", "Z002", "CORRECTO");
        Evento dos = new Evento("E0002", LocalDateTime.parse("2026-10-27T10:00:01"),
                "A001", "COMPROBACION", "N001", "CORRECTO");
        repo.guardar(uno);
        repo.guardar(dos);
        assertEquals(java.util.List.of(uno, dos), repo.leerTodos());
    }

    @Test
    void informaSiHayUnaLineaCorrupta() throws Exception {
        Path archivo = tempDir.resolve("malo.txt");
        java.nio.file.Files.writeString(archivo, "esta|linea|no|tiene|seis\n");
        TextEventRepository repo = new TextEventRepository(archivo);
        assertThrows(java.io.IOException.class, repo::leerTodos);
    }
}
