package es.matrix.modelo;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class EventoTest {
    @Test
    void convierteEventoALineaYLoRecupera() {
        Evento original = new Evento("E0001", LocalDateTime.parse("2026-10-27T10:00:00"),
                "S001", "MOVIMIENTO", "Z002", "CORRECTO");
        assertEquals(original, Evento.desdeLinea(original.aLinea()));
    }

    @Test
    void rechazaLineaConCamposIncorrectos() {
        assertThrows(IllegalArgumentException.class, () -> Evento.desdeLinea("E1|fecha|S1"));
    }

    @Test
    void rechazaSeparadorDentroDeCampo() {
        assertThrows(IllegalArgumentException.class, () ->
                new Evento("E1", LocalDateTime.now(), "S|001", "ACCESO", "Z001", "OK"));
    }
}
