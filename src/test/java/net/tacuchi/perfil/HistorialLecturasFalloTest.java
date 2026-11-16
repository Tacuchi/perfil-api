package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.repository.HistorialLecturasRepository;
import net.tacuchi.perfil.service.RegistroOfertas;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class HistorialLecturasFalloTest extends PruebaOfertas {
    @MockitoSpyBean HistorialLecturasRepository historial;
    @MockitoSpyBean RegistroOfertas registroOfertas;

    @AfterEach void limpiarDobles() { reset(historial, registroOfertas); }

    @Test void registrar_falloDeHistorial_conservaUltimaLecturaYContinuaLasDemas() {
        doThrow(new IllegalStateException("fallo de escritura simulado"))
                .when(historial).registrar(eq(Fuente.GET_ON_BOARD), any(), anyLong(), any(), anyInt(), anyInt(), any(), any());
        var fin = leerADemanda(null);
        assertThat(fin.estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(fuente(Fuente.GET_ON_BOARD).estado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(historial.listar(Fuente.REMOTIVE, null).lecturas()).hasSize(1);
        assertThat(historial.listar(Fuente.REMOTE_OK, null).lecturas()).hasSize(1);
    }

    @Test void leer_falloInesperado_guardaMotivoGenericoEnUltimaLecturaEHistorial() {
        doThrow(new IllegalStateException("respuesta simulada inválida"))
                .when(registroOfertas).registrar(eq(Fuente.REMOTIVE), any(), any());
        leerADemanda(Fuente.REMOTIVE);
        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.FALLO);
        getIdentificado("/api/fuentes/REMOTIVE/historial").expectStatus().isOk().expectBody()
                .jsonPath("$.lecturas[0].estado").isEqualTo("FALLO")
                .jsonPath("$.lecturas[0].motivo").isEqualTo("no se pudo procesar la respuesta")
                .jsonPath("$.lecturas[0].nuevas").isEqualTo(0);
    }
}
