package es.matrix.forense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;
import es.matrix.modelo.ResultadoForense;
import es.matrix.persistencia.BinaryNodeRepository;
import es.matrix.persistencia.JsonRulesRepository;
import es.matrix.persistencia.TextEventRepository;
import es.matrix.persistencia.XmlWorldRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Analiza exclusivamente los ficheros persistidos por el simulador.
 *
 * <p>Se analiza la última ejecución registrada en el histórico (TXT) y se contrasta cada síntoma con
 * el estado persistido en BIN (acceso directo) y XML (estructura, permisos), aplicando los umbrales
 * de {@code reglas.json}. Un incidente solo se acepta si al menos dos fuentes distintas lo respaldan.
 * No se usan los logs de Log4j como fuente.
 */
public class AnalizadorForense {
    private static final Logger LOGGER = LogManager.getLogger(AnalizadorForense.class);
    private static final Logger EVIDENCIA = LogManager.getLogger("EVIDENCIA");

    static final String INDETERMINADO = "INDETERMINADO";

    private final Path datos;
    private final Path resultados;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public AnalizadorForense(Path datos, Path resultados) {
        this.datos = datos;
        this.resultados = resultados;
    }

    public ResultadoForense analizar() throws IOException {
        validarEntradas();
        Files.createDirectories(resultados);
        LOGGER.info("Inicio del análisis forense; datos={} resultados={}", datos.toAbsolutePath(), resultados.toAbsolutePath());

        List<Evento> historico = new TextEventRepository(datos.resolve("operaciones.txt")).leerTodos();
        if (historico.isEmpty()) throw new IOException("El histórico no contiene eventos para analizar.");
        Document xml = new XmlWorldRepository(datos.resolve("estado.xml")).leerEstado();
        JsonNode reglas = new JsonRulesRepository(datos.resolve("reglas.json")).leerReglas();
        BinaryNodeRepository binario = new BinaryNodeRepository(datos.resolve("nodos.dat"));

        String ultimaEjecucion = historico.get(historico.size() - 1).idEjecucion();
        List<Evento> ejecucion = historico.stream().filter(e -> e.idEjecucion().equals(ultimaEjecucion)).toList();
        LOGGER.info("{} registros procesados; {} pertenecen a la ejecución analizada {}", historico.size(), ejecucion.size(), ultimaEjecucion);

        Contexto ctx = new Contexto(xml, binario,
                reglas.path("sincronizacion").path("nivelMinimo").asInt(),
                reglas.path("carga").path("nivelMaximo").asDouble(),
                reglas.path("integridad").path("nivelMinimo").asInt());

        Hallazgo hallazgo = detectar(ctx, ejecucion, ultimaEjecucion);
        LOGGER.info("Resultado: {} (certeza {})", hallazgo.nombre, hallazgo.certeza());
        EVIDENCIA.info("ANALISIS | ejecucion={} | resultado={} | certeza={} | fuentes={}",
                ultimaEjecucion, hallazgo.nombre, hallazgo.certeza(), hallazgo.fuentes());

        escribirInforme(historico.size(), ejecucion.size(), ultimaEjecucion, hallazgo);
        escribirBinarioAnomalias(hallazgo.idsBinarios);
        escribirJsonAfectados(xml, ultimaEjecucion, hallazgo);
        LOGGER.info("Salidas generadas en {}", resultados.toAbsolutePath());
        return new ResultadoForense(hallazgo.nombre, hallazgo.conclusion, hallazgo.certeza());
    }

    private void validarEntradas() throws IOException {
        for (String nombre : List.of("operaciones.txt", "nodos.dat", "estado.xml", "reglas.json")) {
            Path p = datos.resolve(nombre);
            if (Files.notExists(p) || !Files.isRegularFile(p)) {
                throw new IOException("Falta el fichero de entrada: " + p.toAbsolutePath());
            }
        }
    }

    // ------------------------------------------------------------------ detección

    private Hallazgo detectar(Contexto c, List<Evento> run, String idEjecucion) throws IOException {
        List<Hallazgo> candidatos = new ArrayList<>();
        perdidaSincronizacion(c, run).ifPresent(candidatos::add);
        comportamientoSujeto(c, run).ifPresent(candidatos::add);
        accesoNoAutorizado(c, run).ifPresent(candidatos::add);
        falloNodo(c, run).ifPresent(candidatos::add);
        agenteFueraProtocolo(c, run).ifPresent(candidatos::add);
        alteracionZona(c, run).ifPresent(candidatos::add);

        Optional<Hallazgo> mejor = candidatos.stream()
                .max(Comparator.<Hallazgo>comparingInt(Hallazgo::fuentes).thenComparingInt(h -> h.orden));
        if (mejor.isPresent()) return mejor.get();

        Hallazgo h = new Hallazgo(INDETERMINADO);
        List<String> residuales = c.anomaliasResiduales();
        h.conclusion = "Los datos de la ejecución " + idEjecucion + " no permiten atribuir ninguno de los incidentes definidos"
                + " (se exigen al menos dos fuentes coincidentes)."
                + (residuales.isEmpty() ? "" : " Hay anomalías residuales en el estado persistido que no están respaldadas"
                + " por eventos de esta ejecución: " + String.join("; ", residuales) + ".");
        h.efectos.addAll(residuales);
        return h;
    }

    private Optional<Hallazgo> perdidaSincronizacion(Contexto c, List<Evento> run) throws IOException {
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("COMPROBACION_SINCRONIZACION") || !e.resultado().equals("ALERTA")) continue;
            Element nodo = c.porId("nodo", e.objetivoId());
            if (nodo == null) continue;
            NodoRegistro n = c.leerNodo(nodo);
            boolean bin = n.nivelSincronizacion() < c.syncMin;
            boolean xml = !"NORMAL".equals(nodo.getAttribute("estado"));
            if (!bin && !xml) continue;
            Hallazgo h = nuevo("Pérdida de sincronización", e, i);
            h.txt = true; h.bin = bin; h.xml = xml;
            h.efectos.add(fmt("El histórico registra una alerta de sincronización en el nodo %s", nodo.getAttribute("id")));
            if (bin) h.efectos.add(fmt("Sincronización del nodo %s al %d%% (mínimo exigido %d%%)", nodo.getAttribute("id"), n.nivelSincronizacion(), c.syncMin));
            if (xml) h.efectos.add(fmt("El nodo %s figura como %s en estado.xml", nodo.getAttribute("id"), nodo.getAttribute("estado")));
            h.nodos.add(nodo.getAttribute("id"));
            h.zonas.add(nodo.getAttribute("zonaId"));
            h.idsBinarios.add(n.idNumerico());
            h.conclusion = fmt("El nodo %s ha perdido la sincronización con el sistema; la zona %s trabaja con datos desfasados.",
                    nodo.getAttribute("id"), nodo.getAttribute("zonaId"));
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private Optional<Hallazgo> comportamientoSujeto(Contexto c, List<Evento> run) throws IOException {
        Map<String, Integer> cambios = new HashMap<>();
        for (Evento e : run) if (e.tipoOperacion().equals("CAMBIO_ZONA")) cambios.merge(e.actorId(), 1, Integer::sum);
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("CAMBIO_ZONA") || cambios.getOrDefault(e.actorId(), 0) < 3) continue;
            Element sujeto = c.porId("sujeto", e.actorId());
            if (sujeto == null || !e.objetivoId().equals(sujeto.getAttribute("zonaActual"))) continue;
            Hallazgo h = nuevo("Comportamiento anómalo de un sujeto", e, i);
            h.eventos.clear();
            List<String> recorrido = new ArrayList<>();
            for (Evento cz : run) {
                if (cz.tipoOperacion().equals("CAMBIO_ZONA") && cz.actorId().equals(e.actorId())) {
                    h.eventos.add(cz);
                    recorrido.add(cz.objetivoId());
                    h.zonas.add(cz.objetivoId());
                }
            }
            h.txt = true; h.xml = true;
            h.sujetos.add(e.actorId());
            h.efectos.add(fmt("El sujeto %s cambió %d veces de zona en la misma ejecución (%s)", e.actorId(), recorrido.size(), String.join(" -> ", recorrido)));
            h.efectos.add(fmt("estado.xml sitúa a %s en %s con estado %s", e.actorId(), sujeto.getAttribute("zonaActual"), sujeto.getAttribute("estado")));
            if (!c.tienePermiso(e.actorId(), e.objetivoId())) {
                h.efectos.add(fmt("El sujeto %s no tiene permiso para la zona %s", e.actorId(), e.objetivoId()));
            }
            Element nodoZona = c.nodoDeZona(e.objetivoId());
            if (nodoZona != null) {
                NodoRegistro n = c.leerNodo(nodoZona);
                if (n.carga() > c.cargaMax) {
                    h.bin = true;
                    h.nodos.add(nodoZona.getAttribute("id"));
                    h.idsBinarios.add(n.idNumerico());
                    h.efectos.add(fmt(Locale.ROOT, "Carga del nodo %s al %.1f%% (máximo %.1f%%)", nodoZona.getAttribute("id"), n.carga(), c.cargaMax));
                }
            }
            h.conclusion = fmt("El sujeto %s muestra un comportamiento anómalo: saltos repetidos de zona hasta %s.", e.actorId(), e.objetivoId());
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private Optional<Hallazgo> accesoNoAutorizado(Contexto c, List<Evento> run) throws IOException {
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("ACCESO")) continue;
            if (c.porId("sujeto", e.actorId()) == null || c.porId("zona", e.objetivoId()) == null) continue;
            if (c.tienePermiso(e.actorId(), e.objetivoId())) continue;
            Hallazgo h = nuevo("Acceso no autorizado", e, i);
            h.txt = true; h.xml = true;
            h.sujetos.add(e.actorId());
            h.zonas.add(e.objetivoId());
            h.efectos.add(fmt("El sujeto %s accedió a la zona %s", e.actorId(), e.objetivoId()));
            h.efectos.add(fmt("estado.xml no contiene ningún permiso de %s para la zona %s", e.actorId(), e.objetivoId()));
            Element nodoZona = c.nodoDeZona(e.objetivoId());
            if (nodoZona != null) {
                NodoRegistro n = c.leerNodo(nodoZona);
                if (n.carga() > c.cargaMax) {
                    h.bin = true;
                    h.nodos.add(nodoZona.getAttribute("id"));
                    h.idsBinarios.add(n.idNumerico());
                    h.efectos.add(fmt(Locale.ROOT, "Carga anómala del nodo %s al %.1f%% (máximo %.1f%%)", nodoZona.getAttribute("id"), n.carga(), c.cargaMax));
                }
            }
            h.conclusion = fmt("Un sujeto sin permiso (%s) ha entrado en la zona %s; la seguridad de la zona está comprometida.", e.actorId(), e.objetivoId());
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private Optional<Hallazgo> falloNodo(Contexto c, List<Evento> run) throws IOException {
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("OPERACION_NODO") || !e.resultado().equals("FALLO")) continue;
            Element nodo = c.porId("nodo", e.actorId());
            if (nodo == null) continue;
            NodoRegistro n = c.leerNodo(nodo);
            boolean bin = n.codigoEstado() == 2;
            boolean xml = "FALLO".equals(nodo.getAttribute("estado"));
            if (!bin && !xml) continue;
            Hallazgo h = nuevo("Fallo de nodo", e, i);
            h.txt = true; h.bin = bin; h.xml = xml;
            h.efectos.add(fmt("El histórico registra un fallo en la operación del nodo %s", nodo.getAttribute("id")));
            if (bin) h.efectos.add(fmt(Locale.ROOT, "Registro binario del nodo %s con código de estado 2 (FALLO), sincronización %d%% y carga %.1f%%",
                    nodo.getAttribute("id"), n.nivelSincronizacion(), n.carga()));
            if (xml) h.efectos.add(fmt("El nodo %s figura como FALLO en estado.xml", nodo.getAttribute("id")));
            h.nodos.add(nodo.getAttribute("id"));
            h.zonas.add(nodo.getAttribute("zonaId"));
            h.idsBinarios.add(n.idNumerico());
            h.conclusion = fmt("El nodo %s ha dejado de funcionar y la zona %s queda sin servicio.", nodo.getAttribute("id"), nodo.getAttribute("zonaId"));
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private Optional<Hallazgo> agenteFueraProtocolo(Contexto c, List<Evento> run) throws IOException {
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("ACCION_AGENTE")) continue;
            Element agente = c.porId("agente", e.actorId());
            if (agente == null || e.objetivoId().equals(agente.getAttribute("zonaAsignada"))) continue;
            Hallazgo h = nuevo("Agente fuera de protocolo", e, i);
            h.txt = true; h.xml = true;
            h.agentes.add(e.actorId());
            h.zonas.add(e.objetivoId());
            h.zonas.add(agente.getAttribute("zonaAsignada"));
            h.efectos.add(fmt("El agente %s actuó en la zona %s, pero su zona asignada en estado.xml es %s",
                    e.actorId(), e.objetivoId(), agente.getAttribute("zonaAsignada")));
            h.efectos.add(fmt("El agente %s figura como %s", e.actorId(), agente.getAttribute("estado")));
            Element nodoZona = c.nodoDeZona(e.objetivoId());
            if (nodoZona != null) {
                NodoRegistro n = c.leerNodo(nodoZona);
                if (n.carga() > c.cargaMax) {
                    h.bin = true;
                    h.nodos.add(nodoZona.getAttribute("id"));
                    h.idsBinarios.add(n.idNumerico());
                    h.efectos.add(fmt(Locale.ROOT, "Carga anómala del nodo %s al %.1f%% (máximo %.1f%%)", nodoZona.getAttribute("id"), n.carga(), c.cargaMax));
                }
            }
            h.conclusion = fmt("El agente %s ha actuado fuera del comportamiento previsto, en una zona que no tiene asignada (%s).", e.actorId(), e.objetivoId());
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private Optional<Hallazgo> alteracionZona(Contexto c, List<Evento> run) throws IOException {
        for (int i = run.size() - 1; i >= 0; i--) {
            Evento e = run.get(i);
            if (!e.tipoOperacion().equals("COMPROBACION_INTEGRIDAD") || !e.resultado().equals("ALERTA")) continue;
            Element zona = c.porId("zona", e.objetivoId());
            if (zona == null) continue;
            int integridad = Integer.parseInt(zona.getAttribute("integridad"));
            if (integridad >= c.integridadMin) continue;
            Hallazgo h = nuevo("Alteración de zona", e, i);
            h.txt = true; h.xml = true;
            h.zonas.add(zona.getAttribute("id"));
            h.efectos.add(fmt("La comprobación de integridad de la zona %s terminó en alerta", zona.getAttribute("id")));
            h.efectos.add(fmt("Integridad de la zona %s al %d%% (mínimo %d%%), estado %s", zona.getAttribute("id"), integridad, c.integridadMin, zona.getAttribute("estado")));
            Element nodoZona = c.nodoDeZona(zona.getAttribute("id"));
            if (nodoZona != null) {
                NodoRegistro n = c.leerNodo(nodoZona);
                if (n.nivelSincronizacion() < c.syncMin) {
                    h.bin = true;
                    h.nodos.add(nodoZona.getAttribute("id"));
                    h.idsBinarios.add(n.idNumerico());
                    h.efectos.add(fmt("Sincronización del nodo %s de la zona al %d%% (mínimo %d%%)", nodoZona.getAttribute("id"), n.nivelSincronizacion(), c.syncMin));
                }
            }
            h.conclusion = fmt("La zona %s ha sido alterada: su integridad ha caído por debajo del mínimo y la simulación en ella no es fiable.", zona.getAttribute("id"));
            return Optional.of(h);
        }
        return Optional.empty();
    }

    private static Hallazgo nuevo(String nombre, Evento evidencia, int orden) {
        Hallazgo h = new Hallazgo(nombre);
        h.eventos.add(evidencia);
        h.orden = orden;
        return h;
    }

    private static String fmt(String plantilla, Object... args) { return String.format(Locale.ROOT, plantilla, args); }

    private static String fmt(Locale l, String plantilla, Object... args) { return String.format(l, plantilla, args); }

    // ------------------------------------------------------------------ salidas

    private void escribirInforme(int totalEventos, int eventosEjecucion, String idEjecucion, Hallazgo h) throws IOException {
        Path p = resultados.resolve("informe.txt");
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("INFORME FORENSE MATRIX\n======================\n");
            w.write("Fecha del análisis: " + LocalDateTime.now().withNano(0) + "\n");
            w.write("Ejecución analizada: " + idEjecucion + "\n");
            w.write("Eventos en el histórico: " + totalEventos + " (de la ejecución analizada: " + eventosEjecucion + ")\n");
            w.write("Fuentes que respaldan el hallazgo: TXT=" + (h.txt ? "sí" : "no") + ", BIN=" + (h.bin ? "sí" : "no") + ", XML=" + (h.xml ? "sí" : "no") + "\n\n");
            w.write("Incidente identificado: " + h.nombre + "\n");
            w.write("Nivel de certeza: " + h.certeza() + "\n\n");
            w.write("Efectos detectados:\n");
            if (h.efectos.isEmpty()) w.write("- (ninguno)\n");
            for (String efecto : h.efectos) w.write("- " + efecto + "\n");
            w.write("\nConclusión:\n" + h.conclusion + "\n");
            if (!h.eventos.isEmpty()) {
                w.write("\nEvidencias en el histórico:\n");
                for (Evento e : h.eventos) w.write("- " + e.aLinea() + "\n");
            }
        }
    }

    /** Copia al BIN de salida únicamente los registros anómalos usados en el análisis (acceso directo con seek). */
    private void escribirBinarioAnomalias(Set<Integer> ids) throws IOException {
        Path entrada = datos.resolve("nodos.dat");
        Path salida = resultados.resolve("anomalias.dat");
        try (RandomAccessFile in = new RandomAccessFile(entrada.toFile(), "r");
             RandomAccessFile out = new RandomAccessFile(salida.toFile(), "rw")) {
            if (in.length() % NodoRegistro.TAMANO_REGISTRO != 0) throw new IOException("nodos.dat está incompleto o corrupto.");
            out.setLength(0);
            for (int id : new TreeSet<>(ids)) {
                long posicion = (long) (id - 1) * NodoRegistro.TAMANO_REGISTRO;
                if (posicion < 0 || posicion + NodoRegistro.TAMANO_REGISTRO > in.length()) continue;
                in.seek(posicion);
                byte[] registro = new byte[NodoRegistro.TAMANO_REGISTRO];
                in.readFully(registro);
                out.write(registro);
            }
        }
    }

    /** Escribe solo los elementos del XML que intervienen en el análisis, con su estado y relaciones. */
    private void escribirJsonAfectados(Document xml, String idEjecucion, Hallazgo h) throws IOException {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("incidente", h.nombre);
        salida.put("certeza", h.certeza());
        salida.put("idEjecucion", idEjecucion);
        Map<String, List<Map<String, String>>> entidades = new LinkedHashMap<>();
        añadir(entidades, xml, "sujeto", h.sujetos, "id");
        añadir(entidades, xml, "agente", h.agentes, "id");
        añadir(entidades, xml, "zona", h.zonas, "id");
        añadir(entidades, xml, "nodo", h.nodos, "id");
        añadir(entidades, xml, "permiso", h.sujetos, "actorId");
        salida.put("entidadesAfectadas", entidades);
        json.writeValue(resultados.resolve("entidades_afectadas.json").toFile(), salida);
    }

    private static void añadir(Map<String, List<Map<String, String>>> destino, Document xml, String etiqueta, Set<String> ids, String atributoClave) {
        if (ids.isEmpty()) return;
        NodeList lista = xml.getElementsByTagName(etiqueta);
        List<Map<String, String>> encontrados = new ArrayList<>();
        for (int i = 0; i < lista.getLength(); i++) {
            Element e = (Element) lista.item(i);
            if (!ids.contains(e.getAttribute(atributoClave))) continue;
            Map<String, String> atributos = new LinkedHashMap<>();
            for (int a = 0; a < e.getAttributes().getLength(); a++) {
                atributos.put(e.getAttributes().item(a).getNodeName(), e.getAttributes().item(a).getNodeValue());
            }
            encontrados.add(atributos);
        }
        if (!encontrados.isEmpty()) destino.put(etiqueta, encontrados);
    }

    // ------------------------------------------------------------------ tipos auxiliares

    /** Hallazgo candidato: qué incidente es, qué fuentes lo respaldan y qué elementos intervienen. */
    private static final class Hallazgo {
        final String nombre;
        String conclusion = "";
        final List<String> efectos = new ArrayList<>();
        final List<Evento> eventos = new ArrayList<>();
        final Set<Integer> idsBinarios = new LinkedHashSet<>();
        final Set<String> sujetos = new LinkedHashSet<>();
        final Set<String> agentes = new LinkedHashSet<>();
        final Set<String> zonas = new LinkedHashSet<>();
        final Set<String> nodos = new LinkedHashSet<>();
        boolean txt, bin, xml;
        int orden;

        Hallazgo(String nombre) { this.nombre = nombre; }

        int fuentes() { return (txt ? 1 : 0) + (bin ? 1 : 0) + (xml ? 1 : 0); }

        String certeza() {
            return switch (fuentes()) {
                case 3 -> "ALTA";
                case 2 -> "MEDIA";
                default -> INDETERMINADO;
            };
        }
    }

    /** Estado persistido ya leído (XML, BIN) y umbrales de reglas.json. */
    private static final class Contexto {
        final Document xml;
        final BinaryNodeRepository binario;
        final int syncMin;
        final double cargaMax;
        final int integridadMin;

        Contexto(Document xml, BinaryNodeRepository binario, int syncMin, double cargaMax, int integridadMin) {
            this.xml = xml; this.binario = binario;
            this.syncMin = syncMin; this.cargaMax = cargaMax; this.integridadMin = integridadMin;
        }

        Element porId(String etiqueta, String id) {
            NodeList lista = xml.getElementsByTagName(etiqueta);
            for (int i = 0; i < lista.getLength(); i++) {
                Element e = (Element) lista.item(i);
                if (e.getAttribute("id").equals(id)) return e;
            }
            return null;
        }

        Element nodoDeZona(String zonaId) {
            NodeList lista = xml.getElementsByTagName("nodo");
            for (int i = 0; i < lista.getLength(); i++) {
                Element e = (Element) lista.item(i);
                if (e.getAttribute("zonaId").equals(zonaId)) return e;
            }
            return null;
        }

        boolean tienePermiso(String actorId, String zonaId) {
            NodeList lista = xml.getElementsByTagName("permiso");
            for (int i = 0; i < lista.getLength(); i++) {
                Element p = (Element) lista.item(i);
                if (p.getAttribute("actorId").equals(actorId) && p.getAttribute("zonaId").equals(zonaId)
                        && "true".equals(p.getAttribute("permitido"))) return true;
            }
            return false;
        }

        /** Lectura directa del registro binario del nodo; avisa si hay valores fuera de rango. */
        NodoRegistro leerNodo(Element nodoXml) throws IOException {
            NodoRegistro n = binario.leerPorId(Integer.parseInt(nodoXml.getAttribute("idBinario")));
            String id = nodoXml.getAttribute("id");
            if (n.nivelSincronizacion() < syncMin) {
                LOGGER.warn("Medición fuera de rango en {}: sincronización={}% (mínimo {}%)", id, n.nivelSincronizacion(), syncMin);
            }
            if (n.carga() > cargaMax) {
                LOGGER.warn("Medición fuera de rango en {}: carga={}% (máximo {}%)", id, String.format(Locale.ROOT, "%.1f", n.carga()), cargaMax);
            }
            if (n.codigoEstado() == 2) LOGGER.warn("El registro binario de {} indica FALLO", id);
            return n;
        }

        /** Valores anómalos que siguen en el estado persistido (de cualquier ejecución). */
        List<String> anomaliasResiduales() throws IOException {
            List<String> r = new ArrayList<>();
            NodeList nodos = xml.getElementsByTagName("nodo");
            for (int i = 0; i < nodos.getLength(); i++) {
                Element e = (Element) nodos.item(i);
                NodoRegistro n = leerNodo(e);
                if (n.nivelSincronizacion() < syncMin || n.carga() > cargaMax || n.codigoEstado() != 0) {
                    r.add("nodo " + e.getAttribute("id") + " en estado " + n.estadoTexto());
                }
            }
            NodeList zonas = xml.getElementsByTagName("zona");
            for (int i = 0; i < zonas.getLength(); i++) {
                Element e = (Element) zonas.item(i);
                if (Integer.parseInt(e.getAttribute("integridad")) < integridadMin) r.add("zona " + e.getAttribute("id") + " con integridad " + e.getAttribute("integridad") + "%");
            }
            NodeList agentes = xml.getElementsByTagName("agente");
            for (int i = 0; i < agentes.getLength(); i++) {
                Element e = (Element) agentes.item(i);
                if (!"ACTIVO".equals(e.getAttribute("estado"))) r.add("agente " + e.getAttribute("id") + " en estado " + e.getAttribute("estado"));
            }
            NodeList sujetos = xml.getElementsByTagName("sujeto");
            for (int i = 0; i < sujetos.getLength(); i++) {
                Element e = (Element) sujetos.item(i);
                if (!"ACTIVO".equals(e.getAttribute("estado"))) r.add("sujeto " + e.getAttribute("id") + " en estado " + e.getAttribute("estado"));
            }
            return r;
        }
    }
}
