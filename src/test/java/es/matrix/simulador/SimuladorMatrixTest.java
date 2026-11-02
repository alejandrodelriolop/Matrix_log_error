package es.matrix.simulador;

import es.matrix.TestSupport;
import es.matrix.incidentes.EventoCriticoException;
import es.matrix.incidentes.IncidenteMatrix;
import es.matrix.modelo.Evento;
import es.matrix.persistencia.TextEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SimuladorMatrixTest {
    @TempDir Path datos;

    @ParameterizedTest
    @EnumSource(IncidenteMatrix.class)
    void cadaIncidenteLanzaLaExcepcionCriticaDespuesDePersistirLosDatos(IncidenteMatrix incidente) throws Exception {
        TestSupport.inicializar(datos);
        TextEventRepository historico = new TextEventRepository(datos.resolve("operaciones.txt"));
        int antes = historico.leerTodos().size();

        SimuladorMatrix simulador = new SimuladorMatrix(datos, 1, true, incidente, 0, 0, new Random(42));
        EventoCriticoException excepcion = assertThrows(EventoCriticoException.class, simulador::ejecutar);

        assertEquals(incidente.descripcion(), excepcion.getMessage());
        // Las evidencias ya estaban en disco cuando se lanzó la excepción.
        List<Evento> despues = historico.leerTodos();
        assertTrue(despues.size() > antes, "debe haber eventos nuevos persistidos");
        // El fichero de texto describe síntomas, nunca el nombre del incidente.
        assertFalse(Files.readString(datos.resolve("operaciones.txt")).contains(incidente.name()));
    }

    @Test
    void modoNormalSinIncidentesTerminaSinExcepcionYGuardaOperaciones() throws Exception {
        TestSupport.inicializar(datos);
        TextEventRepository historico = new TextEventRepository(datos.resolve("operaciones.txt"));
        int antes = historico.leerTodos().size();

        TestSupport.simularNormal(datos, 1);

        List<Evento> eventos = historico.leerTodos();
        assertTrue(eventos.size() > antes);
        eventos.subList(antes, eventos.size()).forEach(e -> assertEquals("OK", e.resultado()));
    }

    @Test
    void elModoPruebasExigeElegirUnIncidente() {
        assertThrows(IllegalArgumentException.class, () -> new SimuladorMatrix(datos, 1, true, null));
    }

    @Test
    void rechazaUnBinarioQueNoCoincideConElXml() throws Exception {
        TestSupport.inicializar(datos);
        Path binario = datos.resolve("nodos.dat");
        Files.write(binario, Arrays.copyOf(Files.readAllBytes(binario), 28)); // solo 1 de 4 registros
        SimuladorMatrix simulador = new SimuladorMatrix(datos, 1, false, null, 0, 0, new Random(1));
        assertThrows(IOException.class, simulador::ejecutar);
    }
}
