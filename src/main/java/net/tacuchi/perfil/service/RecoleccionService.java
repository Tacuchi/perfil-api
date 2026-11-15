package net.tacuchi.perfil.service;

import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.Fuente;

/**
 * Recolección de ofertas: la programada corre sola según perfil.ofertas.cron y la persona la lanza a demanda
 * desde el panel. Nunca corren dos a la vez.
 */
public interface RecoleccionService {

    /**
     * Recolección programada: lee cada fuente que hoy aún no tiene lecturas, guarda lo leído, depura lo vencido y
     * avisa si corresponde. Si hay otra en curso, esta se omite.
     */
    void recoger();

    /**
     * Lanza ahora la lectura de todas las fuentes con lecturas disponibles hoy y responde sin esperarla.
     *
     * @throws net.tacuchi.perfil.exception.RecoleccionRechazadaException si hay otra en curso o ninguna fuente
     *         tiene lecturas disponibles
     */
    RecoleccionResponse lanzar();

    /**
     * Lanza ahora la lectura de una fuente y responde sin esperarla.
     *
     * @throws net.tacuchi.perfil.exception.RecoleccionRechazadaException si hay otra en curso o la fuente llegó a
     *         su tope de hoy
     */
    RecoleccionResponse lanzar(Fuente fuente);

    /** Avance de la recolección en curso o resultado de la última. */
    RecoleccionResponse avance();
}
