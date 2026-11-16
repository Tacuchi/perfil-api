package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.function.Supplier;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Las rutas del lector siguen protegidas aunque ninguna fuente de ofertas necesite la máquina. */
class CorridaLocalTest extends PruebaOfertas {
    @Test void sinFuentesDeMaquina_noAbreCorridaNiOcupaElCandado() {
        var antes = avance();
        postIdentificado("/api/recoleccion/maquina").expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.motivo").isEqualTo("SIN_FUENTES");
        assertThat(avance()).isEqualTo(antes);
        leerADemanda(net.tacuchi.perfil.model.Fuente.REMOTIVE);
        assertThat(APIFY.consultas()).isZero();
    }

    @Test void abrirLaCorrida_sigueExigiendoIdentidadDelPanelYOrigen() {
        cliente.post().uri("/api/recoleccion/maquina").header("Origin", ORIGEN_PANEL).exchange()
                .expectStatus().isUnauthorized();
        cliente.post().uri("/api/recoleccion/maquina").header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro.test").exchange().expectStatus().isForbidden();
        cliente.post().uri("/api/recoleccion/maquina").header(CABECERA_ACCESS, ACCESS.codigoDelLector())
                .header("Origin", ORIGEN_PANEL).exchange().expectStatus().isUnauthorized();
    }

    @Test void rutasDelLector_rechazanIdentidadesAjenasYCodigosInventados() {
        List<Supplier<String>> ajenas = List.of(() -> null, ACCESS::codigoValido,
                () -> ACCESS.codigoDeServicio("otro-token.access"), ACCESS::codigoDelLectorConCorreo,
                ACCESS::codigoDelLectorVencido, ACCESS::codigoDelLectorConFirmaAjena);
        for (String ruta : List.of("iniciar", "terminar", "fuentes/LINKEDIN/empezar", "fuentes/BUMERAN/empezar")) {
            for (Supplier<String> identidad : ajenas) {
                var peticion = cliente.post().uri("/api/lector/corrida/" + ruta).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"codigo\":\"" + "A".repeat(43) + "\"}");
                String codigo = identidad.get();
                if (codigo != null) { peticion.header(CABECERA_ACCESS, codigo); }
                peticion.exchange().expectStatus().isUnauthorized();
            }
            cliente.post().uri("/api/lector/corrida/" + ruta).header(CABECERA_ACCESS, ACCESS.codigoDelLector())
                    .contentType(MediaType.APPLICATION_JSON).body("{\"codigo\":\"" + "A".repeat(43) + "\"}")
                    .exchange().expectStatus().isForbidden();
        }
        cliente.get().uri("/api/fuentes").header(CABECERA_ACCESS, ACCESS.codigoDelLector()).exchange()
                .expectStatus().isUnauthorized();
    }
}
