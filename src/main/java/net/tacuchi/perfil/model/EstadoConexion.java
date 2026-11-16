package net.tacuchi.perfil.model;

/**
 * Si una conexión se puede leer. Distinto del resultado de su última lectura: una conexión a la que le falta lo
 * que necesita queda FALTA_CONFIGURAR o FALTA_INICIAR_SESION y no se lee, nunca aparece como una lectura sin
 * ofertas. PENDIENTE_MAQUINA: se lee desde la máquina de la persona y hoy aún no se leyó.
 */
public enum EstadoConexion {
    LISTA, PAUSADA, FALTA_CONFIGURAR, FALTA_INICIAR_SESION, PENDIENTE_MAQUINA
}
