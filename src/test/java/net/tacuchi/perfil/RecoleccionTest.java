package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Recolección contra los tres sitios servidos en local con sus respuestas reales guardadas. */
class RecoleccionTest extends PruebaOfertas {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void cadaFuenteEntregaOfertasConTodoLoQueLaListaMuestra() {
        recoleccion.recoger();

        for (Fuente fuente : Fuente.values()) {
            List<OfertaResponse> ofertas = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=" + fuente.name());
            assertThat(ofertas).as(fuente.nombre()).isNotEmpty().allSatisfy(o -> {
                assertThat(o.titulo()).isNotBlank();
                assertThat(o.empresa()).isNotBlank();
                assertThat(o.fuenteNombre()).isEqualTo(fuente.nombre());
                assertThat(o.fecha()).isNotNull();
                assertThat(o.modalidad()).isNotNull();
                assertThat(o.url()).startsWith("https://");
                assertThat(o.motivos()).isNotEmpty();
            });
        }
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTIVE")).hasSize(4);
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTE_OK")).hasSize(4);
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=GET_ON_BOARD")).hasSize(6);
        assertThat(fuentes()).allSatisfy(f -> assertThat(f.estado()).isEqualTo(EstadoLectura.CORRECTA));
    }

    @Test
    void cadaFuenteSeLeeUnaSolaVezPorDiaAunqueLaRecoleccionSeDispareMasVeces() {
        recoleccion.recoger();
        recoleccion.recoger();

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD)).as("una página por categoría").isEqualTo(4);

        reloj.adelantar(Duration.ofDays(1));
        recoleccion.recoger();

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(2);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(2);
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD)).isEqualTo(8);
    }

    @Test
    void unaFuenteBloqueadaNoPareceUnDiaSinOfertas() {
        recoleccion.recoger();
        Instant ayer = fuente(Fuente.REMOTE_OK).ultimaCorrecta();
        int ofertasDeAyer = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTE_OK").size();

        reloj.adelantar(Duration.ofDays(1));
        FUENTES.modo(Fuente.REMOTE_OK, FuentesDePrueba.Modo.BLOQUEA);
        recoleccion.recoger();

        FuenteResponse remoteOk = fuente(Fuente.REMOTE_OK);
        assertThat(remoteOk.estado()).isEqualTo(EstadoLectura.BLOQUEO);
        assertThat(remoteOk.ultimaCorrecta()).isEqualTo(ayer);
        assertThat(remoteOk.ultimaLectura()).isAfter(ayer);
        List<OfertaResponse> siguen = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTE_OK");
        assertThat(siguen).hasSize(ofertasDeAyer).allSatisfy(o -> assertThat(o.publicada()).isTrue());
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).as("sin segundo intento").isEqualTo(2);
        assertThat(FUENTES.rutas()).filteredOn(r -> r.startsWith("/remoteok"))
                .as("sin otra vía").containsOnly("/remoteok/api");
        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.CORRECTA);
    }

    @Test
    void unaFuenteQueFallaQuedaComoFalloYUnaNoLeidaComoSinLeer() {
        assertThat(fuentes()).allSatisfy(f -> assertThat(f.estado()).isEqualTo(EstadoLectura.SIN_LEER));

        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.FALLA);
        recoleccion.recoger();

        FuenteResponse remotive = fuente(Fuente.REMOTIVE);
        assertThat(remotive.estado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(remotive.detalle()).contains("500");
        assertThat(remotive.ultimaCorrecta()).isNull();
        assertThat(remotive.ultimaLectura()).isNotNull();
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
    }

    @Test
    void unaRespuestaQueNoEsJsonCuentaComoFallo() {
        FUENTES.reemplazar("remotive.json", "<html>mantenimiento</html>");
        recoleccion.recoger();

        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.FALLO);
    }

    @Test
    void loQueLaFuenteDejoDePublicarApareceComoYaNoPublicado() {
        recoleccion.recoger();
        ObjectNode respuesta = (ObjectNode) JSON.readTree(FuentesDePrueba.guardada("remotive.json"));
        ArrayNode ofertas = (ArrayNode) respuesta.get("jobs");
        for (int i = ofertas.size() - 1; i >= 0; i--) {
            if (ofertas.get(i).path("id").asLong() == 2091136L) {
                ofertas.remove(i);
            }
        }
        String sinUna = JSON.writeValueAsString(respuesta);

        reloj.adelantar(Duration.ofDays(1));
        FUENTES.reemplazar("remotive.json", sinUna);
        recoleccion.recoger();

        OfertaResponse retirada = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTIVE").stream()
                .filter(o -> o.url().endsWith("-2091136")).findFirst().orElseThrow();
        assertThat(retirada.publicada()).isFalse();
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTIVE")).filteredOn(OfertaResponse::publicada)
                .hasSize(3);
    }

    @Test
    void elCorreoDeContactoSaleDelTextoDeLaOferta() {
        recoleccion.recoger();

        OfertaResponse conCorreo = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=REMOTIVE").stream()
                .filter(OfertaResponse::conCorreo).findFirst().orElseThrow();
        assertThat(detalle(conCorreo.id()).correoContacto()).isEqualTo("postulaciones@empresa-inventada.test");
    }
}
