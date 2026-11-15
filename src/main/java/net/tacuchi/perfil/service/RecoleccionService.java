package net.tacuchi.perfil.service;

/** Recolección diaria de ofertas. Nadie la dispara: corre sola según perfil.ofertas.cron. */
public interface RecoleccionService {

    /** Lee cada fuente que aún no se leyó hoy, guarda lo leído, depura lo vencido y avisa si corresponde. */
    void recoger();
}
