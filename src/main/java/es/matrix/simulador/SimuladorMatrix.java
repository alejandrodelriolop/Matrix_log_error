package es.matrix.simulador;

import com.fasterxml.jackson.databind.JsonNode;
import es.matrix.incidentes.EventoCriticoException;
import es.matrix.incidentes.IncidenteMatrix;
import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;
import es.matrix.persistencia.BinaryNodeRepository;
import es.matrix.persistencia.JsonRulesRepository;
import es.matrix.persistencia.TextEventRepository;
import es.matrix.persistencia.XmlWorldRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Coordina el ciclo de simulación y la persistencia de operaciones e incidentes.
 *
 * <p>Cada incidente deja una huella coherente en las tres fuentes (TXT, BIN y XML) usando los
 * mismos mecanismos que la operativa normal. Ningún fichero contiene el nombre del incidente:
 * el analizador debe deducirlo cruzando las evidencias.
 */
public class SimuladorMatrix {
    private static final Logger LOGGER = LogManager.getLogger(SimuladorMatrix.class);
    private static final Logger EVIDENCIA = LogManager.getLogger("EVIDENCIA");

    private static final long PAUSA_POR_DEFECTO_MS = 700;
    private static final int PROBABILIDAD_INCIDENTE_POR_DEFECTO = 3;

    private final Path carpetaDatos;
    private final int duracionSegundos;
    private final boolean modoPruebas;
    private final IncidenteMatrix incidenteElegido;
    private final long pausaMs;
    private final int probabilidadIncidente;
    private final Random random;

    private TextEventRepository eventos;
    private BinaryNodeRepository nodos;
    private XmlWorldRepository mundo;
    private JsonRulesRepository reglasRepository;
    private int siguienteEvento;
    private String idEjecucion;
    private int umbralSincronizacion;
    private double umbralCarga;
    private int umbralIntegridad;

    public SimuladorMatrix(Path carpetaDatos, int duracionSegundos, boolean modoPruebas, IncidenteMatrix incidenteElegido) {
        this(carpetaDatos, duracionSegundos, modoPruebas, incidenteElegido,
                PAUSA_POR_DEFECTO_MS, PROBABILIDAD_INCIDENTE_POR_DEFECTO, new Random());
    }

    /**
     * @param pausaMs               pausa entre ciclos (0 en las pruebas automáticas)
     * @param probabilidadIncidente % de probabilidad por ciclo de incidente aleatorio en modo normal
     * @param random                generador de aleatoriedad (con semilla fija las pruebas son reproducibles)
     */
    public SimuladorMatrix(Path carpetaDatos, int duracionSegundos, boolean modoPruebas, IncidenteMatrix incidenteElegido,
                           long pausaMs, int probabilidadIncidente, Random random) {
        if (carpetaDatos == null) throw new IllegalArgumentException("La carpeta de datos es obligatoria.");
        if (duracionSegundos <= 0) throw new IllegalArgumentException("La duración debe ser mayor que cero.");
        if (modoPruebas && incidenteElegido == null) throw new IllegalArgumentException("En modo pruebas debes seleccionar un incidente.");
        if (pausaMs < 0) throw new IllegalArgumentException("La pausa no puede ser negativa.");
        if (probabilidadIncidente < 0 || probabilidadIncidente > 100) throw new IllegalArgumentException("La probabilidad debe estar entre 0 y 100.");
        this.carpetaDatos = carpetaDatos;
        this.duracionSegundos = duracionSegundos;
        this.modoPruebas = modoPruebas;
        this.incidenteElegido = incidenteElegido;
        this.pausaMs = pausaMs;
        this.probabilidadIncidente = probabilidadIncidente;
        this.random = random;
    }

    public void ejecutar() throws IOException, InterruptedException, EventoCriticoException {
        prepararRepositorios();
        validarDatosIniciales();
        LOGGER.info("Datos iniciales validados; modo={}, duración={} segundos", modoPruebas ? "PRUEBAS" : "NORMAL", duracionSegundos);
        EVIDENCIA.info("DATOS_VALIDADOS | modo={} | duracionSegundos={}", modoPruebas ? "PRUEBAS" : "NORMAL", duracionSegundos);
        siguienteEvento = eventos.leerTodos().size() + 1;
        idEjecucion = "RUN-" + UUID.randomUUID();
        LOGGER.info("Iniciando ejecución {}", idEjecucion);
        EVIDENCIA.info("INICIO_EJECUCION | idEjecucion={}", idEjecucion);
        System.out.println("Inicio de Matrix. Duración máxima: " + duracionSegundos + " segundos.");
        System.out.println("Modo: " + (modoPruebas ? "PRUEBAS" : "NORMAL"));
        long inicio = System.nanoTime();
        long duracionNanos = duracionSegundos * 1_000_000_000L;
        int operaciones = 0;
        boolean incidenteGenerado = false;

        while (System.nanoTime() - inicio < duracionNanos) {
            realizarOperacionNormal();
            operaciones++;
            if (modoPruebas && !incidenteGenerado) {
                incidenteGenerado = true;
                inyectarIncidente(incidenteElegido);
            } else if (!modoPruebas && random.nextInt(100) < probabilidadIncidente) {
                IncidenteMatrix aleatorio = IncidenteMatrix.values()[random.nextInt(IncidenteMatrix.values().length)];
                inyectarIncidente(aleatorio);
            }
            mostrarProgreso(inicio, operaciones);
            if (pausaMs > 0) Thread.sleep(pausaMs);
        }
        System.out.println();
        LOGGER.info("Simulación finalizada tras {} operaciones", operaciones);
        EVIDENCIA.info("FIN_EJECUCION | resultado=FINALIZACION_NORMAL | operaciones={}", operaciones);
        System.out.println("Simulación finalizada.");
        System.out.println("Operaciones generadas en esta ejecución: " + operaciones);
        System.out.println("Histórico: " + carpetaDatos.resolve("operaciones.txt").toAbsolutePath());
        System.out.println("Estado binario: " + carpetaDatos.resolve("nodos.dat").toAbsolutePath());
        System.out.println("Estado XML: " + carpetaDatos.resolve("estado.xml").toAbsolutePath());
    }

    private void prepararRepositorios() throws IOException {
        Files.createDirectories(carpetaDatos);
        eventos = new TextEventRepository(carpetaDatos.resolve("operaciones.txt"));
        nodos = new BinaryNodeRepository(carpetaDatos.resolve("nodos.dat"));
        mundo = new XmlWorldRepository(carpetaDatos.resolve("estado.xml"));
        reglasRepository = new JsonRulesRepository(carpetaDatos.resolve("reglas.json"));
    }

    private void validarDatosIniciales() throws IOException {
        Document doc = mundo.leerEstado();
        int nodosXml = elementos(doc, "nodo").size();
        long registros = nodos.contarRegistros();
        System.out.println("BIN utilizado: " + carpetaDatos.resolve("nodos.dat").toAbsolutePath());
        System.out.println("Registros encontrados: " + registros);
        if (registros == 0 || registros != nodosXml) {
            throw new IOException("nodos.dat (" + registros + " registros) no coincide con los " + nodosXml
                    + " nodos de estado.xml. Ejecuta primero la inicialización de datos.");
        }
        JsonNode reglas = reglasRepository.leerReglas();
        umbralSincronizacion = reglas.path("sincronizacion").path("nivelMinimo").asInt();
        umbralCarga = reglas.path("carga").path("nivelMaximo").asDouble();
        umbralIntegridad = reglas.path("integridad").path("nivelMinimo").asInt();
    }

    // ------------------------------------------------------------------ operativa normal

    private void realizarOperacionNormal() throws IOException {
        Document documento = mundo.leerEstado();
        List<Element> sujetos = elementos(documento, "sujeto");
        List<Element> agentes = elementos(documento, "agente");
        List<Element> listaNodos = elementos(documento, "nodo");
        if (sujetos.isEmpty() || agentes.isEmpty() || listaNodos.isEmpty()) throw new IOException("estado.xml no contiene entidades suficientes.");
        String actorId, tipoOperacion, objetivoId;
        switch (random.nextInt(4)) {
            case 0 -> {
                Element e = elegir(sujetos);
                actorId = e.getAttribute("id"); tipoOperacion = "CONEXION"; objetivoId = e.getAttribute("zonaActual");
            }
            case 1 -> {
                // Un acceso normal siempre es a una zona para la que el sujeto tiene permiso.
                Element e = elegir(sujetos);
                actorId = e.getAttribute("id"); tipoOperacion = "ACCESO";
                List<String> permitidas = zonasPermitidas(documento, actorId);
                objetivoId = permitidas.isEmpty() ? e.getAttribute("zonaActual") : elegirTexto(permitidas);
            }
            case 2 -> {
                Element e = elegir(agentes);
                actorId = e.getAttribute("id"); tipoOperacion = "INSPECCION"; objetivoId = e.getAttribute("zonaAsignada");
            }
            default -> {
                Element e = elegir(listaNodos);
                actorId = e.getAttribute("id"); tipoOperacion = "COMPROBACION_INTEGRIDAD"; objetivoId = e.getAttribute("id");
            }
        }
        guardarEvento(actorId, tipoOperacion, objetivoId, "OK");
        Element nodoElegido = elegir(listaNodos);
        int idBinario = Integer.parseInt(nodoElegido.getAttribute("idBinario"));
        NodoRegistro anterior = nodos.leerPorId(idBinario);
        int sincronizacion = limitar(anterior.nivelSincronizacion() + random.nextInt(7) - 3, 85, 100);
        double carga = limitar(anterior.carga() + (random.nextDouble() * 10 - 5), 10, 85);
        NodoRegistro actualizado = new NodoRegistro(idBinario, sincronizacion, carga, 0, anterior.numeroOperaciones() + 1);
        nodos.modificar(actualizado);
        mundo.actualizarEstadoNodo(nodoElegido.getAttribute("id"), actualizado.estadoTexto());
        System.out.printf("%s | %s | %s -> %s | nodo %s: sync=%d%%, carga=%.1f%%%n",
                String.format("E%04d", siguienteEvento - 1), tipoOperacion, actorId, objetivoId, nodoElegido.getAttribute("id"), sincronizacion, carga);
    }

    // ------------------------------------------------------------------ incidentes

    /**
     * Persiste las consecuencias del incidente (TXT + BIN + XML) y después lanza la excepción crítica.
     * La excepción no crea las evidencias: solo indica que el sistema ha detectado la situación crítica.
     */
    private void inyectarIncidente(IncidenteMatrix incidente) throws IOException, EventoCriticoException {
        Document doc = mundo.leerEstado();
        List<Element> sujetos = elementos(doc, "sujeto");
        List<Element> agentes = elementos(doc, "agente");
        List<String> zonas = ids(elementos(doc, "zona"));
        List<Element> listaNodos = elementos(doc, "nodo");
        switch (incidente) {
            case PERDIDA_SINCRONIZACION -> {
                Element n = elegir(listaNodos);
                String id = n.getAttribute("id");
                escribirNodo(n, Math.max(0, umbralSincronizacion - 55), null, 1);
                guardarEvento(id, "COMPROBACION_SINCRONIZACION", id, "ALERTA");
            }
            case COMPORTAMIENTO_SUJETO -> {
                Element s = elegir(sujetos);
                String id = s.getAttribute("id");
                List<String> permitidas = zonasPermitidas(doc, id);
                String previa = s.getAttribute("zonaActual");
                // Tres cambios de zona seguidos; el último, a una zona sin permiso si existe.
                for (int i = 0; i < 3; i++) {
                    List<String> candidatas = new ArrayList<>(zonas);
                    candidatas.remove(previa);
                    if (i == 2) {
                        List<String> sinPermiso = new ArrayList<>(candidatas);
                        sinPermiso.removeAll(permitidas);
                        if (!sinPermiso.isEmpty()) candidatas = sinPermiso;
                    }
                    String destino = elegirTexto(candidatas);
                    mundo.actualizarZonaSujeto(id, destino);
                    guardarEvento(id, "CAMBIO_ZONA", destino, "OK");
                    previa = destino;
                }
                mundo.actualizarEstadoSujeto(id, "ANOMALO");
                escribirNodo(nodoDeZona(doc, previa), null, Math.min(100.0, umbralCarga + 1), 1);
            }
            case ACCESO_NO_AUTORIZADO -> {
                Element s = elegir(sujetos);
                String id = s.getAttribute("id");
                List<String> sinPermiso = new ArrayList<>(zonas);
                sinPermiso.removeAll(zonasPermitidas(doc, id));
                if (sinPermiso.isEmpty()) throw new IOException("El sujeto " + id + " tiene permiso en todas las zonas.");
                String destino = elegirTexto(sinPermiso);
                guardarEvento(id, "ACCESO", destino, "OK");
                escribirNodo(nodoDeZona(doc, destino), null, Math.min(100.0, umbralCarga + 5), 1);
            }
            case FALLO_NODO -> {
                Element n = elegir(listaNodos);
                escribirNodo(n, 0, 100.0, 2);
                guardarEvento(n.getAttribute("id"), "OPERACION_NODO", n.getAttribute("id"), "FALLO");
            }
            case AGENTE_FUERA_PROTOCOLO -> {
                Element a = elegir(agentes);
                String id = a.getAttribute("id");
                List<String> otras = new ArrayList<>(zonas);
                otras.remove(a.getAttribute("zonaAsignada"));
                String destino = elegirTexto(otras);
                mundo.actualizarEstadoAgente(id, "FUERA_PROTOCOLO");
                guardarEvento(id, "ACCION_AGENTE", destino, "OK");
                escribirNodo(nodoDeZona(doc, destino), null, Math.min(100.0, umbralCarga + 3), 1);
            }
            case ALTERACION_ZONA -> {
                String idZona = elegirTexto(zonas);
                Element nodo = nodoDeZona(doc, idZona);
                mundo.actualizarIntegridadZona(idZona, Math.max(0, umbralIntegridad - 45), "ALTERADA");
                escribirNodo(nodo, Math.max(0, umbralSincronizacion - 10), null, 1);
                guardarEvento(nodo.getAttribute("id"), "COMPROBACION_INTEGRIDAD", idZona, "ALERTA");
            }
        }
        EVIDENCIA.error("INCIDENTE_PERSISTIDO | idEjecucion={} | descripcion={}", idEjecucion, incidente.descripcion());
        LOGGER.error("Incidente '{}' persistido en los ficheros de datos", incidente.descripcion());
        System.out.println("\nEvidencia del incidente persistida: " + incidente.descripcion());
        throw new EventoCriticoException(incidente.descripcion());
    }

    /** Modifica el registro binario del nodo (acceso directo) y refleja el nuevo estado en el XML. */
    private void escribirNodo(Element nodoXml, Integer sincronizacion, Double carga, int codigoEstado) throws IOException {
        int idBinario = Integer.parseInt(nodoXml.getAttribute("idBinario"));
        NodoRegistro anterior = nodos.leerPorId(idBinario);
        NodoRegistro nuevo = new NodoRegistro(idBinario,
                sincronizacion != null ? sincronizacion : anterior.nivelSincronizacion(),
                carga != null ? carga : anterior.carga(),
                codigoEstado, anterior.numeroOperaciones() + 1);
        nodos.modificar(nuevo);
        mundo.actualizarEstadoNodo(nodoXml.getAttribute("id"), nuevo.estadoTexto());
    }

    private void guardarEvento(String actorId, String tipo, String objetivo, String resultado) throws IOException {
        Evento evento = new Evento(String.format("E%04d", siguienteEvento++), idEjecucion,
                LocalDateTime.now().withNano(0), actorId, tipo, objetivo, resultado);
        eventos.guardar(evento);
        EVIDENCIA.info("EVENTO_PERSISTIDO | id={} | fechaHora={} | actor={} | tipo={} | objetivo={} | resultado={}",
                evento.idEvento(), evento.fechaHora(), evento.actorId(), evento.tipoOperacion(), evento.objetivoId(), evento.resultado());
    }

    // ------------------------------------------------------------------ utilidades

    private void mostrarProgreso(long inicio, int operaciones) {
        long transcurrido = System.nanoTime() - inicio;
        System.out.printf("Progreso: %d/%d s | operaciones: %d\r", Math.min(duracionSegundos, transcurrido / 1_000_000_000L), duracionSegundos, operaciones);
    }

    private Element nodoDeZona(Document doc, String zonaId) throws IOException {
        for (Element n : elementos(doc, "nodo")) if (zonaId.equals(n.getAttribute("zonaId"))) return n;
        throw new IOException("La zona " + zonaId + " no tiene ningún nodo asociado en estado.xml.");
    }

    private static List<String> zonasPermitidas(Document doc, String actorId) {
        List<String> permitidas = new ArrayList<>();
        for (Element p : elementos(doc, "permiso")) {
            if (actorId.equals(p.getAttribute("actorId")) && "true".equals(p.getAttribute("permitido"))) {
                permitidas.add(p.getAttribute("zonaId"));
            }
        }
        return permitidas;
    }

    private static List<String> ids(List<Element> lista) {
        List<String> resultado = new ArrayList<>();
        for (Element e : lista) resultado.add(e.getAttribute("id"));
        return resultado;
    }

    private Element elegir(List<Element> lista) { return lista.get(random.nextInt(lista.size())); }

    private String elegirTexto(List<String> lista) { return lista.get(random.nextInt(lista.size())); }

    private static List<Element> elementos(Document d, String etiqueta) {
        NodeList lista = d.getElementsByTagName(etiqueta);
        List<Element> resultado = new ArrayList<>();
        for (int i = 0; i < lista.getLength(); i++) resultado.add((Element) lista.item(i));
        return resultado;
    }

    private static int limitar(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static double limitar(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
