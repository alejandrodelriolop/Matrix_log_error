package es.matrix.datos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lee y valida la configuración JSON usada por Matrix. */
public class JsonRulesRepository {
    private final Path archivo;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonRulesRepository(Path archivo) {
        this.archivo = archivo;
    }

    public JsonNode leerReglas() throws IOException {
        if (Files.notExists(archivo)) throw new IOException("No existe el fichero de reglas: " + archivo);
        JsonNode raiz = mapper.readTree(archivo.toFile());
        if (raiz == null || !raiz.isObject()) throw new IOException("reglas.json debe contener un objeto JSON.");
        comprobarNumero(raiz, "sincronizacion", "nivelMinimo", 0, 100);
        comprobarNumero(raiz, "carga", "nivelMaximo", 0, 100);
        comprobarNumero(raiz, "integridad", "nivelMinimo", 0, 100);
        return raiz;
    }

    private static void comprobarNumero(JsonNode raiz, String grupo, String campo, double minimo, double maximo)
            throws IOException {
        JsonNode nodo = raiz.path(grupo).path(campo);
        if (!nodo.isNumber() || nodo.asDouble() < minimo || nodo.asDouble() > maximo) {
            throw new IOException("Regla no válida: " + grupo + "." + campo
                    + " debe ser un número entre " + minimo + " y " + maximo + ".");
        }
    }
}
