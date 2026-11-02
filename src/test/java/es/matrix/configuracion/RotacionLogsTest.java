package es.matrix.configuracion;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RotacionLogsTest {
    @TempDir Path logs;

    @Test
    void laConfiguracionExternaDefineRotacionPorTamanoConCompresionYLimite() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/log4j2.xml")) {
            assertNotNull(in, "log4j2.xml debe estar en src/main/resources");
            Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Element rolling = (Element) xml.getElementsByTagName("RollingFile").item(0);
            assertNotNull(rolling);
            assertTrue(rolling.getAttribute("filePattern").endsWith(".log.gz"), "los históricos se comprimen en .gz");
            assertEquals(1, xml.getElementsByTagName("SizeBasedTriggeringPolicy").getLength());
            Element estrategia = (Element) xml.getElementsByTagName("DefaultRolloverStrategy").item(0);
            assertTrue(Integer.parseInt(estrategia.getAttribute("max")) > 0, "debe limitarse el número de históricos");
        }
    }

    @Test
    void elLogTecnicoRotaYComprimeAlSuperarElTamanoConfigurado() throws Exception {
        System.setProperty("matrix.logDir", logs.toString());
        LoggerContext contexto = null;
        try {
            URI configuracion = getClass().getResource("/log4j2.xml").toURI();
            contexto = Configurator.initialize("rotacion-test", getClass().getClassLoader(), configuracion);
            Logger logger = contexto.getLogger("rotacion");
            for (int i = 0; i < 200; i++) {
                logger.info("Línea de prueba {} para superar el límite de tamaño del log técnico", i);
            }
        } finally {
            if (contexto != null) Configurator.shutdown(contexto);
            System.clearProperty("matrix.logDir");
        }

        Path runtime = logs.resolve("runtime");
        assertTrue(Files.exists(runtime.resolve("matrix-runtime.log")), "log activo");
        List<Path> historicos;
        try (Stream<Path> ficheros = Files.list(runtime)) {
            historicos = ficheros.filter(p -> p.getFileName().toString().endsWith(".log.gz")).toList();
        }
        assertFalse(historicos.isEmpty(), "debe existir al menos un histórico comprimido");
        assertTrue(historicos.size() <= 10, "no se conservan más históricos que el máximo configurado");
        historicos.forEach(p -> assertTrue(p.getFileName().toString().matches("matrix-runtime-\\d{4}-\\d{2}-\\d{2}-\\d+\\.log\\.gz")));
    }
}
