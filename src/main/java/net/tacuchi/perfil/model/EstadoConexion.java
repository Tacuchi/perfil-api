package net.tacuchi.perfil.model;

/**
 * Si una conexión se puede leer. Distinto del resultado de su última lectura: una conexión a la que le falta lo
 * que necesita queda FALTA_CONFIGURAR y no se lee, nunca aparece como una lectura sin ofertas.
 */
public enum EstadoConexion {
    LISTA, PAUSADA, FALTA_CONFIGURAR
}
