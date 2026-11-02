package es.matrix.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.matrix.TestSupport;
import es.matrix.forense.AnalizadorForense;
import es.matrix.incidentes.IncidenteMatrix;
import es.matrix.modelo.NodoRegistro;
import es.matrix.modelo.ResultadoForense;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Flujo completo: datos -> incidente -> persistencia -> análisis -> salidas. */
class FlujoIntegracionTest {
    @TempDir Path datos;
    @TempDir Path salida;

    @Test
    void flujoCompletoDeUnaAlteracionDeZona() throws Exception {
        // 1. datos iniciales
        TestSupport.inicializar(datos);
        for (String f : new String[]{"operaciones.txt", "nodos.dat", "estado.xml", "reglas.json"}) {
            assertTrue(Files.exists(datos.resolve(f)), "falta " + f);
        }

        // 2-3. incidente + persistencia (la excepción crítica la comprueba TestSupport)
        TestSupport.simularIncidente(datos, IncidenteMatrix.ALTERACION_ZONA, 11);
        assertFalse(Files.readString(datos.resolve("operaciones.txt")).contains("ALTERACION_ZONA"),
                "ningún fichero de datos debe llevar el nombre del incidente");

        // 4. análisis independiente: solo recibe las carpetas
        ResultadoForense resultado = new AnalizadorForense(datos, salida).analizar();
        assertEquals("Alteración de zona", resultado.incidente());
        assertEquals("ALTA", resultado.certeza());

        // 5. salidas: TXT, BIN y JSON con funciones distintas
        String informe = Files.readString(salida.resolve("informe.txt"));
        assertTrue(informe.contains("Alteración de zona"));
        assertTrue(informe.contains("Efectos detectados:"));

        Path anomalias = salida.resolve("anomalias.dat");
        assertEquals(NodoRegistro.TAMANO_REGISTRO, Files.size(anomalias), "un único registro anómalo");
        assertTrue(Files.size(anomalias) < Files.size(datos.resolve("nodos.dat")), "no es una copia del BIN");
        try (RandomAccessFile raf = new RandomAccessFile(anomalias.toFile(), "r")) {
            raf.readInt();                       // id del nodo
            assertTrue(raf.readInt() < 80, "la sincronización del nodo de la zona alterada está bajo el mínimo");
        }

        JsonNode json = new ObjectMapper().readTree(salida.resolve("entidades_afectadas.json").toFile());
        JsonNode zona = json.path("entidadesAfectadas").path("zona").get(0);
        assertEquals("ALTERADA", zona.path("estado").asText());
        assertTrue(zona.path("integridad").asInt() < 80);
    }

    @Test
    void elAnalizadorFuncionaConUnaCopiaDeLosFicherosSinElSimulador() throws Exception {
        TestSupport.inicializar(datos);
        TestSupport.simularIncidente(datos, IncidenteMatrix.AGENTE_FUERA_PROTOCOLO, 5);
        Path copia = Files.createDirectory(salida.resolve("copia"));
        try (Stream<Path> ficheros = Files.list(datos)) {
            for (Path f : ficheros.toList()) Files.copy(f, copia.resolve(f.getFileName()));
        }

        ResultadoForense resultado = new AnalizadorForense(copia, salida.resolve("res")).analizar();

        assertEquals("Agente fuera de protocolo", resultado.incidente());
    }
}
