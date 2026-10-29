package es.matrix.persistencia;

import es.matrix.persistencia.BinaryNodeRepository;
import es.matrix.modelo.NodoRegistro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BinaryNodeRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void escribeLeeYActualizaUnRegistroPorAccesoDirecto() throws Exception {
        BinaryNodeRepository repo = new BinaryNodeRepository(tempDir.resolve("nodos.dat"));
        repo.anadir(new NodoRegistro(1, 98, 20.0, 0, 3));
        repo.anadir(new NodoRegistro(2, 75, 40.0, 1, 5));
        assertEquals(2, repo.contarRegistros());
        assertEquals(75, repo.leerPorId(2).nivelSincronizacion());

        repo.modificar(new NodoRegistro(2, 60, 45.0, 2, 6));
        assertEquals(60, repo.leerPorId(2).nivelSincronizacion());
        assertEquals("FALLO", repo.leerPorId(2).estadoTexto());
    }

    @Test
    void rechazaArchivoConRegistroIncompleto() throws Exception {
        Path archivo = tempDir.resolve("corrupto.dat");
        java.nio.file.Files.write(archivo, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> new BinaryNodeRepository(archivo));
    }

    @Test
    void rechazaIdQueNoExiste() throws Exception {
        BinaryNodeRepository repo = new BinaryNodeRepository(tempDir.resolve("vacio.dat"));
        assertThrows(IOException.class, () -> repo.leerPorId(1));
    }
}