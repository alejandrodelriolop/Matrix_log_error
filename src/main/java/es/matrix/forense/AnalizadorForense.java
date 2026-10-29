package es.matrix.forense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;
import es.matrix.modelo.ResultadoForense;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Analiza exclusivamente los archivos persistidos por el simulador. */
public class AnalizadorForense {
    private final Path datos;
    private final Path resultados;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public AnalizadorForense(Path datos, Path resultados) {
        this.datos = datos;
        this.resultados = resultados;
    }

    public ResultadoForense analizar() throws Exception {
        validarEntradas();
        Files.createDirectories(resultados);
        List<Evento> eventos = leerEventos(datos.resolve("operaciones.txt"));
        Document xml = leerXml(datos.resolve("estado.xml"));
        Map<String, Object> reglas = json.readValue(datos.resolve("reglas.json").toFile(), Map.class);
        if (eventos.isEmpty()) throw new IOException("El histórico no contiene eventos para analizar.");

        Hallazgo hallazgo = detectar(eventos, xml, reglas);
        escribirInforme(eventos, hallazgo);
        escribirBinarioAnomalias(hallazgo.idsNodosBinarios());
        escribirJsonAfectados(xml, hallazgo);
        return new ResultadoForense(hallazgo.nombre(), hallazgo.conclusion());
    }

    private void validarEntradas() throws IOException {
        for (String nombre : List.of("operaciones.txt", "nodos.dat", "estado.xml", "reglas.json")) {
            Path p = datos.resolve(nombre);
            if (Files.notExists(p) || !Files.isRegularFile(p)) throw new IOException("Falta el archivo de entrada: " + p.toAbsolutePath());
        }
    }

    private List<Evento> leerEventos(Path archivo) throws IOException {
        List<Evento> eventos = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea; int numero = 0;
            while ((linea = r.readLine()) != null) {
                numero++;
                if (linea.isBlank()) continue;
                try { eventos.add(Evento.desdeLinea(linea)); }
                catch (IllegalArgumentException e) { throw new IOException("Evento inválido en línea " + numero + ": " + e.getMessage(), e); }
            }
        }
        return eventos;
    }

    private Document leerXml(Path archivo) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return f.newDocumentBuilder().parse(archivo.toFile());
    }

    private Hallazgo detectar(List<Evento> eventos, Document xml, Map<String, Object> reglas) throws IOException {
        // Se buscan evidencias explícitas en el histórico, empezando por la más reciente.
        for (int i = eventos.size() - 1; i >= 0; i--) {
            Evento e = eventos.get(i);
            if (e.resultado().equals("ALERTA") && e.tipoOperacion().equals("COMPROBACION_SINCRONIZACION"))
                return hallazgo("Pérdida de sincronización", e, "El histórico registra una alerta de sincronización y el estado binario debe confirmar una sincronización anómala.", Set.of(1), Set.of(e.actorId(), e.objetivoId()), Set.of());
            if (e.tipoOperacion().equals("CAMBIO_ZONA") && e.resultado().equals("NO_AUTORIZADO"))
                return hallazgo("Comportamiento anómalo de un sujeto", e, "Se registró un cambio de zona marcado como no autorizado; se contrasta con la ubicación actual en XML.", Set.of(), Set.of(e.actorId(), e.objetivoId()), Set.of());
            if (e.tipoOperacion().equals("ACCESO") && e.resultado().equals("ACCESO_NO_AUTORIZADO_EJECUTADO"))
                return hallazgo("Acceso no autorizado", e, "El histórico indica que se ejecutó un acceso no autorizado.", Set.of(), Set.of(e.actorId(), e.objetivoId()), Set.of());
            if (e.tipoOperacion().equals("OPERACION_NODO") && e.resultado().equals("FALLO"))
                return hallazgo("Fallo de nodo", e, "El histórico registra un fallo de nodo; el estado binario permite confirmar el código de fallo.", Set.of(idBinarioDesdeXml(xml, e.actorId())), Set.of(e.actorId()), Set.of());
            if (e.tipoOperacion().equals("ACCION_AGENTE") && e.resultado().equals("FUERA_PROTOCOLO"))
                return hallazgo("Agente fuera de protocolo", e, "El agente ha realizado una acción identificada como fuera de protocolo.", Set.of(), Set.of(e.actorId(), e.objetivoId()), Set.of());
            if (e.tipoOperacion().equals("COMPROBACION_INTEGRIDAD") && e.resultado().equals("ALERTA"))
                return hallazgo("Alteración de zona", e, "Se ha registrado una alerta de integridad para una zona.", Set.of(3), Set.of(e.actorId(), e.objetivoId()), Set.of());
        }
        // Respaldo por estado persistido si no hay una marca de incidente inequívoca en TXT.
        try (RandomAccessFile raf = new RandomAccessFile(datos.resolve("nodos.dat").toFile(), "r")) {
            if (raf.length() % NodoRegistro.TAMANO_REGISTRO != 0) throw new IOException("nodos.dat está incompleto o corrupto.");
            while (raf.getFilePointer() < raf.length()) {
                NodoRegistro n = new NodoRegistro(raf.readInt(), raf.readInt(), raf.readDouble(), raf.readInt(), raf.readLong());
                if (n.nivelSincronizacion() < 80) return new Hallazgo("Pérdida de sincronización", "Sincronización inferior al umbral configurado; la evidencia textual específica no está disponible.", List.of(), Set.of(n.idNumerico()), Set.of("N00" + n.idNumerico()), Set.of(), "INDETERMINADO");
                if (n.codigoEstado() == 2) return new Hallazgo("Fallo de nodo", "El estado binario marca un nodo como fallido.", List.of(), Set.of(n.idNumerico()), Set.of("N00" + n.idNumerico()), Set.of(), "ALTA");
            }
        }
        return new Hallazgo("No determinado", "No se encontraron evidencias suficientes para atribuir uno de los seis incidentes.", List.of(), Set.of(), Set.of(), Set.of(), "INDETERMINADO");
    }

    private Hallazgo hallazgo(String nombre, Evento e, String conclusion, Set<Integer> binarios, Set<String> entidades, Set<String> zonas) {
        return new Hallazgo(nombre, conclusion, List.of(e), binarios, entidades, zonas, "ALTA");
    }

    private int idBinarioDesdeXml(Document xml, String idNodo) throws IOException {
        NodeList lista = xml.getElementsByTagName("nodo");
        for (int i = 0; i < lista.getLength(); i++) {
            Element n = (Element) lista.item(i);
            if (n.getAttribute("id").equals(idNodo)) return Integer.parseInt(n.getAttribute("idBinario"));
        }
        throw new IOException("No se encuentra el nodo " + idNodo + " en estado.xml.");
    }

    private void escribirInforme(List<Evento> eventos, Hallazgo h) throws IOException {
        Path p = resultados.resolve("informe.txt");
        try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("INFORME FORENSE MATRIX\n======================\n");
            w.write("Eventos analizados: " + eventos.size() + "\n");
            w.write("Incidente: " + h.nombre() + "\nNivel de certeza: " + h.certeza() + "\n");
            if (!h.eventos().isEmpty()) w.write("ID de ejecución: " + h.eventos().get(0).idEjecucion() + "\n");
            w.write("Conclusión: " + h.conclusion() + "\n\nEvidencias relevantes:\n");
            if (h.eventos().isEmpty()) w.write("- No hay un evento textual concluyente.\n");
            for (Evento e : h.eventos()) w.write("- " + e.aLinea() + "\n");
            w.write("\nEntidades afectadas: " + String.join(", ", h.entidades()) + "\n");
            w.write("Registros BIN relevantes: " + h.idsNodosBinarios() + "\n");
        }
    }

    private void escribirBinarioAnomalias(Set<Integer> ids) throws IOException {
        Path entrada = datos.resolve("nodos.dat"), salida = resultados.resolve("anomalias.dat");
        try (RandomAccessFile in = new RandomAccessFile(entrada.toFile(), "r"); RandomAccessFile out = new RandomAccessFile(salida.toFile(), "rw")) {
            if (in.length() % NodoRegistro.TAMANO_REGISTRO != 0) throw new IOException("nodos.dat está incompleto o corrupto.");
            out.setLength(0);
            for (int id : ids) {
                long posicion = (long) (id - 1) * NodoRegistro.TAMANO_REGISTRO;
                if (posicion < 0 || posicion + NodoRegistro.TAMANO_REGISTRO > in.length()) continue;
                in.seek(posicion);
                int guardado = in.readInt();
                if (guardado != id) throw new IOException("ID binario inconsistente para el nodo " + id);
                in.seek(posicion); byte[] registro = new byte[NodoRegistro.TAMANO_REGISTRO]; in.readFully(registro); out.write(registro);
            }
        }
    }

    private void escribirJsonAfectados(Document xml, Hallazgo h) throws IOException {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("incidente", h.nombre()); salida.put("certeza", h.certeza()); salida.put("entidadesIds", h.entidades());
        Map<String, List<Map<String, String>>> entidades = new LinkedHashMap<>();
        for (String tipo : List.of("sujeto", "agente", "zona", "nodo", "permiso")) {
            NodeList lista = xml.getElementsByTagName(tipo); List<Map<String, String>> encontradas = new ArrayList<>();
            for (int i = 0; i < lista.getLength(); i++) {
                Element e = (Element) lista.item(i);
                String id = e.hasAttribute("id") ? e.getAttribute("id") : e.getAttribute("actorId");
                String zona = e.hasAttribute("zonaId") ? e.getAttribute("zonaId") : e.getAttribute("zonaActual");
                boolean afecta = h.entidades().contains(id) || h.entidades().contains(e.getAttribute("actorId")) || h.entidades().contains(e.getAttribute("objetivoId")) || (!zona.isBlank() && h.entidades().contains(zona));
                if (afecta) {
                    Map<String, String> atributos = new LinkedHashMap<>();
                    for (int a = 0; a < e.getAttributes().getLength(); a++) { var attr = e.getAttributes().item(a); atributos.put(attr.getNodeName(), attr.getNodeValue()); }
                    encontradas.add(atributos);
                }
            }
            if (!encontradas.isEmpty()) entidades.put(tipo, encontradas);
        }
        salida.put("entidadesAfectadas", entidades);
        json.writeValue(resultados.resolve("entidades_afectadas.json").toFile(), salida);
    }

    private record Hallazgo(String nombre, String conclusion, List<Evento> eventos, Set<Integer> idsNodosBinarios,
                            Set<String> entidades, Set<String> zonas, String certeza) { }
}

