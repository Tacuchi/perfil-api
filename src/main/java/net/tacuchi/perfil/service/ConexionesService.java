package net.tacuchi.perfil.service;

import net.tacuchi.perfil.dto.HistorialLecturasResponse;
import java.math.BigDecimal;
import java.util.List;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.model.Fuente;

/** Las conexiones del monitor: su estado y lo que la persona administra de cada una. */
public interface ConexionesService {

    /** Una por cada fuente conocida, en el orden del catálogo. */
    List<FuenteResponse> listar();

    HistorialLecturasResponse historial(Fuente fuente, Long antesDe);

    /** Pausa la conexión: ninguna recolección la lee hasta reanudarla. */
    FuenteResponse pausar(Fuente fuente);

    FuenteResponse reanudar(Fuente fuente);

    /** Cifra y guarda la clave de una conexión de pago, reemplazando la anterior. La clave no vuelve nunca. */
    FuenteResponse cargarClave(Fuente fuente, String clave);

    /** Fija el tope mensual de gasto de una conexión de pago, en dólares. */
    FuenteResponse fijarTope(Fuente fuente, BigDecimal tope);
}
