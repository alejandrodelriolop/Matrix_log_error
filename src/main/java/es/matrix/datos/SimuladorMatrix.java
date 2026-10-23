// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package es.matrix.datos;

import es.matrix.modelo.Evento;
import es.matrix.modelo.NodoRegistro;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SimuladorMatrix {
    private final Path carpetaDatos;
    private final int duracionSegundos;
    private final boolean modoPruebas;
    private final Random random = new Random();
    private TextEventRepository eventos;
    private BinaryNodeRepository nodos;
    private XmlWorldRepository mundo;
    private JsonRulesRepository reglasRepository;
    private int siguienteEvento;

    public SimuladorMatrix(Path carpetaDatos, int duracionSegundos, boolean modoPruebas) {
        if (carpetaDatos == null) {
            throw new IllegalArgumentException("La carpeta de datos es obligatoria.");
        } else if (duracionSegundos <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor que cero.");
        } else {
            this.carpetaDatos = carpetaDatos;
            this.duracionSegundos = duracionSegundos;
            this.modoPruebas = modoPruebas;
        }
    }

    public void ejecutar() throws IOException, InterruptedException {
        this.prepararRepositorios();
        this.validarDatosIniciales();
        this.siguienteEvento = this.eventos.leerTodos().size() + 1;
        int var10001 = this.duracionSegundos;
        System.out.println("Inicio de Matrix. Duración: " + var10001 + " segundos.");
        System.out.println("Modo: " + (this.modoPruebas ? "PRUEBAS" : "NORMAL"));
        long inicio = System.nanoTime();
        long duracionNanos = (long)this.duracionSegundos * 1000000000L;
        int operaciones = 0;

        while(System.nanoTime() - inicio < duracionNanos) {
            this.realizarOperacionNormal();
            ++operaciones;
            this.mostrarProgreso(inicio, duracionNanos, operaciones);
            Thread.sleep(700L);
        }

        System.out.println();
        System.out.println("Simulación finalizada.");
        System.out.println("Operaciones generadas en esta ejecución: " + operaciones);
        System.out.println("Histórico: " + String.valueOf(this.carpetaDatos.resolve("operaciones.txt").toAbsolutePath()));
        System.out.println("Estado binario: " + String.valueOf(this.carpetaDatos.resolve("nodos.dat").toAbsolutePath()));
        System.out.println("Estado XML: " + String.valueOf(this.carpetaDatos.resolve("estado.xml").toAbsolutePath()));
    }

    private void prepararRepositorios() throws IOException {
        Files.createDirectories(this.carpetaDatos);
        this.eventos = new TextEventRepository(this.carpetaDatos.resolve("operaciones.txt"));
        this.nodos = new BinaryNodeRepository(this.carpetaDatos.resolve("nodos.dat"));
        this.mundo = new XmlWorldRepository(this.carpetaDatos.resolve("estado.xml"));
        this.reglasRepository = new JsonRulesRepository(this.carpetaDatos.resolve("reglas.json"));
    }

    private void validarDatosIniciales() throws IOException {
        if (this.nodos.contarRegistros() < 4L) {
            throw new IOException("nodos.dat debe contener los cuatro nodos. Ejecuta primero InicializarDatosMain.");
        } else {
            this.mundo.leerEstado();
            this.reglasRepository.leerReglas();
        }
    }

    private void realizarOperacionNormal() throws IOException {
        Document documento = this.mundo.leerEstado();
        List<Element> sujetos = elementos(documento, "sujeto");
        List<Element> agentes = elementos(documento, "agente");
        List<Element> zonas = elementos(documento, "zona");
        List<Element> listaNodos = elementos(documento, "nodo");
        if (!sujetos.isEmpty() && !agentes.isEmpty() && !zonas.isEmpty() && !listaNodos.isEmpty()) {
            int tipo = this.random.nextInt(4);
            String resultado = "OK";
            String actorId;
            String tipoOperacion;
            String objetivoId;
            switch (tipo) {
                case 0:
                    Element sujeto = (Element)sujetos.get(this.random.nextInt(sujetos.size()));
                    actorId = sujeto.getAttribute("id");
                    tipoOperacion = "CONEXION";
                    objetivoId = sujeto.getAttribute("zonaActual");
                    break;
                case 1:
                    Element sujeto2 = (Element)sujetos.get(this.random.nextInt(sujetos.size()));
                    actorId = sujeto2.getAttribute("id");
                    tipoOperacion = "ACCESO";
                    objetivoId = sujeto2.getAttribute("zonaActual");
                    break;
                case 2:
                    Element agente = (Element)agentes.get(this.random.nextInt(agentes.size()));
                    actorId = agente.getAttribute("id");
                    tipoOperacion = "INSPECCION";
                    objetivoId = agente.getAttribute("zonaAsignada");
                    break;
                default:
                    Element nodoXml = (Element)listaNodos.get(this.random.nextInt(listaNodos.size()));
                    actorId = nodoXml.getAttribute("id");
                    tipoOperacion = "COMPROBACION_INTEGRIDAD";
                    objetivoId = nodoXml.getAttribute("id");
            }

            Evento evento = new Evento(String.format("E%04d", this.siguienteEvento++), LocalDateTime.now().withNano(0), actorId, tipoOperacion, objetivoId, resultado);
            this.eventos.guardar(evento);
            Element nodoElegido = (Element)listaNodos.get(this.random.nextInt(listaNodos.size()));
            int idBinario = Integer.parseInt(nodoElegido.getAttribute("idBinario"));
            NodoRegistro anterior = this.nodos.leerPorId(idBinario);
            int sincronizacion = limitar(anterior.nivelSincronizacion() + this.random.nextInt(7) - 3, 85, 100);
            double carga = limitar(anterior.carga() + (this.random.nextDouble() * (double)10.0F - (double)5.0F), (double)10.0F, (double)85.0F);
            NodoRegistro actualizado = new NodoRegistro(idBinario, sincronizacion, carga, 0, anterior.numeroOperaciones() + 1L);
            this.nodos.modificar(actualizado);
            this.mundo.actualizarEstadoNodo(nodoElegido.getAttribute("id"), actualizado.estadoTexto());
            System.out.printf("%s | %s | %s -> %s | nodo %s: sync=%d%%, carga=%.1f%%%n", evento.idEvento(), evento.tipoOperacion(), evento.actorId(), evento.objetivoId(), nodoElegido.getAttribute("id"), sincronizacion, carga);
        } else {
            throw new IOException("estado.xml no contiene sujetos, agentes, zonas y nodos suficientes.");
        }
    }

    private void mostrarProgreso(long inicio, long duracion, int operaciones) {
        long transcurrido = System.nanoTime() - inicio;
        System.out.printf("Progreso: %d/%d s | operaciones: %d\r", Math.min((long)this.duracionSegundos, transcurrido / 1000000000L), this.duracionSegundos, operaciones);
    }

    private static List<Element> elementos(Document documento, String etiqueta) {
        NodeList nodos = documento.getElementsByTagName(etiqueta);
        List<Element> resultado = new ArrayList();

        for(int i = 0; i < nodos.getLength(); ++i) {
            resultado.add((Element)nodos.item(i));
        }

        return resultado;
    }

    private static int limitar(int valor, int minimo, int maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private static double limitar(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}
