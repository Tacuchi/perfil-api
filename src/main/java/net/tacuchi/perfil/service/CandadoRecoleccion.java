package net.tacuchi.perfil.service;

import java.time.Clock;
import java.util.function.UnaryOperator;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.MotivoCierre;
import net.tacuchi.perfil.model.PasoFuente;
import org.springframework.stereotype.Component;

/**
 * El único candado y el único avance de todas las recolecciones: programada, a demanda y corrida local. Nunca
 * corren dos a la vez.
 *
 * <p>El candado se toma en el hilo que pide la recolección (para responder al instante si se acepta) y lo suelta
 * otro hilo; por eso es una bandera guardada por un monitor y no un {@code ReentrantLock}, que solo puede soltar el
 * hilo que lo tomó. El avance vive en memoria: con una sola instancia de la API basta.
 */
@Component
public class CandadoRecoleccion {

    private final Clock clock;
    private final Object monitor = new Object();
    /** Guardada por {@link #monitor}. */
    private boolean enCurso;
    /** Se cambia solo dentro de {@link #monitor}; se lee sin él. */
    private volatile RecoleccionResponse avance = RecoleccionResponse.NINGUNA;

    public CandadoRecoleccion(Clock clock) {
        this.clock = clock;
    }

    /** @return false si ya hay una recolección en curso */
    public boolean tomar() {
        synchronized (monitor) {
            if (enCurso) {
                return false;
            }
            enCurso = true;
            return true;
        }
    }

    public void soltar(boolean sinAvance) {
        soltar(sinAvance, null);
    }

    /**
     * Suelta el candado y, si la recolección llegó a mostrarse, la cierra en el mismo paso: quien ve el resultado
     * final ya puede lanzar otra. Una fuente que no llegó a terminar figura como fallida.
     *
     * @param motivo por qué se cierra sin que la terminara quien la lanzó; null si terminó
     */
    public void soltar(boolean sinAvance, MotivoCierre motivo) {
        synchronized (monitor) {
            enCurso = false;
            if (sinAvance || avance.estado() != EstadoRecoleccion.EN_CURSO) {
                return;
            }
            RecoleccionResponse cerrado = avance;
            for (FuenteEnRecoleccion fuente : avance.fuentes()) {
                if (fuente.paso() != PasoFuente.TERMINADA) {
                    cerrado = cerrado.conFuente(fuente.fuente(), f -> f.terminada(EstadoLectura.FALLO, 0));
                }
            }
            avance = cerrado.terminada(clock.instant(), motivo);
        }
    }

    public RecoleccionResponse cambiar(UnaryOperator<RecoleccionResponse> cambio) {
        synchronized (monitor) {
            avance = cambio.apply(avance);
            return avance;
        }
    }

    public RecoleccionResponse avance() {
        return avance;
    }
}
