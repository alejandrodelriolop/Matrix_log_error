package es.matrix.modelo;

/** Datos actuales de un nodo. El formato binario ocupa 28 bytes por registro. */
public record NodoRegistro(
        int idNumerico,
        int nivelSincronizacion,
        double carga,
        int codigoEstado,
        long numeroOperaciones) {

    public static final int TAMANO_REGISTRO = Integer.BYTES + Integer.BYTES
            + Double.BYTES + Integer.BYTES + Long.BYTES; // 28 bytes

    public NodoRegistro {
        if (idNumerico < 1) throw new IllegalArgumentException("El ID numérico debe ser mayor que cero.");
        if (nivelSincronizacion < 0 || nivelSincronizacion > 100)
            throw new IllegalArgumentException("La sincronización debe estar entre 0 y 100.");
        if (!Double.isFinite(carga) || carga < 0 || carga > 100)
            throw new IllegalArgumentException("La carga debe estar entre 0 y 100.");
        if (codigoEstado < 0 || codigoEstado > 2)
            throw new IllegalArgumentException("Código de estado válido: 0, 1 o 2.");
        if (numeroOperaciones < 0)
            throw new IllegalArgumentException("El número de operaciones no puede ser negativo.");
    }

    public String estadoTexto() {
        return switch (codigoEstado) {
            case 0 -> "NORMAL";
            case 1 -> "DEGRADADO";
            case 2 -> "FALLO";
            default -> throw new IllegalStateException("Código de estado desconocido.");
        };
    }
}