package net.tacuchi.perfil.service;

/** Correo diario a la persona con las ofertas nuevas de encaje alto. */
public interface AvisoService {

    /** Envía el correo del día si el aviso está encendido, hay ofertas nuevas de encaje alto y hoy no salió otro. */
    void avisarSiCorresponde();
}
