package net.tacuchi.perfil.service;

import net.tacuchi.perfil.dto.CorridaAbiertaResponse;
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.Fuente;

/**
 * Corrida local: la persona la abre desde el panel y el lector de su máquina la recorre con el código de un solo
 * uso. Usa el mismo candado y el mismo avance que las demás recolecciones.
 */
public interface CorridaLocalService {

    /**
     * Abre la corrida con las conexiones de la máquina no pausadas y con lecturas disponibles hoy. No gasta
     * ninguna lectura.
     *
     * @throws net.tacuchi.perfil.exception.RecoleccionRechazadaException si hay otra recolección en curso o
     *         ninguna conexión de la máquina se puede leer
     */
    CorridaAbiertaResponse abrir();

    /** El lector se reporta: desde aquí corre el plazo total y ya no el del reporte. */
    CorridaLectorResponse iniciar(String codigo);

    /**
     * El lector avisa que empieza a consultar el sitio: recién aquí se gasta la lectura del día.
     *
     * @throws net.tacuchi.perfil.exception.RecoleccionRechazadaException si la conexión ya no tiene lecturas hoy
     *         o quedó pausada: el lector no debe leerla
     */
    void empezar(String codigo, Fuente fuente);

    /** Guarda y clasifica los avisos, o registra el fallo clasificado. Nunca marca nada como «ya no publicada». */
    FuenteEnRecoleccion entregar(String codigo, Fuente fuente, EntregaRequest entrega);

    /** Cierra la corrida, suelta el candado y deja el código sin uso. */
    RecoleccionResponse terminar(String codigo);

    /** Cierra la corrida abierta si venció el plazo del reporte o el total. */
    void revisarPlazos();
}
