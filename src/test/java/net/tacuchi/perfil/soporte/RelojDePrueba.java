package net.tacuchi.perfil.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj real con un desfase que las pruebas adelantan para simular el paso de los días. */
public class RelojDePrueba extends Clock {

    private volatile Duration desfase = Duration.ZERO;

    public void adelantar(Duration cuanto) {
        desfase = desfase.plus(cuanto);
    }

    public void reiniciar() {
        desfase = Duration.ZERO;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(desfase);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        throw new UnsupportedOperationException("el reloj de prueba solo trabaja en UTC");
    }
}
