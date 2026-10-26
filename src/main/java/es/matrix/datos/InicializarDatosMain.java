package es.matrix.datos;

import com.fasterxml.jackson.databind.JsonNode;
import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Punto de entrada de la fase 1: crea datos iniciales y prueba las cuatro persistencias. */
public class InicializarDatosMain {
    private static final Path DATOS = Path.of("datos");

    public static void main(String[] args) {
        try {
            RunLogging.iniciar("inicializacion");
        } catch (Exception e) {
            System.err.println("No se pudieron preparar los logs: " + e.getMessage());
            return;
        }
        Logger logger = LogManager.getLogger(InicializarDatosMain.class);
        Logger evidencia = LogManager.getLogger("EVIDENCIA");
        try {
            Files.createDirectories(DATOS);
            crearReglasSiNoExisten();
            crearEstadoXmlSiNoExiste();
            crearHistoricoSiNoExiste();
            crearBinarioSiNoExiste();

            TextEventRepository eventos = new TextEventRepository(DATOS.resolve("operaciones.txt"));
            BinaryNodeRepository nodos = new BinaryNodeRepository(DATOS.resolve("nodos.dat"));
            XmlWorldRepository mundo = new XmlWorldRepository(DATOS.resolve("estado.xml"));
            JsonRulesRepository reglasRepo = new JsonRulesRepository(DATOS.resolve("reglas.json"));

            System.out.println("=== Matrix: comprobación de contrato de datos ===");
            System.out.println("Eventos TXT leídos: " + eventos.leerTodos().size());
            System.out.println("Registros BIN: " + nodos.contarRegistros());
            System.out.println("Nodo N002 leído por acceso directo: " + nodos.leerPorId(2));
            JsonNode reglas = reglasRepo.leerReglas();
            System.out.println("Sincronización mínima: " + reglas.path("sincronizacion").path("nivelMinimo").asInt());
            System.out.println("XML válido; raíz: " + mundo.leerEstado().getDocumentElement().getTagName());
            System.out.println("Fase 1 inicializada correctamente.");
            logger.info("Inicialización y comprobación de datos completadas");
            evidencia.info("INICIALIZACION_COMPLETADA | eventos={} | nodos={}", eventos.leerTodos().size(), nodos.contarRegistros());
        } catch (Exception e) {
            logger.error("No se pudo inicializar la fase 1", e);
            evidencia.error("ERROR_INICIALIZACION | mensaje={}", e.getMessage());
            System.err.println("No se pudo inicializar la fase 1: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void crearReglasSiNoExisten() throws IOException {
        Path archivo = DATOS.resolve("reglas.json");
        if (Files.notExists(archivo)) {
            String json = """
                    {
                      "sincronizacion": { "nivelMinimo": 80 },
                      "carga": { "nivelMaximo": 90.0 },
                      "integridad": { "nivelMinimo": 80 },
                      "sujetos": { "permitirCambiosDeZonaNoAutorizados": false },
                      "agentes": { "permitirAccionesFueraDeProtocolo": false }
                    }
                    """;
            Files.writeString(archivo, json, StandardCharsets.UTF_8);
        }
    }

    private static void crearEstadoXmlSiNoExiste() throws IOException {
        Path archivo = DATOS.resolve("estado.xml");
        if (Files.notExists(archivo)) {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <matrix>
                      <sujetos>
                        <sujeto id="S001" zonaActual="Z001" estado="ACTIVO"/>
                        <sujeto id="S002" zonaActual="Z001" estado="ACTIVO"/>
                        <sujeto id="S003" zonaActual="Z002" estado="ACTIVO"/>
                        <sujeto id="S004" zonaActual="Z002" estado="ACTIVO"/>
                        <sujeto id="S005" zonaActual="Z003" estado="ACTIVO"/>
                        <sujeto id="S006" zonaActual="Z003" estado="ACTIVO"/>
                        <sujeto id="S007" zonaActual="Z004" estado="ACTIVO"/>
                        <sujeto id="S008" zonaActual="Z004" estado="ACTIVO"/>
                      </sujetos>
                      <agentes>
                        <agente id="A001" zonaAsignada="Z001" estado="ACTIVO"/>
                        <agente id="A002" zonaAsignada="Z002" estado="ACTIVO"/>
                        <agente id="A003" zonaAsignada="Z003" estado="ACTIVO"/>
                      </agentes>
                      <zonas>
                        <zona id="Z001" integridad="100" estado="NORMAL"/>
                        <zona id="Z002" integridad="100" estado="NORMAL"/>
                        <zona id="Z003" integridad="100" estado="NORMAL"/>
                        <zona id="Z004" integridad="100" estado="NORMAL"/>
                      </zonas>
                      <nodos>
                        <nodo id="N001" idBinario="1" zonaId="Z001" estado="NORMAL"/>
                        <nodo id="N002" idBinario="2" zonaId="Z002" estado="NORMAL"/>
                        <nodo id="N003" idBinario="3" zonaId="Z003" estado="NORMAL"/>
                        <nodo id="N004" idBinario="4" zonaId="Z004" estado="NORMAL"/>
                      </nodos>
                      <permisos>
                        <permiso actorId="S001" zonaId="Z001" permitido="true"/>
                        <permiso actorId="S002" zonaId="Z001" permitido="true"/>
                        <permiso actorId="S003" zonaId="Z002" permitido="true"/>
                        <permiso actorId="S004" zonaId="Z002" permitido="true"/>
                        <permiso actorId="S005" zonaId="Z003" permitido="true"/>
                        <permiso actorId="S006" zonaId="Z003" permitido="true"/>
                        <permiso actorId="S007" zonaId="Z004" permitido="true"/>
                        <permiso actorId="S008" zonaId="Z004" permitido="true"/>
                      </permisos>
                    </matrix>
                    """;
            Files.writeString(archivo, xml, StandardCharsets.UTF_8);
        }
    }

    private static void crearHistoricoSiNoExiste() throws IOException {
        Path archivo = DATOS.resolve("operaciones.txt");
        if (Files.notExists(archivo)) {
            TextEventRepository repo = new TextEventRepository(archivo);
            LocalDateTime ahora = LocalDateTime.now().withNano(0);
            List<Evento> iniciales = List.of(
                    new Evento("E0001", ahora, "S001", "CONEXION", "Z001", "OK"),
                    new Evento("E0002", ahora.plusSeconds(2), "A001", "INSPECCION", "Z001", "OK"),
                    new Evento("E0003", ahora.plusSeconds(4), "S003", "ACCESO", "Z002", "OK")
            );
            for (Evento evento : iniciales) repo.guardar(evento);
        }
    }

    private static void crearBinarioSiNoExiste() throws IOException {
        Path archivo = DATOS.resolve("nodos.dat");
        if (Files.notExists(archivo) || Files.size(archivo) == 0) {
            BinaryNodeRepository repo = new BinaryNodeRepository(archivo);
            for (int i = 1; i <= 4; i++) {
                repo.anadir(new NodoRegistro(i, 100, 25.0 + i, 0, 0));
            }
        }
    }
}
