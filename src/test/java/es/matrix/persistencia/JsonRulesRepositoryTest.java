package es.matrix.persistencia;

import es.matrix.persistencia.JsonRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JsonRulesRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void leeReglasValidas() throws Exception {
        Path archivo = tempDir.resolve("reglas.json");
        Files.writeString(archivo, """
                {
                  "sincronizacion":{"nivelMinimo":80},
                  "carga":{"nivelMaximo":90},
                  "integridad":{"nivelMinimo":80}
                }
                """);
        assertEquals(80, new JsonRulesRepository(archivo).leerReglas()
                .path("sincronizacion").path("nivelMinimo").asInt());
    }

    @Test
    void rechazaReglasFueraDeRango() throws Exception {
        Path archivo = tempDir.resolve("reglas.json");
        Files.writeString(archivo, """
                {
                  "sincronizacion":{"nivelMinimo":150},
                  "carga":{"nivelMaximo":90},
                  "integridad":{"nivelMinimo":80}
                }
                """);
        assertThrows(java.io.IOException.class, () -> new JsonRulesRepository(archivo).leerReglas());
    }
}
