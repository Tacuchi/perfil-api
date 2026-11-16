package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;

class CorridaBumeranTest extends PruebaOfertas {
    @Test void bumeranYLinkedin_declaranPagoSinTopeDiarioNiEstadoDeMaquina() {
        for (Fuente fuente : java.util.List.of(Fuente.LINKEDIN, Fuente.BUMERAN)) {
            var conexion = fuente(fuente);
            assertThat(conexion.via()).isEqualTo(Via.PAGO);
            assertThat(conexion.necesita()).isEqualTo(Requisito.CLAVE);
            assertThat(conexion.lecturasDia()).isZero();
            assertThat(conexion.avisosPorCorrida()).isNull();
            assertThat(conexion.estadoConexion()).isEqualTo(EstadoConexion.FALTA_CONFIGURAR);
            postIdentificado("/api/recoleccion/" + fuente).expectStatus().isEqualTo(409).expectBody()
                    .jsonPath("$.motivo").isEqualTo("FALTA_CONFIGURAR");
        }
        assertThat(APIFY.consultas()).isZero();
    }
}
