package net.tacuchi.perfil;

import static net.tacuchi.perfil.soporte.Ofertas.oferta;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.RepetidaResponse;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.EstadoOferta;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;

/** Guardar, clasificar, listar y marcar ofertas, por HTTP y sobre PostgreSQL. */
class OfertasTest extends PruebaOfertas {

    @Test
    void unaOfertaEncontradaVariasVecesEnLaMismaFuenteApareceUnaVez() {
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("77").build(),
                oferta().fuente(Fuente.REMOTIVE).id("77").build());
        reloj.adelantar(Duration.ofDays(1));
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("77").titulo("Desarrollador Senior (editado)")
                .build());

        List<OfertaResponse> ofertas = todas();
        assertThat(ofertas).hasSize(1);
        assertThat(ofertas.getFirst().titulo()).isEqualTo("Desarrollador Senior (editado)");
    }

    @Test
    void laMismaOfertaEnDosFuentesApareceDosVecesMarcadaComoPosibleRepetida() {
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("r1")
                .empresa("Acme S.A.").titulo("Desarrollador Angular Senior").build());
        leidas(Fuente.REMOTE_OK, oferta().fuente(Fuente.REMOTE_OK).id("k1")
                .empresa("ACME SA").titulo("Desarrollador angular, senior").build());
        leidas(Fuente.GET_ON_BOARD, oferta().fuente(Fuente.GET_ON_BOARD).id("g1")
                .empresa("Otra").titulo("Desarrollador Angular Senior").build());

        OfertaResponse remotive = unica("?fuente=REMOTIVE");
        OfertaResponse remoteOk = unica("?fuente=REMOTE_OK");
        assertThat(remotive.posiblesRepetidas()).containsExactly(
                new RepetidaResponse(remoteOk.id(), Fuente.REMOTE_OK, "Remote OK"));
        assertThat(remoteOk.posiblesRepetidas()).containsExactly(
                new RepetidaResponse(remotive.id(), Fuente.REMOTIVE, "Remotive"));
        assertThat(unica("?fuente=GET_ON_BOARD").posiblesRepetidas()).isEmpty();
        assertThat(detalle(remotive.id()).oferta().posiblesRepetidas()).hasSize(1);
    }

    @Test
    void cadaOfertaMuestraSuEncajeYSusMotivos() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("alta").build(), oferta().id("media").media().build(),
                oferta().id("fuera").sinTecnologias().build());

        List<OfertaResponse> ofertas = todas();
        assertThat(ofertas).extracting(OfertaResponse::encaje)
                .containsExactlyInAnyOrder(Encaje.ALTO, Encaje.MEDIO, Encaje.NO_ENCAJA);
        assertThat(ofertas).allSatisfy(o -> assertThat(o.motivos()).isNotEmpty());
        assertThat(unica("?encaje=NO_ENCAJA").motivos()).contains("No menciona ninguna tecnología del CV.");
    }

    @Test
    void laListaPorDefectoMuestraLasNuevasDeEncajeAltoYMedio() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("alta").build(), oferta().id("media").media().build(),
                oferta().id("fuera").sinTecnologias().build(), oferta().id("guardada").build());
        marcar(id("guardada"), "GUARDADA");

        assertThat(lista("")).extracting(OfertaResponse::url).containsExactlyInAnyOrder(
                "https://sitio.test/ofertas/alta", "https://sitio.test/ofertas/media");
        assertThat(lista("?encaje=NO_ENCAJA")).extracting(OfertaResponse::url)
                .containsExactly("https://sitio.test/ofertas/fuera");
    }

    @Test
    void laListaSeFiltraPorEncajeEstadoFuenteModalidadTecnologiaYFecha() {
        Instant dia10 = Instant.parse("2026-11-10T15:00:00Z");
        Instant dia12 = Instant.parse("2026-11-12T15:00:00Z");
        leidas(Fuente.GET_ON_BOARD,
                oferta().id("g-remota").publicadaEn(dia10).build(),
                oferta().id("g-hibrida").modalidad(Modalidad.HIBRIDO).ubicacion("Lima, Perú").admite(true)
                        .publicadaEn(dia12).descripcion("Flutter y Dart. Inglés técnico.").build());
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("r-media").media().publicadaEn(dia12).build());
        marcar(id("g-remota"), "POSTULADA");

        assertThat(urls("?encaje=MEDIO")).containsExactly("r-media");
        assertThat(urls("?estado=POSTULADA")).containsExactly("g-remota");
        assertThat(urls("?fuente=REMOTIVE")).containsExactly("r-media");
        assertThat(urls("?modalidad=HIBRIDO")).containsExactly("g-hibrida");
        assertThat(urls("?tecnologia=flutter")).containsExactly("g-hibrida");
        assertThat(urls("?tecnologia=Java")).containsExactly("r-media");
        assertThat(urls("?estado=NUEVA,POSTULADA&desde=2026-11-11")).containsExactlyInAnyOrder("g-hibrida", "r-media");
        assertThat(urls("?estado=NUEVA,POSTULADA&hasta=2026-11-10")).containsExactly("g-remota");
        assertThat(urls("?estado=NUEVA,POSTULADA&desde=2026-11-10&hasta=2026-11-10")).containsExactly("g-remota");
    }

    @Test
    void unFiltroConUnValorFueraDeCatalogoSeRechazaNombrandoLosValidos() {
        getIdentificado("/api/ofertas?encaje=EXCELENTE").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(String.class, detalle -> assertThat(detalle)
                        .contains("encaje", "EXCELENTE", "ALTO, MEDIO, NO_ENCAJA"));
        getIdentificado("/api/ofertas?desde=ayer").expectStatus().isBadRequest();
    }

    @Test
    void laPersonaMarcaUnaOfertaYLaPostuladaGuardaSuFecha() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("x").build());
        long id = id("x");

        assertThat(marcar(id, "GUARDADA").oferta().estado()).isEqualTo(EstadoOferta.GUARDADA);
        assertThat(marcar(id, "DESCARTADA").oferta().estado()).isEqualTo(EstadoOferta.DESCARTADA);
        Instant antes = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        OfertaResponse postulada = marcar(id, "POSTULADA").oferta();
        assertThat(postulada.estado()).isEqualTo(EstadoOferta.POSTULADA);
        assertThat(postulada.postuladaEn()).isAfterOrEqualTo(antes).isBeforeOrEqualTo(reloj.instant());

        reloj.adelantar(Duration.ofDays(3));
        assertThat(marcar(id, "POSTULADA").oferta().postuladaEn()).isEqualTo(postulada.postuladaEn());
        assertThat(marcar(id, "GUARDADA").oferta().postuladaEn()).isNull();
    }

    @Test
    void unEstadoNoPermitidoODesconocidoSeRechaza() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("x").build());
        long id = id("x");

        patchIdentificado("/api/ofertas/" + id, "{\"estado\":\"NUEVA\"}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("GUARDADA"));
        patchIdentificado("/api/ofertas/" + id, "{\"estado\":\"ARCHIVADA\"}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("POSTULADA"));
        patchIdentificado("/api/ofertas/" + id, "{}").expectStatus().isBadRequest();
        patchIdentificado("/api/ofertas/999999", "{\"estado\":\"GUARDADA\"}").expectStatus().isNotFound();
        getIdentificado("/api/ofertas/999999").expectStatus().isNotFound();
        assertThat(unica("").estado()).isEqualTo(EstadoOferta.NUEVA);
    }

    @Test
    void unaOfertaDescartadaNoRegresaAunqueLaFuenteLaSigaPublicando() {
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("d").build());
        marcar(id("d"), "DESCARTADA");

        reloj.adelantar(Duration.ofDays(1));
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("d").build());

        assertThat(lista("")).isEmpty();
        assertThat(unica("?estado=DESCARTADA").estado()).isEqualTo(EstadoOferta.DESCARTADA);
    }

    @Test
    void elDetalleTraeElTextoYElCorreoDeContacto() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("c").correo("postula@empresa-inventada.test").build(),
                oferta().id("s").build());

        OfertaDetalleResponse conCorreo = detalle(id("c"));
        assertThat(conCorreo.correoContacto()).isEqualTo("postula@empresa-inventada.test");
        assertThat(conCorreo.oferta().conCorreo()).isTrue();
        assertThat(conCorreo.descripcion()).contains("Angular");
        assertThat(detalle(id("s")).correoContacto()).isNull();
    }

    @Test
    void unaOfertaQueLaFuenteYaNoEntregaApareceComoYaNoPublicada() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("sigue").build(), oferta().id("retirada").build());
        reloj.adelantar(Duration.ofDays(1));
        leidasEnParte(Fuente.GET_ON_BOARD, oferta().id("sigue").build());
        assertThat(publicada("retirada")).as("una lectura parcial no retira nada").isTrue();

        leidas(Fuente.GET_ON_BOARD, oferta().id("sigue").build());

        assertThat(publicada("retirada")).isFalse();
        assertThat(publicada("sigue")).isTrue();
        reloj.adelantar(Duration.ofDays(1));
        leidas(Fuente.GET_ON_BOARD, oferta().id("retirada").build());
        assertThat(publicada("retirada")).as("si vuelve a publicarse, vuelve").isTrue();
    }

    @Test
    void nuevasYDescartadasSeEliminanA60DiasDeVistasGuardadasYPostuladasSeConservan() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("nueva").build(), oferta().id("descartada").build(),
                oferta().id("guardada").build(), oferta().id("postulada").build());
        marcar(id("descartada"), "DESCARTADA");
        marcar(id("guardada"), "GUARDADA");
        marcar(id("postulada"), "POSTULADA");
        for (Fuente fuente : Fuente.values()) {
            FUENTES.modo(fuente, FuentesDePrueba.Modo.BLOQUEA);
        }

        reloj.adelantar(Duration.ofDays(59));
        recoleccion.recoger();
        assertThat(todas()).hasSize(4);

        reloj.adelantar(Duration.ofDays(2));
        recoleccion.recoger();
        assertThat(todas()).extracting(OfertaResponse::url).containsExactlyInAnyOrder(
                "https://sitio.test/ofertas/guardada", "https://sitio.test/ofertas/postulada");
    }

    private long id(String idExterno) {
        return todas().stream().filter(o -> o.url().endsWith("/" + idExterno)).findFirst().orElseThrow().id();
    }

    private boolean publicada(String idExterno) {
        return detalle(id(idExterno)).oferta().publicada();
    }

    private List<String> urls(String consulta) {
        return lista(consulta).stream().map(o -> o.url().substring(o.url().lastIndexOf('/') + 1)).toList();
    }
}
