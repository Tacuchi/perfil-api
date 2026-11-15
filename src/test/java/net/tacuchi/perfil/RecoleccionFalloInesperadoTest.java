package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Un fallo que nadie esperaba en el hilo que lee no deja la recolección colgada ni el candado tomado. */
class RecoleccionFalloInesperadoTest extends PruebaOfertas {

    @MockitoSpyBean
    private FuenteRepository fuenteRepository;

    @Test
    void trasUnaExcepcionInesperadaLaRecoleccionTerminaYSePuedeLanzarOtra() {
        doThrow(new IllegalStateException("base caída a mitad de lectura"))
                .when(fuenteRepository).registrar(eq(Fuente.REMOTIVE), any(), any(), any(), anyInt());

        lanzar(Fuente.REMOTIVE);
        RecoleccionResponse fin = esperarFin();

        assertThat(fin.estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(enAvance(fin, Fuente.REMOTIVE).resultado()).isEqualTo(EstadoLectura.FALLO);

        reset(fuenteRepository);
        RecoleccionResponse otra = leerADemanda(Fuente.REMOTIVE);
        assertThat(enAvance(otra, Fuente.REMOTIVE).resultado()).isEqualTo(EstadoLectura.CORRECTA);
    }
}
