package net.tacuchi.perfil.service;

import java.util.List;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.model.Fuente;

/** Las conexiones del monitor: su estado y lo que la persona administra de cada una. */
public interface ConexionesService {

    /** Una por cada fuente conocida, en el orden del catálogo. */
    List<FuenteResponse> listar();

    /** Pausa la conexión: ninguna recolección la lee hasta reanudarla. */
    FuenteResponse pausar(Fuente fuente);

    FuenteResponse reanudar(Fuente fuente);
}
