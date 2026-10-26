package es.matrix.datos;

/** Señala que la simulación ha registrado un incidente crítico y debe detenerse. */
public class EventoCriticoException extends Exception {
    public EventoCriticoException(String mensaje) { super(mensaje); }
}