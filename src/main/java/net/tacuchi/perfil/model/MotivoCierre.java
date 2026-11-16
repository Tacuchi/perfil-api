package net.tacuchi.perfil.model;

/**
 * Por qué una corrida local se cerró sin que el lector la terminara: no se reportó a tiempo en la máquina de la
 * persona, o se acabó el plazo total.
 */
public enum MotivoCierre {
    LECTOR_SIN_RESPUESTA, PLAZO_VENCIDO
}
