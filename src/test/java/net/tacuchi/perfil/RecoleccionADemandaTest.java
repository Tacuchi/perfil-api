package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.PasoFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** La persona lanza la recolección desde el panel y sigue su avance, todo por HTTP contra los sitios de prueba. */
class RecoleccionADemandaTest extends PruebaOfertas {

    private LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
    }

    private void rechazado(String ruta, String motivo) {
        postIdentificado(ruta).expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo(motivo)
                .jsonPath("$.detail").isNotEmpty();
    }

    @Test
    void elLanzamientoGeneralRespondeAlInstanteYLeeCadaFuenteUnaVez() {
        RecoleccionResponse aceptada = lanzar(null);

        assertThat(aceptada.estado()).isEqualTo(EstadoRecoleccion.EN_CURSO);
        assertThat(aceptada.origen()).isEqualTo(OrigenRecoleccion.DEMANDA);
        assertThat(aceptada.total()).isEqualTo(3);
        assertThat(omitidasDelServidor(aceptada)).isEmpty();
        assertThat(aceptada.fuentes()).extracting(f -> f.fuente())
                .containsExactly(Fuente.GET_ON_BOARD, Fuente.REMOTIVE, Fuente.REMOTE_OK);

        RecoleccionResponse fin = esperarFin();

        assertThat(fin.estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(fin.terminadas()).isEqualTo(3);
        assertThat(fin.fin()).isNotNull();
        assertThat(fin.fuentes()).allSatisfy(f -> {
            assertThat(f.paso()).isEqualTo(PasoFuente.TERMINADA);
            assertThat(f.resultado()).isEqualTo(EstadoLectura.CORRECTA);
            assertThat(f.ofertasLeidas()).isPositive();
        });
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD)).as("una página por categoría").isEqualTo(4);
        assertThat(todas()).isNotEmpty();
        assertThat(fuentesDelServidor()).allSatisfy(f -> assertThat(f.lecturasRestantes()).isEqualTo(2));
    }

    @Test
    void elLanzamientoDeUnaFuenteLeeSoloEsa() {
        RecoleccionResponse aceptada = lanzar(Fuente.REMOTE_OK);
        assertThat(aceptada.total()).isEqualTo(1);

        RecoleccionResponse fin = esperarFin();

        assertThat(enAvance(fin, Fuente.REMOTE_OK).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isZero();
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD)).isZero();
    }

    @Test
    void aMitadDeLaLecturaElAvanceMuestraCadaPasoYUnaSegundaConsultaTraeLoMismo() {
        FUENTES.pausar(Fuente.REMOTIVE);
        lanzar(null);

        RecoleccionResponse aMitad = esperarAvance(
                a -> a.estado() == EstadoRecoleccion.EN_CURSO
                        && enAvance(a, Fuente.REMOTIVE).paso() == PasoFuente.LEYENDO,
                "Remotive leyendo");

        assertThat(aMitad.terminadas()).isEqualTo(1);
        assertThat(aMitad.total()).isEqualTo(3);
        assertThat(enAvance(aMitad, Fuente.GET_ON_BOARD).paso()).isEqualTo(PasoFuente.TERMINADA);
        assertThat(enAvance(aMitad, Fuente.REMOTE_OK).paso()).isEqualTo(PasoFuente.PENDIENTE);
        assertThat(enAvance(aMitad, Fuente.REMOTIVE).resultado()).isNull();
        assertThat(avance()).as("otra pestaña ve el mismo avance").isEqualTo(aMitad);

        FUENTES.soltar(Fuente.REMOTIVE);
        RecoleccionResponse fin = esperarFin();
        assertThat(fin.terminadas()).isEqualTo(3);
        assertThat(avance()).as("al volver, el resultado").isEqualTo(fin);
    }

    @Test
    void alTerminarCadaFuenteMuestraSiSeLeyoFalloOBloqueo() {
        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.FALLA);
        FUENTES.modo(Fuente.REMOTE_OK, FuentesDePrueba.Modo.BLOQUEA);
        lanzar(null);

        RecoleccionResponse fin = esperarFin();

        assertThat(enAvance(fin, Fuente.GET_ON_BOARD).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(enAvance(fin, Fuente.GET_ON_BOARD).ofertasLeidas()).isPositive();
        assertThat(enAvance(fin, Fuente.REMOTIVE).resultado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(enAvance(fin, Fuente.REMOTIVE).ofertasLeidas()).isZero();
        assertThat(enAvance(fin, Fuente.REMOTE_OK).resultado()).isEqualTo(EstadoLectura.BLOQUEO);
        assertThat(fin.terminadas()).isEqualTo(3);
    }

    @Test
    void conUnaADemandaEnCursoNoSeLanzaOtra() {
        FUENTES.pausar(Fuente.REMOTIVE);
        lanzar(Fuente.REMOTIVE);
        esperarAvance(a -> enAvance(a, Fuente.REMOTIVE).paso() == PasoFuente.LEYENDO, "Remotive leyendo");

        rechazado("/api/recoleccion", "EN_CURSO");
        rechazado("/api/recoleccion/REMOTE_OK", "EN_CURSO");

        FUENTES.soltar(Fuente.REMOTIVE);
        esperarFin();
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isZero();
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
    }

    @Test
    void conUnaProgramadaEnCursoNoSeLanzaOtraYElPanelLaVe() throws InterruptedException {
        FUENTES.pausar(Fuente.REMOTIVE);
        Thread programada = Thread.ofVirtual().start(recoleccion::recoger);

        RecoleccionResponse enCurso = esperarAvance(
                a -> a.origen() == OrigenRecoleccion.PROGRAMADA && a.estado() == EstadoRecoleccion.EN_CURSO
                        && enAvance(a, Fuente.REMOTIVE).paso() == PasoFuente.LEYENDO,
                "programada leyendo Remotive");
        assertThat(enCurso.total()).isEqualTo(3);

        rechazado("/api/recoleccion", "EN_CURSO");
        rechazado("/api/recoleccion/GET_ON_BOARD", "EN_CURSO");

        FUENTES.soltar(Fuente.REMOTIVE);
        programada.join();
        assertThat(avance().estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD)).isEqualTo(4);
    }

    @Test
    void unaFuenteEnSuTopeSeRechazaYElGeneralLaOmite() {
        recoleccion.recoger();
        leerADemanda(Fuente.REMOTIVE);
        leerADemanda(Fuente.REMOTIVE);

        rechazado("/api/recoleccion/REMOTIVE", "TOPE_DIARIO");
        RecoleccionResponse aceptada = lanzar(null);

        assertThat(aceptada.total()).isEqualTo(2);
        assertThat(aceptada.fuentes()).extracting(f -> f.fuente())
                .containsExactly(Fuente.GET_ON_BOARD, Fuente.REMOTE_OK);
        assertThat(omitidasDelServidor(aceptada)).singleElement().satisfies(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.REMOTIVE);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.TOPE_DIARIO);
        });
        RecoleccionResponse fin = esperarFin();
        assertThat(fin.terminadas()).isEqualTo(2);
        assertThat(omitidasDelServidor(fin)).hasSize(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).isEqualTo(3);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isZero();
    }

    @Test
    void sinFuentesDisponiblesElGeneralSeRechaza() {
        for (int i = 0; i < Reglas.TOPE_LECTURAS_DIA; i++) {
            leerADemanda(null);
        }

        rechazado("/api/recoleccion", "SIN_FUENTES");
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK, hoy())).isEqualTo(3);
    }

    @Test
    void unaFuenteQueFalloHoySeVuelveALeerConSuBoton() {
        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.FALLA);
        recoleccion.recoger();
        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.FALLO);

        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.BIEN);
        RecoleccionResponse fin = leerADemanda(Fuente.REMOTIVE);

        assertThat(enAvance(fin, Fuente.REMOTIVE).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(2);
    }

    @Test
    void unaProgramadaSinNadaQueLeerNoReemplazaElUltimoResultado() {
        RecoleccionResponse fin = leerADemanda(null);

        recoleccion.recoger();

        assertThat(avance()).isEqualTo(fin);
    }

    @Test
    void lanzarExigeElCodigoYVenirDelPanel() {
        cliente.post().uri("/api/recoleccion").header("Origin", ORIGEN_PANEL).exchange()
                .expectStatus().isUnauthorized();
        cliente.post().uri("/api/recoleccion")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro-sitio.test")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        cliente.post().uri("/api/recoleccion/REMOTIVE")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        cliente.get().uri("/api/recoleccion").exchange().expectStatus().isUnauthorized();

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isZero();
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(3);
    }

    @Test
    void unaFuenteDesconocidaOUnMetodoNoAdmitidoDicenQueFallo() {
        postIdentificado("/api/recoleccion/OTRA").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(d -> assertThat((String) d).contains("REMOTIVE"));
        cliente.delete().uri("/api/recoleccion")
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
