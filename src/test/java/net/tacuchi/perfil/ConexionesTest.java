package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Requisito;
import net.tacuchi.perfil.model.Via;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** La persona administra cada conexión desde el panel: su estado, y pausarla y reanudarla. Todo por HTTP. */
class ConexionesTest extends PruebaOfertas {

    private LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
    }

    private FuenteResponse pausar(Fuente fuente) {
        return postIdentificado("/api/fuentes/" + fuente.name() + "/pausar").expectStatus().isOk()
                .expectBody(FuenteResponse.class).returnResult().getResponseBody();
    }

    private FuenteResponse reanudar(Fuente fuente) {
        return postIdentificado("/api/fuentes/" + fuente.name() + "/reanudar").expectStatus().isOk()
                .expectBody(FuenteResponse.class).returnResult().getResponseBody();
    }

    @Test
    void cadaConexionDiceDesdeDondeSeLeeLoQueNecesitaYSiSePuedeLeer() {
        assertThat(fuentesDelServidor()).extracting(FuenteResponse::fuente)
                .containsExactly(Fuente.GET_ON_BOARD, Fuente.REMOTIVE, Fuente.REMOTE_OK);
        assertThat(fuentesDelServidor()).allSatisfy(f -> {
            assertThat(f.via()).isEqualTo(Via.SERVIDOR);
            assertThat(f.necesita()).isEqualTo(Requisito.NADA);
            assertThat(f.tieneLoQueNecesita()).isTrue();
            assertThat(f.pausada()).isFalse();
            assertThat(f.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
            assertThat(f.estado()).isEqualTo(EstadoLectura.SIN_LEER);
            assertThat(f.lecturasRestantes()).isEqualTo(Reglas.TOPE_LECTURAS_DIA);
        });
    }

    @Test
    void laRespuestaConservaLosCamposQueLeeElPanelDesplegado() {
        recoleccion.recoger();

        getIdentificado("/api/fuentes").expectStatus().isOk().expectBody()
                .jsonPath("$[1].fuente").isEqualTo("REMOTIVE")
                .jsonPath("$[1].nombre").isEqualTo("Remotive")
                .jsonPath("$[1].estado").isEqualTo("CORRECTA")
                .jsonPath("$[1].detalle").doesNotExist()
                .jsonPath("$[1].ultimaLectura").isNotEmpty()
                .jsonPath("$[1].ultimaCorrecta").isNotEmpty()
                .jsonPath("$[1].ofertasLeidas").value(n -> assertThat(((Number) n).intValue()).isPositive())
                .jsonPath("$[1].lecturasRestantes").isEqualTo(2)
                .jsonPath("$[1].via").isEqualTo("SERVIDOR")
                .jsonPath("$[1].necesita").isEqualTo("NADA")
                .jsonPath("$[1].estadoConexion").isEqualTo("LISTA");
    }

    @Test
    void pausarYReanudarCambianElEstadoDeLaConexion() {
        FuenteResponse pausada = pausar(Fuente.REMOTIVE);
        assertThat(pausada.pausada()).isTrue();
        assertThat(pausada.estadoConexion()).isEqualTo(EstadoConexion.PAUSADA);
        assertThat(fuente(Fuente.REMOTIVE).pausada()).isTrue();
        assertThat(fuente(Fuente.REMOTE_OK).pausada()).isFalse();

        FuenteResponse reanudada = reanudar(Fuente.REMOTIVE);
        assertThat(reanudada.pausada()).isFalse();
        assertThat(reanudada.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
    }

    @Test
    void pausarExigeElCodigoYVenirDelPanel() {
        cliente.post().uri("/api/fuentes/REMOTIVE/pausar").header("Origin", ORIGEN_PANEL).exchange()
                .expectStatus().isUnauthorized();
        cliente.post().uri("/api/fuentes/REMOTIVE/pausar")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro-sitio.test")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        cliente.post().uri("/api/fuentes/REMOTIVE/reanudar")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(fuente(Fuente.REMOTIVE).pausada()).isFalse();
    }

    @Test
    void laProgramadaOmiteLaPausadaConSuMotivoSinPedirleNada() {
        pausar(Fuente.REMOTIVE);

        recoleccion.recoger();

        RecoleccionResponse fin = avance();
        assertThat(fin.origen()).isEqualTo(OrigenRecoleccion.PROGRAMADA);
        assertThat(fin.fuentes()).extracting(f -> f.fuente()).containsExactly(Fuente.GET_ON_BOARD, Fuente.REMOTE_OK);
        assertThat(omitidasDelServidor(fin)).singleElement().satisfies(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.REMOTIVE);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.PAUSADA);
        });
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isZero();
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(1);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).as("no se contó").isEqualTo(3);
        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.SIN_LEER);
    }

    @Test
    void elGeneralOmiteLaPausadaConSuMotivoSinPedirleNada() {
        pausar(Fuente.REMOTE_OK);

        RecoleccionResponse aceptada = lanzar(null);

        assertThat(aceptada.total()).isEqualTo(2);
        assertThat(omitidasDelServidor(aceptada)).singleElement().satisfies(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.REMOTE_OK);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.PAUSADA);
        });
        RecoleccionResponse fin = esperarFin();
        assertThat(fin.estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(omitidasDelServidor(fin)).hasSize(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isZero();
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
    }

    @Test
    void lanzarUnaPausadaSeRechazaConSuMotivoYReanudadaSeLee() {
        pausar(Fuente.REMOTIVE);
        RecoleccionResponse antes = avance();

        postIdentificado("/api/recoleccion/REMOTIVE").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("PAUSADA")
                .jsonPath("$.detail").value(d -> assertThat((String) d).contains("Remotive"));
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isZero();
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(3);
        assertThat(avance()).as("el rechazo no lanza ni cambia nada").isEqualTo(antes);

        reanudar(Fuente.REMOTIVE);
        RecoleccionResponse fin = leerADemanda(Fuente.REMOTIVE);
        assertThat(enAvance(fin, Fuente.REMOTIVE).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).isEqualTo(1);
    }

    @Test
    void conTodasPausadasElGeneralSeRechazaSinPedirNada() {
        for (Fuente fuente : Fuente.values()) {
            pausar(fuente);
        }
        RecoleccionResponse antes = avance();

        postIdentificado("/api/recoleccion").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("SIN_FUENTES")
                .jsonPath("$.detail").value(d -> assertThat((String) d).contains("pausadas"));
        recoleccion.recoger();

        for (Fuente fuente : Fuente.values()) {
            if (!fuente.desdeMaquina()) {
                assertThat(FUENTES.lecturas(fuente)).as(fuente.nombre()).isZero();
            }
        }
        assertThat(avance()).as("ni el general ni la programada lanzaron nada").isEqualTo(antes);
    }

    @Test
    void unaConexionDesconocidaSeRechazaConLosValoresValidos() {
        postIdentificado("/api/fuentes/OTRA/pausar").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(d -> assertThat((String) d).contains("REMOTIVE"));
    }
}
