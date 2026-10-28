package es.matrix.datos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class XmlWorldRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void actualizaEstadoDeNodoEnXml() throws Exception {
        Path archivo = tempDir.resolve("estado.xml");
        Files.writeString(archivo, """
                <matrix>
                  <nodo id="N001" estado="NORMAL"/>
                </matrix>
                """);
        XmlWorldRepository repo = new XmlWorldRepository(archivo);
        repo.actualizarEstadoNodo("N001", "FALLO");
        assertTrue(Files.readString(archivo).contains("estado=\"FALLO\""));
    }

    @Test
    void rechazaEstadoDeNodoDesconocido() throws Exception {
        Path archivo = tempDir.resolve("estado.xml");
        Files.writeString(archivo, "<matrix><nodo id=\"N001\" estado=\"NORMAL\"/></matrix>");
        XmlWorldRepository repo = new XmlWorldRepository(archivo);
        assertThrows(IllegalArgumentException.class, () -> repo.actualizarEstadoNodo("N001", "ROTO"));
    }
}
