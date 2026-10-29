package es.matrix.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Representa una operación del histórico e identifica la ejecución a la que pertenece. */
public record Evento(
        String idEvento,
        String idEjecucion,
        LocalDateTime fechaHora,
        String actorId,
        String tipoOperacion,
        String objetivoId,
        String resultado) {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Constructor de compatibilidad para eventos antiguos; se etiquetan como LEGACY. */
    public Evento(String idEvento, LocalDateTime fechaHora, String actorId,
                  String tipoOperacion, String objetivoId, String resultado) {
        this(idEvento, "LEGACY", fechaHora, actorId, tipoOperacion, objetivoId, resultado);
    }

    public Evento {
        validarObligatorio(idEvento, "idEvento");
        validarObligatorio(idEjecucion, "idEjecucion");
        if (fechaHora == null) throw new IllegalArgumentException("La fecha y hora son obligatorias.");
        validarObligatorio(actorId, "actorId");
        validarObligatorio(tipoOperacion, "tipoOperacion");
        validarObligatorio(objetivoId, "objetivoId");
        validarObligatorio(resultado, "resultado");
        validarCampo(idEvento); validarCampo(idEjecucion); validarCampo(actorId);
        validarCampo(tipoOperacion); validarCampo(objetivoId); validarCampo(resultado);
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(nombre + " es obligatorio.");
    }

    private static void validarCampo(String valor) {
        if (valor.contains("|") || valor.contains("\n") || valor.contains("\r"))
            throw new IllegalArgumentException("Los campos no pueden contener | ni saltos de línea.");
    }

    /** Nuevo formato de siete campos: idEvento|idEjecucion|fechaHora|actor|tipo|objetivo|resultado. */
    public String aLinea() {
        return String.join("|", idEvento, idEjecucion, FORMATO.format(fechaHora), actorId, tipoOperacion, objetivoId, resultado);
    }

    public static Evento desdeLinea(String linea) {
        String[] campos = linea.split("\\|", -1);
        try {
            if (campos.length == 6) {
                // Compatibilidad de lectura con el formato anterior; no se puede reconstruir la ejecución real.
                return new Evento(campos[0], "LEGACY", LocalDateTime.parse(campos[1], FORMATO), campos[2], campos[3], campos[4], campos[5]);
            }
            if (campos.length != 7) throw new IllegalArgumentException("La línea debe contener siete campos (o seis en formato antiguo): " + linea);
            return new Evento(campos[0], campos[1], LocalDateTime.parse(campos[2], FORMATO), campos[3], campos[4], campos[5], campos[6]);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha ISO 8601 no válida en la línea: " + linea, e);
        }
    }
}
