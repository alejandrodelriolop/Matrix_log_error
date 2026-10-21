package es.matrix.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Representa una línea del histórico operaciones.txt. */
public record Evento(

        String idEvento,
        LocalDateTime fechaHora,
        String actorId,
        String tipoOperacion,
        String objetivoId,
        String resultado) {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public Evento {
        if (idEvento == null || idEvento.isBlank()
                || fechaHora == null
                || actorId == null || actorId.isBlank()
                || tipoOperacion == null || tipoOperacion.isBlank()
                || objetivoId == null || objetivoId.isBlank()
                || resultado == null || resultado.isBlank()) {
            throw new IllegalArgumentException("Todos los campos del evento son obligatorios.");
        }
        validarCampo(idEvento);
        validarCampo(actorId);
        validarCampo(tipoOperacion);
        validarCampo(objetivoId);
        validarCampo(resultado);
    }

    private static void validarCampo(String valor) {
        if (valor.contains("|") || valor.contains("\n") || valor.contains("\r")) {
            throw new IllegalArgumentException("Los campos no pueden contener | ni saltos de línea.");
        }
    }

    public String aLinea() {
        return String.join("|", idEvento, FORMATO.format(fechaHora), actorId,
                tipoOperacion, objetivoId, resultado);
    }

    public static Evento desdeLinea(String linea) {
        String[] campos = linea.split("\\|", -1);
        if (campos.length != 6) {
            throw new IllegalArgumentException("La línea debe contener exactamente seis campos: " + linea);
        }
        try {
            return new Evento(campos[0], LocalDateTime.parse(campos[1], FORMATO),
                    campos[2], campos[3], campos[4], campos[5]);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha ISO 8601 no válida: " + campos[1], e);
        }
    }
}
