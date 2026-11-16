package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.soporte.ApifyDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Con una llave de claves que no mide 32 bytes la API arranca igual, y las de pago quedan sin configurar. */
@TestPropertySource(properties = "prueba.llave=bGxhdmUtY29ydGE=")
class ComputrabajoSinLlaveTest extends PruebaOfertas {

    @Test
    void laApiArrancaYComputrabajoQuedaFaltaConfigurarSinConsultasAlServicio() {
        FuenteResponse sinLlave = fuente(Fuente.COMPUTRABAJO);
        assertThat(sinLlave.estadoConexion()).isEqualTo(EstadoConexion.FALTA_CONFIGURAR);
        assertThat(sinLlave.queFalta()).isEqualTo("falta la llave de claves del servidor");

        cliente.put().uri("/api/fuentes/COMPUTRABAJO/clave")
                .header(CABECERA_ACCESS, ACCESS.codigoValido()).header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON).body("{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("FALTA_LLAVE")
                .jsonPath("$.detail").value(d -> assertThat((String) d)
                        .contains("falta la llave de claves del servidor").doesNotContain(ApifyDePrueba.CLAVE));
        assertThat(jdbc.sql("SELECT count(*) FROM conexion WHERE clave_cifrada IS NOT NULL").query(Integer.class)
                .single()).as("nada se cifró").isZero();

        FuenteResponse conTope = cliente.put().uri("/api/fuentes/COMPUTRABAJO/tope")
                .header(CABECERA_ACCESS, ACCESS.codigoValido()).header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON).body("{\"topeMensual\":5}").exchange()
                .expectStatus().isOk().expectBody(FuenteResponse.class).returnResult().getResponseBody();
        assertThat(conTope.estadoConexion()).isEqualTo(EstadoConexion.FALTA_CONFIGURAR);

        RecoleccionResponse general = lanzar(null);
        assertThat(general.omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.COMPUTRABAJO);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.FALTA_CONFIGURAR);
        });
        esperarFin();
        postIdentificado("/api/recoleccion/COMPUTRABAJO").expectStatus().isEqualTo(HttpStatus.CONFLICT);

        assertThat(APIFY.consultas()).isZero();
    }
}
