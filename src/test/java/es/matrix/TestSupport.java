package es.matrix;

import es.matrix.incidentes.EventoCriticoException;
import es.matrix.incidentes.IncidenteMatrix;
import es.matrix.simulador.InicializarDatosMain;
import es.matrix.simulador.SimuladorMatrix;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Utilidades comunes de las pruebas: datos controlados, reproducibles y en directorios temporales. */
public final class TestSupport {
    private TestSupport() { }

    public static void inicializar(Path datos) throws IOException {
        InicializarDatosMain.inicializar(datos);
    }

    /** Ejecuta el simulador en modo pruebas con semilla fija y comprueba que termina con el evento crítico. */
    public static void simularIncidente(Path datos, IncidenteMatrix incidente, long semilla) {
        SimuladorMatrix simulador = new SimuladorMatrix(datos, 1, true, incidente, 0, 0, new Random(semilla));
        assertThrows(EventoCriticoException.class, simulador::ejecutar);
    }

    /** Ejecuta una simulación normal corta, sin incidentes aleatorios. */
    public static void simularNormal(Path datos, long semilla) throws Exception {
        new SimuladorMatrix(datos, 1, false, null, 30, 0, new Random(semilla)).ejecutar();
    }
}
