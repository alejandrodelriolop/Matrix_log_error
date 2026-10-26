package es.matrix.datos;

import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;
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

/** Coordina el ciclo de simulación y la persistencia de operaciones e incidentes. */
public class SimuladorMatrix {
    private static final Logger LOGGER = LogManager.getLogger(SimuladorMatrix.class);
    private static final Logger EVIDENCIA = LogManager.getLogger("EVIDENCIA");
    private final Path carpetaDatos;
    private final int duracionSegundos;
    private final boolean modoPruebas;
    private final IncidenteMatrix incidenteElegido;
    private final Random random = new Random();
    private TextEventRepository eventos;
    private BinaryNodeRepository nodos;
    private XmlWorldRepository mundo;
    private JsonRulesRepository reglasRepository;
    private int siguienteEvento;

    public SimuladorMatrix(Path carpetaDatos, int duracionSegundos, boolean modoPruebas, IncidenteMatrix incidenteElegido) {
        if (carpetaDatos == null) throw new IllegalArgumentException("La carpeta de datos es obligatoria.");
        if (duracionSegundos <= 0) throw new IllegalArgumentException("La duración debe ser mayor que cero.");
        if (modoPruebas && incidenteElegido == null) throw new IllegalArgumentException("En modo pruebas debes seleccionar un incidente.");
        this.carpetaDatos = carpetaDatos;
        this.duracionSegundos = duracionSegundos;
        this.modoPruebas = modoPruebas;
        this.incidenteElegido = incidenteElegido;
    }

    public void ejecutar() throws IOException, InterruptedException, EventoCriticoException {
        prepararRepositorios();
        validarDatosIniciales();
        LOGGER.info("Datos iniciales validados; modo={}, duración={} segundos", modoPruebas ? "PRUEBAS" : "NORMAL", duracionSegundos);
        EVIDENCIA.info("DATOS_VALIDADOS | modo={} | duracionSegundos={}", modoPruebas ? "PRUEBAS" : "NORMAL", duracionSegundos);
        siguienteEvento = eventos.leerTodos().size() + 1;
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
                inyectarIncidente(incidenteElegido);
                incidenteGenerado = true;
            } else if (!modoPruebas && random.nextInt(100) < 3) {
                IncidenteMatrix aleatorio = IncidenteMatrix.values()[random.nextInt(IncidenteMatrix.values().length)];
                inyectarIncidente(aleatorio);
            }
            mostrarProgreso(inicio, duracionNanos, operaciones);
            Thread.sleep(700);
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
        if (nodos.contarRegistros() != 4) throw new IOException("nodos.dat debe contener exactamente los cuatro nodos. Ejecuta primero InicializarDatosMain.");
        mundo.leerEstado();
        reglasRepository.leerReglas();
    }

    private void realizarOperacionNormal() throws IOException {
        Document documento = mundo.leerEstado();
        List<Element> sujetos = elementos(documento, "sujeto");
        List<Element> agentes = elementos(documento, "agente");
        List<Element> listaNodos = elementos(documento, "nodo");
        if (sujetos.isEmpty() || agentes.isEmpty() || listaNodos.isEmpty()) throw new IOException("estado.xml no contiene entidades suficientes.");
        int tipo = random.nextInt(4);
        String actorId, tipoOperacion, objetivoId;
        switch (tipo) {
            case 0 -> { Element e = sujetos.get(random.nextInt(sujetos.size())); actorId=e.getAttribute("id"); tipoOperacion="CONEXION"; objetivoId=e.getAttribute("zonaActual"); }
            case 1 -> { Element e = sujetos.get(random.nextInt(sujetos.size())); actorId=e.getAttribute("id"); tipoOperacion="ACCESO"; objetivoId=e.getAttribute("zonaActual"); }
            case 2 -> { Element e = agentes.get(random.nextInt(agentes.size())); actorId=e.getAttribute("id"); tipoOperacion="INSPECCION"; objetivoId=e.getAttribute("zonaAsignada"); }
            default -> { Element e = listaNodos.get(random.nextInt(listaNodos.size())); actorId=e.getAttribute("id"); tipoOperacion="COMPROBACION_INTEGRIDAD"; objetivoId=e.getAttribute("id"); }
        }
        guardarEvento(actorId, tipoOperacion, objetivoId, "OK");
        Element nodoElegido = listaNodos.get(random.nextInt(listaNodos.size()));
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

    private void inyectarIncidente(IncidenteMatrix incidente) throws IOException, EventoCriticoException {
        Document doc = mundo.leerEstado();
        List<Element> sujetos = elementos(doc, "sujeto");
        List<Element> agentes = elementos(doc, "agente");
        List<Element> zonas = elementos(doc, "zona");
        List<Element> listaNodos = elementos(doc, "nodo");
        switch (incidente) {
            case PERDIDA_SINCRONIZACION -> {
                Element n = listaNodos.get(0); int id = Integer.parseInt(n.getAttribute("idBinario"));
                NodoRegistro old = nodos.leerPorId(id);
                nodos.modificar(new NodoRegistro(id, 25, old.carga(), 1, old.numeroOperaciones()+1));
                mundo.actualizarEstadoNodo(n.getAttribute("id"), "DEGRADADO");
                guardarEvento(n.getAttribute("id"), "COMPROBACION_SINCRONIZACION", n.getAttribute("id"), "ALERTA");
            }
            case COMPORTAMIENTO_SUJETO -> {
                Element s = sujetos.get(0); String zonaAnterior = s.getAttribute("zonaActual");
                String zonaNueva = zonaAnterior.equals("Z004") ? "Z002" : "Z004";
                mundo.actualizarZonaSujeto(s.getAttribute("id"), zonaNueva);
                guardarEvento(s.getAttribute("id"), "CAMBIO_ZONA", zonaNueva, "NO_AUTORIZADO");
            }
            case ACCESO_NO_AUTORIZADO -> {
                Element s = sujetos.get(0);
                String zonaDestino = s.getAttribute("zonaActual").equals("Z004") ? "Z002" : "Z004";
                guardarEvento(s.getAttribute("id"), "ACCESO", zonaDestino, "ACCESO_NO_AUTORIZADO_EJECUTADO");
            }
            case FALLO_NODO -> {
                Element n = listaNodos.get(1); int id = Integer.parseInt(n.getAttribute("idBinario"));
                NodoRegistro old = nodos.leerPorId(id);
                nodos.modificar(new NodoRegistro(id, 0, 100.0, 2, old.numeroOperaciones()+1));
                mundo.actualizarEstadoNodo(n.getAttribute("id"), "FALLO");
                guardarEvento(n.getAttribute("id"), "OPERACION_NODO", n.getAttribute("id"), "FALLO");
            }
            case AGENTE_FUERA_PROTOCOLO -> {
                Element a = agentes.get(0);
                mundo.actualizarEstadoAgente(a.getAttribute("id"), "FUERA_PROTOCOLO");
                guardarEvento(a.getAttribute("id"), "ACCION_AGENTE", a.getAttribute("zonaAsignada"), "FUERA_PROTOCOLO");
            }
            case ALTERACION_ZONA -> {
                Element z = zonas.get(2); String idZona = z.getAttribute("id");
                mundo.actualizarIntegridadZona(idZona, 35, "ALTERADA");
                guardarEvento("N003", "COMPROBACION_INTEGRIDAD", idZona, "ALERTA");
            }
        }
        EVIDENCIA.error("INCIDENTE_PERSISTIDO | codigo={} | descripcion={}", incidente.name(), incidente.descripcion());
        LOGGER.error("Incidente {} persistido en los archivos de datos", incidente.name());
        System.out.println("\nEvidencia del incidente persistida: " + incidente.descripcion());
        throw new EventoCriticoException(incidente.descripcion());
    }

    private void guardarEvento(String actorId, String tipo, String objetivo, String resultado) throws IOException {
        Evento evento = new Evento(String.format("E%04d", siguienteEvento++), LocalDateTime.now().withNano(0), actorId, tipo, objetivo, resultado);
        eventos.guardar(evento);
        EVIDENCIA.info("EVENTO_PERSISTIDO | id={} | fechaHora={} | actor={} | tipo={} | objetivo={} | resultado={}",
                evento.idEvento(), evento.fechaHora(), evento.actorId(), evento.tipoOperacion(), evento.objetivoId(), evento.resultado());
    }

    private void mostrarProgreso(long inicio, long duracion, int operaciones) {
        long transcurrido = System.nanoTime() - inicio;
        System.out.printf("Progreso: %d/%d s | operaciones: %d\r", Math.min(duracionSegundos, transcurrido / 1_000_000_000L), duracionSegundos, operaciones);
    }
    private static List<Element> elementos(Document d, String etiqueta) {
        NodeList lista=d.getElementsByTagName(etiqueta); List<Element> resultado=new ArrayList<>();
        for(int i=0;i<lista.getLength();i++) resultado.add((Element)lista.item(i)); return resultado;
    }
    private static int limitar(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static double limitar(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}