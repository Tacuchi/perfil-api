package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import net.tacuchi.perfil.dto.*;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.soporte.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "perfil.computrabajo.plazo-corrida=1s")
class LinkedinApifyTest extends PruebaApify {
    private static final Fuente FUENTE = Fuente.LINKEDIN;
    private static final String ACTOR = ApifyDePrueba.LINKEDIN;

    @Test void leer_consultaLosTresTerminosEnAmbosAmbitosConCuotasYCosto() {
        clave();
        var fin = enAvance(leerADemanda(FUENTE), FUENTE);
        assertThat(fin.resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(fin.ofertasLeidas()).isEqualTo(2);
        // Dos avisos por corrida en la muestra: doce avisos cobrados, aunque solo haya dos distintos.
        assertThat(fin.costoUsd()).isEqualByComparingTo("0.0108");
        assertThat(fuente(FUENTE).consumoMes()).isEqualByComparingTo("0.0108");
        var entradas = APIFY.peticiones().stream().filter(x -> x.metodo().equals("POST")).toList();
        assertThat(entradas).hasSize(6).allSatisfy(x -> {
            assertThat(x.conClave()).isTrue();
            assertThat(x.ruta()).isEqualTo("/v2/acts/" + ACTOR + "/runs");
            assertThat(x.cuerpo().path("datePosted").asString()).isEqualTo("r604800");
            assertThat(x.cuerpo().path("workType").isMissingNode()).isTrue();
        });
        assertThat(entradas).extracting(x -> x.cuerpo().path("keywords").asString())
                .containsExactly("desarrollador de software", "programador", "full stack",
                        "desarrollador de software", "programador", "full stack");
        assertThat(entradas).extracting(x -> x.cuerpo().path("limit").asInt()).containsExactly(9,9,7,9,9,7);
        assertThat(entradas).extracting(x -> x.parametros().get("maxTotalChargeUsd"))
                .containsExactly("0.0046", "0.0046", "0.0038", "0.0046", "0.0046", "0.0038");
        assertThat(entradas.subList(0,3)).allSatisfy(x -> assertThat(x.cuerpo().path("location").asString()).isEqualTo("Peru"));
        assertThat(entradas.subList(3,6)).allSatisfy(x -> {
            assertThat(x.cuerpo().path("location").asString()).isEqualTo("Latin America");
            assertThat(x.cuerpo().path("urlParam").get(0).path("value").asString()).isEqualTo("2");
        });
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=LINKEDIN")).hasSize(2).allSatisfy(x -> {
            assertThat(x.modalidad()).isEqualTo(Modalidad.REMOTO);
            assertThat(x.empresa()).isEqualTo("Empresa de prueba");
            assertThat(x.url()).startsWith("https://pe.linkedin.com/jobs/view/");
            assertThat(detalle(x.id()).descripcion()).contains("Java y Spring Boot");
        });
    }

    @ParameterizedTest @EnumSource(value = ApifyDePrueba.Modo.class, names = {"FALLA", "VACIA", "HTTP401", "HTTP429", "JSON_INVALIDO"})
    void unaBusquedaFallida_conservaAvisosCargosYMotivoDelAmbito(ApifyDePrueba.Modo modo) {
        clave();
        APIFY.modo(ACTOR, "Latin America", "*", modo);
        var fin = enAvance(leerADemanda(FUENTE), FUENTE);
        assertThat(fin.resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(fin.ofertasLeidas()).isEqualTo(2);
        assertThat(fin.detalle()).contains("Remoto para Latinoamérica", "desarrollador de software");
        assertThat(fuente(FUENTE).detalle()).isEqualTo(fin.detalle());
        getIdentificado("/api/fuentes/LINKEDIN/historial").expectStatus().isOk().expectBody()
                .jsonPath("$.lecturas[0].parcial").isEqualTo(true)
                .jsonPath("$.lecturas[0].motivo").isEqualTo(fin.detalle())
                .jsonPath("$.lecturas[0].costoUsd").isEqualTo(fin.costoUsd().doubleValue());
        assertThat(fin.costoUsd()).isGreaterThanOrEqualTo(new BigDecimal("0.0054"));
        if (modo == ApifyDePrueba.Modo.FALLA || modo == ApifyDePrueba.Modo.VACIA) {
            assertThat(fin.costoUsd()).isEqualByComparingTo("0.0084");
        }
    }

    @Test void falloRemoto_identificaLecturaParcialYAgrupaElMotivoPorAmbito() {
        clave();
        APIFY.modo(ACTOR, "Latin America", "*", ApifyDePrueba.Modo.FALLA);
        leerADemanda(FUENTE);
        getIdentificado("/api/recoleccion").expectStatus().isOk().expectBody()
                .jsonPath("$.fuentes[0].parcial").isEqualTo(true)
                .jsonPath("$.fuentes[0].detalle").isEqualTo("Remoto para Latinoamérica: fallaron las 3 búsquedas "
                        + "(desarrollador de software, programador, full stack): el servicio terminó con fallo");
        getIdentificado("/api/fuentes").expectStatus().isOk().expectBody()
                .jsonPath("$[3].parcial").isEqualTo(true);
    }

    @Test void costoIncierto_cuentaAutorizacionYNoLanzaOtraCorrida() {
        clave(); APIFY.modo(ApifyDePrueba.Modo.SIN_COSTO);
        var fin = enAvance(leerADemanda(FUENTE), FUENTE);
        assertThat(APIFY.lanzamientos(ACTOR)).isEqualTo(1);
        assertThat(fin.costoUsd()).isEqualByComparingTo("0.0046");
        assertThat(fin.detalle()).contains("costo incierto", "omitida");
    }

    @Test void plazoVencido_cuentaAutorizacionYNoPermiteMasGasto() {
        clave(); APIFY.modo(ApifyDePrueba.Modo.NO_TERMINA);
        lanzar(FUENTE);
        postIdentificado("/api/recoleccion/LINKEDIN").expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.motivo").isEqualTo("EN_CURSO");
        var fin = enAvance(esperarFin(), FUENTE);
        assertThat(fin.resultado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(fin.detalle()).contains("no terminó a tiempo", "costo incierto");
        assertThat(fin.costoUsd()).isEqualByComparingTo("0.0046");
        assertThat(APIFY.lanzamientos(ACTOR)).isEqualTo(1);
    }

    @Test void saldoParaPocosAvisos_noSuperaTopeNiRepiteLanzamientos() {
        clave(); tope(FUENTE, "0.01");
        var fin = enAvance(leerADemanda(FUENTE), FUENTE);
        BigDecimal autorizado = APIFY.peticiones().stream().filter(x -> x.metodo().equals("POST"))
                .map(x -> new BigDecimal(x.parametros().get("maxTotalChargeUsd"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(fin.costoUsd()).isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(fin.detalle()).contains("saldo insuficiente", "gastado", "tope del mes");
        var antes = APIFY.lanzamientos(ACTOR);
        leerADemanda(FUENTE);
        assertThat(APIFY.lanzamientos(ACTOR)).isEqualTo(antes);
        assertThat(autorizado).isPositive();
    }

    @Test void lecturaAcotada_conservaAvisosHistoricosEstadoYPostulacion() {
        clave();
        leidas(Fuente.LINKEDIN, Ofertas.oferta().fuente(Fuente.LINKEDIN).id("4487010792").build(),
                Ofertas.oferta().fuente(Fuente.LINKEDIN).id("1000000000").build());
        reloj.adelantar(Duration.ofMinutes(1));
        leerADemanda(FUENTE);
        var primera = lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=LINKEDIN").getFirst();
        marcar(primera.id(), "POSTULADA");
        var antes = detalle(primera.id());
        reloj.adelantar(Duration.ofHours(1));
        leerADemanda(FUENTE);
        var despues = detalle(primera.id());
        assertThat(despues.oferta().estado()).isEqualTo(EstadoOferta.POSTULADA);
        assertThat(despues.oferta().postuladaEn()).isEqualTo(antes.oferta().postuladaEn());
        assertThat(despues.oferta().fecha()).isEqualTo(antes.oferta().fecha());
        assertThat(todas()).hasSize(3).allSatisfy(x -> assertThat(x.publicada()).isTrue());
    }

    @Test void topeDeLinkedin_noFrenaLaLecturaGeneralYElRechazoDiceImportes() {
        clave();
        jdbc.sql("INSERT INTO consumo_mes VALUES ('LINKEDIN', :mes, 1)")
                .param("mes", net.tacuchi.perfil.repository.ConsumoRepository.mesDe(reloj.instant())).update();
        postIdentificado("/api/recoleccion/LINKEDIN").expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.detail").value(x -> assertThat((String)x).contains("gastado 1", "tope 1"));
        var fin = leerADemanda(null);
        assertThat(fin.fuentes()).hasSize(5);
        assertThat(APIFY.lanzamientos(ACTOR)).isZero();
        getIdentificado("/api/fuentes/LINKEDIN/historial").expectStatus().isOk().expectBody()
                .jsonPath("$.lecturas").isEmpty();
        assertThat(APIFY.lanzamientos(ApifyDePrueba.BUMERAN)).isEqualTo(3);
        assertThat(APIFY.lanzamientos(ApifyDePrueba.ACTOR)).isEqualTo(1);
        assertThat(fin.omitidas()).singleElement().satisfies(x -> {
            assertThat(x.gastado()).isEqualByComparingTo("1");
            assertThat(x.topeMensual()).isEqualByComparingTo("1");
        });
    }
}
