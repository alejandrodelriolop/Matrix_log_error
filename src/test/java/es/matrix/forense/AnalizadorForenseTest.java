package es.matrix.forense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.matrix.TestSupport;
import es.matrix.incidentes.IncidenteMatrix;
import es.matrix.modelo.NodoRegistro;
import es.matrix.modelo.ResultadoForense;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnalizadorForenseTest {
    @TempDir Path datos;
    @TempDir Path salida;

    @ParameterizedTest
    @EnumSource(IncidenteMatrix.class)
    void identificaCadaTipoDeIncidenteSoloConLosFicheros(IncidenteMatrix incidente) throws Exception {
        TestSupport.inicializar(datos);
        // Varias semillas = distintas entidades afectadas; los datos de ejecuciones anteriores se acumulan.
        for (long semilla = 1; semilla <= 5; semilla++) {
            TestSupport.simularIncidente(datos, incidente, semilla);

            ResultadoForense resultado = new AnalizadorForense(datos, salida).analizar();

            assertEquals(incidente.descripcion(), resultado.incidente(), "semilla " + semilla);
            assertEquals("ALTA", resultado.certeza(), "semilla " + semilla);
            long bytes = Files.size(salida.resolve("anomalias.dat"));
            assertTrue(bytes > 0 && bytes % NodoRegistro.TAMANO_REGISTRO == 0, "el BIN de salida debe tener registros completos");
        }
    }

    @Test
    void usaSoloLaUltimaEjecucion() throws Exception {
        TestSupport.inicializar(datos);
        TestSupport.simularIncidente(datos, IncidenteMatrix.PERDIDA_SINCRONIZACION, 1);
        TestSupport.simularIncidente(datos, IncidenteMatrix.FALLO_NODO, 2);
        assertEquals(IncidenteMatrix.FALLO_NODO.descripcion(), new AnalizadorForense(datos, salida).analizar().incidente());

        TestSupport.simularIncidente(datos, IncidenteMatrix.PERDIDA_SINCRONIZACION, 3);
        assertEquals(IncidenteMatrix.PERDIDA_SINCRONIZACION.descripcion(), new AnalizadorForense(datos, salida).analizar().incidente());
    }

    @Test
    void unaEjecucionNormalPosteriorNoSeConfundeConUnIncidenteAntiguo() throws Exception {
        TestSupport.inicializar(datos);
        TestSupport.simularIncidente(datos, IncidenteMatrix.ACCESO_NO_AUTORIZADO, 1);
        TestSupport.simularNormal(datos, 2);

        ResultadoForense resultado = new AnalizadorForense(datos, salida).analizar();

        assertEquals("INDETERMINADO", resultado.incidente());
    }

    @Test
    void datosSinIncidenteDanResultadoIndeterminadoYGeneranLasTresSalidas() throws Exception {
        TestSupport.inicializar(datos);

        ResultadoForense resultado = new AnalizadorForense(datos, salida).analizar();

        assertEquals("INDETERMINADO", resultado.incidente());
        assertTrue(Files.exists(salida.resolve("informe.txt")));
        assertEquals(0, Files.size(salida.resolve("anomalias.dat")));
        assertTrue(Files.exists(salida.resolve("entidades_afectadas.json")));
    }

    @Test
    void elJsonDeSalidaSoloContieneLosElementosAfectados() throws Exception {
        TestSupport.inicializar(datos);
        TestSupport.simularIncidente(datos, IncidenteMatrix.ACCESO_NO_AUTORIZADO, 7);

        new AnalizadorForense(datos, salida).analizar();

        JsonNode raiz = new ObjectMapper().readTree(salida.resolve("entidades_afectadas.json").toFile());
        JsonNode entidades = raiz.path("entidadesAfectadas");
        assertEquals("Acceso no autorizado", raiz.path("incidente").asText());
        assertEquals(1, entidades.path("sujeto").size(), "solo el sujeto implicado");
        assertEquals(1, entidades.path("zona").size(), "solo la zona a la que accedió");
        assertEquals(1, entidades.path("nodo").size(), "solo el nodo de esa zona");
        assertTrue(entidades.path("agente").isMissingNode(), "los agentes no intervienen");
    }

    @Test
    void elInformeIncluyeIncidenteEfectosYConclusion() throws Exception {
        TestSupport.inicializar(datos);
        TestSupport.simularIncidente(datos, IncidenteMatrix.FALLO_NODO, 3);

        new AnalizadorForense(datos, salida).analizar();

        String informe = Files.readString(salida.resolve("informe.txt"));
        assertTrue(informe.contains("Incidente identificado: Fallo de nodo"));
        assertTrue(informe.contains("Efectos detectados:"));
        assertTrue(informe.contains("Conclusión:"));
    }

    @Test
    void fallaSiFaltaUnFicheroDeEntrada() throws Exception {
        TestSupport.inicializar(datos);
        Files.delete(datos.resolve("nodos.dat"));
        assertThrows(IOException.class, () -> new AnalizadorForense(datos, salida).analizar());
    }
}
