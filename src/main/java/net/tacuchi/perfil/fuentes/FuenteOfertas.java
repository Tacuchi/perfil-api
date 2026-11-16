package net.tacuchi.perfil.fuentes;

import java.util.List;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.OfertaRecogida;

/**
 * Contrato de cada sitio de empleo. Una pieza por sitio: sumar otro no toca el resto.
 * Una lectura usa solo la vía que el sitio declara; ante un fallo o un bloqueo lanza
 * {@link LecturaFallidaException} y no prueba otra vía.
 */
public interface FuenteOfertas {

    Fuente fuente();

    Lectura leer();

    /**
     * @param completa true si la lectura recorrió todo lo que la fuente publica: solo entonces lo que falta se
     *                 marca como «ya no publicada».
     */
    record Lectura(List<OfertaRecogida> ofertas, boolean completa, java.math.BigDecimal costoUsd, String detalle) {
        public Lectura(List<OfertaRecogida> ofertas, boolean completa) {
            this(ofertas, completa, null, null);
        }
    }
}
