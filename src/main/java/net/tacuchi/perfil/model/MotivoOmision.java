package net.tacuchi.perfil.model;

/**
 * Por qué una recolección no lee una fuente. DESDE_MAQUINA: solo la lee el lector de la máquina de la persona.
 * FALTA_CONFIGURAR: a una de pago le falta su clave, su tope mensual o la llave de claves del servidor.
 * TOPE_MENSUAL: una de pago ya consumió su tope del mes.
 */
public enum MotivoOmision {
    TOPE_DIARIO, PAUSADA, DESDE_MAQUINA, FALTA_CONFIGURAR, TOPE_MENSUAL
}
