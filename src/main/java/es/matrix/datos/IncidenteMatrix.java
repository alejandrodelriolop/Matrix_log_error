package es.matrix.datos;

/** Incidentes que se pueden reproducir en el modo de pruebas. */
public enum IncidenteMatrix {
    PERDIDA_SINCRONIZACION(1, "Pérdida de sincronización"),
    COMPORTAMIENTO_SUJETO(2, "Comportamiento anómalo de un sujeto"),
    ACCESO_NO_AUTORIZADO(3, "Acceso no autorizado"),
    FALLO_NODO(4, "Fallo de nodo"),
    AGENTE_FUERA_PROTOCOLO(5, "Agente fuera de protocolo"),
    ALTERACION_ZONA(6, "Alteración de zona");

    private final int numero;
    private final String descripcion;
    IncidenteMatrix(int numero, String descripcion) { this.numero = numero; this.descripcion = descripcion; }
    public int numero() { return numero; }
    public String descripcion() { return descripcion; }
    public static IncidenteMatrix desdeNumero(int numero) {
        for (IncidenteMatrix incidente : values()) if (incidente.numero == numero) return incidente;
        throw new IllegalArgumentException("Incidente no válido. Elige un número del 1 al 6.");
    }
}
