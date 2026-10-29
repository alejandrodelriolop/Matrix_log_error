package es.matrix.persistencia;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lectura, comprobación y actualización básica de estado.xml mediante DOM. */
public class XmlWorldRepository {
    private final Path archivo;

    public XmlWorldRepository(Path archivo) {
        this.archivo = archivo;
    }

    public Document leerEstado() throws IOException {
        if (Files.notExists(archivo)) throw new IOException("No existe estado.xml: " + archivo);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(archivo.toFile());
            if (document.getDocumentElement() == null || !"matrix".equals(document.getDocumentElement().getTagName())) {
                throw new IOException("La raíz de estado.xml debe ser <matrix>.");
            }
            validarIdsUnicos(document, "sujeto");
            validarIdsUnicos(document, "agente");
            validarIdsUnicos(document, "zona");
            validarIdsUnicos(document, "nodo");
            return document;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("No se pudo leer o validar estado.xml.", e);
        }
    }

    public void actualizarEstadoNodo(String idNodo, String estado) throws IOException {
        if (!estado.equals("NORMAL") && !estado.equals("DEGRADADO") && !estado.equals("FALLO")) {
            throw new IllegalArgumentException("Estado no válido: " + estado);
        }
        Document document = leerEstado();
        Element nodo = buscarPorId(document, "nodo", idNodo);
        if (nodo == null) throw new IOException("No existe el nodo " + idNodo + " en estado.xml.");
        nodo.setAttribute("estado", estado);
        guardar(document);
    }
    public void actualizarIntegridadZona(String idZona, int integridad, String estado) throws IOException {
        if (integridad < 0 || integridad > 100) throw new IllegalArgumentException("La integridad debe estar entre 0 y 100.");
        Document document = leerEstado();
        Element zona = buscarPorId(document, "zona", idZona);
        if (zona == null) throw new IOException("No existe la zona " + idZona + ".");
        zona.setAttribute("integridad", Integer.toString(integridad));
        zona.setAttribute("estado", estado);
        guardar(document);
    }
    public void actualizarEstadoAgente(String idAgente, String estado) throws IOException {
        Document document = leerEstado();
        Element agente = buscarPorId(document, "agente", idAgente);
        if (agente == null) throw new IOException("No existe el agente " + idAgente + ".");
        agente.setAttribute("estado", estado);
        guardar(document);
    }
    public void actualizarZonaSujeto(String idSujeto, String idZona) throws IOException {
        Document document = leerEstado();
        if (buscarPorId(document, "zona", idZona) == null) {
            throw new IOException("No existe la zona " + idZona + ".");
        }
        Element sujeto = buscarPorId(document, "sujeto", idSujeto);
        if (sujeto == null) throw new IOException("No existe el sujeto " + idSujeto + ".");
        sujeto.setAttribute("zonaActual", idZona);
        guardar(document);
    }

    private void guardar(Document document) throws IOException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(archivo.toFile()));
        } catch (Exception e) {
            throw new IOException("No se pudo guardar estado.xml.", e);
        }
    }

    private static Element buscarPorId(Document document, String etiqueta, String id) {
        NodeList elementos = document.getElementsByTagName(etiqueta);
        for (int i = 0; i < elementos.getLength(); i++) {
            Element elemento = (Element) elementos.item(i);
            if (id.equals(elemento.getAttribute("id"))) return elemento;
        }
        return null;
    }

    private static void validarIdsUnicos(Document document, String etiqueta) throws IOException {
        NodeList elementos = document.getElementsByTagName(etiqueta);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < elementos.getLength(); i++) {
            Element elemento = (Element) elementos.item(i);
            String id = elemento.getAttribute("id");
            if (id.isBlank() || !ids.add(id)) {
                throw new IOException("ID vacío o duplicado en elementos <" + etiqueta + ">: " + id);
            }
        }
    }
}