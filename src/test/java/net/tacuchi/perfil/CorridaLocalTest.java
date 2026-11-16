package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.tacuchi.perfil.dto.CorridaAbiertaResponse;
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaEntregada;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.FalloMaquina;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoCierre;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.PasoFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Requisito;
import net.tacuchi.perfil.model.Via;
import net.tacuchi.perfil.soporte.AccessDePrueba;
import net.tacuchi.perfil.soporte.EntregasDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Corrida local por HTTP: la persona la abre desde el panel y la prueba hace de lector, con el código firmado como
 * token de servicio de Access (con su identificador y sin correo).
 */
class CorridaLocalTest extends PruebaOfertas {

    private final List<String> respuestas = new ArrayList<>();

    private LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
    }

    private CorridaAbiertaResponse abrir() {
        CorridaAbiertaResponse abierta = postIdentificado("/api/recoleccion/maquina").expectStatus().isAccepted()
                .expectBody(CorridaAbiertaResponse.class).returnResult().getResponseBody();
        return abierta;
    }

    private RestTestClient.ResponseSpec lector(String ruta, Object cuerpo, String access) {
        RestTestClient.RequestBodySpec peticion = cliente.post().uri("/api/lector/corrida" + ruta)
                .contentType(MediaType.APPLICATION_JSON);
        if (access != null) {
            peticion.header(CABECERA_ACCESS, access);
        }
        return peticion.body(cuerpo).exchange();
    }

    private static Object codigo(String codigo) {
        return java.util.Map.of("codigo", codigo);
    }

    private CorridaLectorResponse iniciar(String codigo) {
        return guardar(lector("/iniciar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isOk()
                .expectBody(CorridaLectorResponse.class).returnResult().getResponseBody());
    }

    private void empezar(String codigo, Fuente fuente) {
        lector("/fuentes/" + fuente.name() + "/empezar", codigo(codigo), ACCESS.codigoDelLector())
                .expectStatus().isNoContent();
    }

    private RestTestClient.ResponseSpec entregar(String codigo, Fuente fuente, List<OfertaEntregada> avisos,
            FalloMaquina fallo, String access) {
        return lector("/fuentes/" + fuente.name() + "/entregar", new EntregaRequest(codigo, avisos, fallo), access);
    }

    private FuenteEnRecoleccion entregar(String codigo, List<OfertaEntregada> avisos) {
        return guardar(entregar(codigo, Fuente.LINKEDIN, avisos, null, ACCESS.codigoDelLector()).expectStatus().isOk()
                .expectBody(FuenteEnRecoleccion.class).returnResult().getResponseBody());
    }

    private FuenteEnRecoleccion fallar(String codigo, FalloMaquina fallo) {
        return guardar(entregar(codigo, Fuente.LINKEDIN, null, fallo, ACCESS.codigoDelLector()).expectStatus().isOk()
                .expectBody(FuenteEnRecoleccion.class).returnResult().getResponseBody());
    }

    private RecoleccionResponse terminar(String codigo) {
        return guardar(lector("/terminar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isOk()
                .expectBody(RecoleccionResponse.class).returnResult().getResponseBody());
    }

    /** Abre, inicia y empieza LinkedIn: queda lista para entregar. */
    private String abiertaLeyendo() {
        String codigo = abrir().codigo();
        iniciar(codigo);
        empezar(codigo, Fuente.LINKEDIN);
        return codigo;
    }

    /** Una corrida completa que entrega esos avisos de LinkedIn. */
    private RecoleccionResponse leerLinkedIn(List<OfertaEntregada> avisos) {
        String codigo = abiertaLeyendo();
        entregar(codigo, avisos);
        return terminar(codigo);
    }

    private RecoleccionResponse leerLinkedInConFallo(FalloMaquina fallo) {
        String codigo = abiertaLeyendo();
        fallar(codigo, fallo);
        return terminar(codigo);
    }

    private <T> T guardar(T respuesta) {
        respuestas.add(String.valueOf(respuesta));
        return respuesta;
    }

    private List<OfertaResponse> deLinkedIn() {
        return lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=LINKEDIN");
    }

    private void reanudar(Fuente fuente) {
        postIdentificado("/api/fuentes/" + fuente.name() + "/reanudar").expectStatus().isOk();
    }

    /** Estas pruebas son de LinkedIn: Bumeran, la otra conexión de la máquina, queda pausada y fuera de sus corridas. */
    @BeforeEach
    void soloLinkedIn() {
        postIdentificado("/api/fuentes/" + Fuente.BUMERAN.name() + "/pausar").expectStatus().isOk();
    }

    @Test
    void linkedInSeLeeDesdeLaMaquinaConSesionUnaLecturaAlDiaY25Avisos() {
        FuenteResponse linkedIn = fuente(Fuente.LINKEDIN);

        assertThat(linkedIn.via()).isEqualTo(Via.MAQUINA);
        assertThat(linkedIn.necesita()).isEqualTo(Requisito.SESION);
        assertThat(linkedIn.tieneLoQueNecesita()).isFalse();
        assertThat(linkedIn.estadoConexion()).as("sin ninguna lectura correcta").isEqualTo(EstadoConexion.FALTA_INICIAR_SESION);
        assertThat(linkedIn.lecturasDia()).isEqualTo(1);
        assertThat(linkedIn.lecturasRestantes()).isEqualTo(1);
        assertThat(linkedIn.avisosPorCorrida()).isEqualTo(Reglas.TOPE_AVISOS_ENTREGA);
        assertThat(fuente(Fuente.REMOTIVE).avisosPorCorrida()).isNull();
    }

    @Test
    void unaCorridaCompletaEntregaOfertasClasificadasConSuAvanceYSueltaElCandado() {
        CorridaAbiertaResponse abierta = abrir();

        assertThat(abierta.codigo()).matches("[A-Za-z0-9_-]{43}");
        assertThat(abierta.avance().origen()).isEqualTo(OrigenRecoleccion.MAQUINA);
        assertThat(abierta.avance().fuentes()).extracting(FuenteEnRecoleccion::fuente).containsExactly(Fuente.LINKEDIN);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).as("abrir no gasta la lectura").isEqualTo(1);
        postIdentificado("/api/recoleccion").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("EN_CURSO");

        CorridaLectorResponse aLeer = iniciar(abierta.codigo());
        assertThat(aLeer.fuentes()).singleElement().satisfies(f -> {
            assertThat(f.fuente()).isEqualTo(Fuente.LINKEDIN);
            assertThat(f.avisosMaximos()).isEqualTo(25);
        });
        empezar(abierta.codigo(), Fuente.LINKEDIN);
        assertThat(enAvance(avance(), Fuente.LINKEDIN).paso()).isEqualTo(PasoFuente.LEYENDO);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).as("empezar gasta la lectura").isZero();

        FuenteEnRecoleccion entregada = entregar(abierta.codigo(), EntregasDePrueba.avisos("a", 3, reloj.instant()));
        assertThat(entregada.resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(entregada.ofertasLeidas()).isEqualTo(3);
        RecoleccionResponse fin = terminar(abierta.codigo());

        assertThat(fin.estado()).isEqualTo(EstadoRecoleccion.TERMINADA);
        assertThat(fin.terminadas()).isEqualTo(1);
        assertThat(fin.cierre()).isNull();
        assertThat(avance()).isEqualTo(fin);
        assertThat(deLinkedIn()).hasSize(3).allSatisfy(o -> {
            assertThat(o.fuenteNombre()).isEqualTo("LinkedIn");
            assertThat(o.motivos()).isNotEmpty();
            assertThat(o.url()).startsWith("https://www.linkedin.com/jobs/view/");
            assertThat(o.publicada()).isTrue();
        });
        FuenteResponse linkedIn = fuente(Fuente.LINKEDIN);
        assertThat(linkedIn.estado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(linkedIn.ofertasLeidas()).isEqualTo(3);
        assertThat(linkedIn.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
        assertThat(linkedIn.tieneLoQueNecesita()).isTrue();
        assertThat(lanzar(null).estado()).as("el candado quedó suelto").isEqualTo(EstadoRecoleccion.EN_CURSO);
    }

    @Test
    void sinLaIdentidadDelLectorOSinUnaCorridaAbiertaLaEntregaSeRechaza() {
        String codigo = abiertaLeyendo();
        List<OfertaEntregada> avisos = EntregasDePrueba.avisos("x", 2, reloj.instant());
        List<Supplier<String>> identidadesAjenas = List.of(
                () -> null,
                ACCESS::codigoValido,
                () -> ACCESS.codigoDeServicio("otro-token.access"),
                ACCESS::codigoDelLectorConCorreo,
                ACCESS::codigoDelLectorVencido,
                ACCESS::codigoDelLectorConFirmaAjena);

        for (Supplier<String> identidad : identidadesAjenas) {
            entregar(codigo, Fuente.LINKEDIN, avisos, null, identidad.get()).expectStatus().isUnauthorized();
        }
        entregar("A".repeat(43), Fuente.LINKEDIN, avisos, null, ACCESS.codigoDelLector())
                .expectStatus().isForbidden().expectBody().jsonPath("$.motivo").isEqualTo("CODIGO");
        entregar(codigo, Fuente.REMOTIVE, avisos, null, ACCESS.codigoDelLector())
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
        assertThat(deLinkedIn()).isEmpty();

        terminar(codigo);
        entregar(codigo, Fuente.LINKEDIN, avisos, null, ACCESS.codigoDelLector())
                .expectStatus().isForbidden();
        lector("/iniciar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isForbidden();

        reloj.adelantar(Duration.ofDays(1));
        String vencida = abrir().codigo();
        iniciar(vencida);
        reloj.adelantar(Reglas.PLAZO_CORRIDA_LOCAL);
        lector("/fuentes/LINKEDIN/empezar", codigo(vencida), ACCESS.codigoDelLector()).expectStatus().isForbidden();
        assertThat(deLinkedIn()).isEmpty();
    }

    @Test
    void laPersonaNoUsaLasRutasDelLectorNiElLectorLasDelPanel() {
        String codigo = abrir().codigo();

        lector("/iniciar", codigo(codigo), ACCESS.codigoValido()).expectStatus().isUnauthorized();
        cliente.get().uri("/api/fuentes").header(CABECERA_ACCESS, ACCESS.codigoDelLector()).exchange()
                .expectStatus().isUnauthorized();
        cliente.post().uri("/api/recoleccion/maquina").header(CABECERA_ACCESS, ACCESS.codigoDelLector())
                .header("Origin", ORIGEN_PANEL).exchange().expectStatus().isUnauthorized();
        cliente.post().uri("/api/fuentes/LINKEDIN/reanudar").header(CABECERA_ACCESS, ACCESS.codigoDelLector())
                .header("Origin", ORIGEN_PANEL).exchange().expectStatus().isUnauthorized();
        iniciar(codigo);
    }

    @Test
    void abrirLaCorridaExigeElCodigoYVenirDelPanel() {
        RecoleccionResponse antes = avance();
        cliente.post().uri("/api/recoleccion/maquina").header("Origin", ORIGEN_PANEL).exchange()
                .expectStatus().isUnauthorized();
        cliente.post().uri("/api/recoleccion/maquina").header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro-sitio.test").exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(avance()).as("no se abrió nada").isEqualTo(antes);
    }

    @Test
    void unLectorQueNoSeReportaCierraLaCorridaSinGastarLaLecturaYOtraCorridaLeeElMismoDia() {
        String codigo = abrir().codigo();

        reloj.adelantar(Reglas.PLAZO_REPORTE_LECTOR);
        RecoleccionResponse cerrada = esperarFin();

        assertThat(cerrada.cierre()).isEqualTo(MotivoCierre.LECTOR_SIN_RESPUESTA);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).isEqualTo(1);
        lector("/iniciar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isForbidden();
        assertThat(lanzar(Fuente.REMOTIVE).origen()).as("el candado quedó suelto").isEqualTo(OrigenRecoleccion.DEMANDA);
        esperarFin();

        RecoleccionResponse fin = leerLinkedIn(EntregasDePrueba.avisos("b", 2, reloj.instant()));
        assertThat(enAvance(fin, Fuente.LINKEDIN).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(deLinkedIn()).hasSize(2);
    }

    @Test
    void faltaDeSesionYCorridaVencidaAntesDeLeerNoGastanLaLecturaVerificacionSi() {
        RecoleccionResponse sinSesion = leerLinkedInConFallo(FalloMaquina.SIN_SESION);

        assertThat(enAvance(sinSesion, Fuente.LINKEDIN).resultado()).isEqualTo(EstadoLectura.BLOQUEO);
        FuenteResponse linkedIn = fuente(Fuente.LINKEDIN);
        assertThat(linkedIn.estadoConexion()).isEqualTo(EstadoConexion.FALTA_INICIAR_SESION);
        assertThat(linkedIn.detalle()).isEqualTo("falta iniciar sesión");
        assertThat(linkedIn.lecturasRestantes()).isEqualTo(1);

        String vencida = abrir().codigo();
        iniciar(vencida);
        reloj.adelantar(Reglas.PLAZO_CORRIDA_LOCAL);
        assertThat(esperarFin().cierre()).isEqualTo(MotivoCierre.PLAZO_VENCIDO);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).isEqualTo(1);

        leerLinkedInConFallo(FalloMaquina.VERIFICACION);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).isZero();
    }

    @Test
    void verificacionYRitmoPausanLaConexionHastaReanudarlaYElRitmoGastaLaLectura() {
        leerLinkedIn(EntregasDePrueba.avisos("c", 1, reloj.instant()));
        reloj.adelantar(Duration.ofDays(1));

        leerLinkedInConFallo(FalloMaquina.VERIFICACION);

        FuenteResponse pausada = fuente(Fuente.LINKEDIN);
        assertThat(pausada.estadoConexion()).isEqualTo(EstadoConexion.PAUSADA);
        assertThat(pausada.motivoPausa()).isEqualTo("el sitio pidió una verificación");
        assertThat(pausada.estado()).isEqualTo(EstadoLectura.BLOQUEO);
        reloj.adelantar(Duration.ofDays(1));
        postIdentificado("/api/recoleccion/maquina").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("SIN_FUENTES");

        reanudar(Fuente.LINKEDIN);
        assertThat(fuente(Fuente.LINKEDIN).estadoConexion()).isEqualTo(EstadoConexion.PENDIENTE_MAQUINA);
        leerLinkedInConFallo(FalloMaquina.RITMO);

        FuenteResponse porRitmo = fuente(Fuente.LINKEDIN);
        assertThat(porRitmo.pausada()).isTrue();
        assertThat(porRitmo.motivoPausa()).isEqualTo("el sitio limitó el ritmo de lectura");
        assertThat(porRitmo.lecturasRestantes()).isZero();
    }

    @Test
    void unaConexionPausadaDuranteLaCorridaNoSeLee() {
        String codigo = abrir().codigo();
        iniciar(codigo);
        postIdentificado("/api/fuentes/LINKEDIN/pausar").expectStatus().isOk();

        lector("/fuentes/LINKEDIN/empezar", codigo(codigo), ACCESS.codigoDelLector())
                .expectStatus().isEqualTo(HttpStatus.CONFLICT).expectBody().jsonPath("$.motivo").isEqualTo("PAUSADA");

        assertThat(avance().omitidas()).filteredOn(o -> o.fuente() != Fuente.BUMERAN).singleElement().satisfies(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.LINKEDIN);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.PAUSADA);
        });
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).isEqualTo(1);
        terminar(codigo);
    }

    @Test
    void unaLecturaSinRespuestasEsUnFalloNoUnDiaSinOfertas() {
        String codigo = abiertaLeyendo();

        FuenteEnRecoleccion vacia = entregar(codigo, List.of());
        terminar(codigo);

        assertThat(vacia.resultado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(fuente(Fuente.LINKEDIN).estado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(fuente(Fuente.LINKEDIN).detalle()).isEqualTo("la lectura no trajo respuestas");
    }

    @Test
    void linkedInSeLeeUnaVezAlDiaYCadaEntregaAdmiteHasta25Avisos() {
        String codigo = abiertaLeyendo();

        entregar(codigo, Fuente.LINKEDIN, EntregasDePrueba.avisos("d", 26, reloj.instant()), null,
                ACCESS.codigoDelLector()).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(d -> assertThat((String) d).contains("25"));
        entregar(codigo, EntregasDePrueba.avisos("d", 25, reloj.instant()));
        terminar(codigo);

        assertThat(deLinkedIn()).hasSize(25);
        postIdentificado("/api/recoleccion/maquina").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("SIN_FUENTES");
        reloj.adelantar(Duration.ofDays(1));
        assertThat(abrir().avance().fuentes()).extracting(FuenteEnRecoleccion::fuente).containsExactly(Fuente.LINKEDIN);
    }

    @Test
    void unaSegundaEntregaNoMarcaComoYaNoPublicadoLoQueNoTrae() {
        leerLinkedIn(EntregasDePrueba.avisos("primera", 3, reloj.instant()));
        reloj.adelantar(Duration.ofDays(1));

        leerLinkedIn(EntregasDePrueba.avisos("segunda", 2, reloj.instant()));

        assertThat(deLinkedIn()).hasSize(5).allSatisfy(o -> assertThat(o.publicada()).isTrue());
    }

    @Test
    void laProgramadaYElGeneralNoLeenLinkedInYLoDicenAunqueNoSeLeaEnDias() {
        leerLinkedIn(EntregasDePrueba.avisos("e", 1, reloj.instant()));
        Instant leida = fuente(Fuente.LINKEDIN).ultimaLectura();
        reloj.adelantar(Duration.ofDays(4));

        FuenteResponse pendiente = fuente(Fuente.LINKEDIN);
        assertThat(pendiente.estadoConexion()).isEqualTo(EstadoConexion.PENDIENTE_MAQUINA);
        assertThat(pendiente.ultimaLectura()).isEqualTo(leida);

        recoleccion.recoger();
        assertThat(avance().origen()).isEqualTo(OrigenRecoleccion.PROGRAMADA);
        assertThat(avance().fuentes()).extracting(FuenteEnRecoleccion::fuente).doesNotContain(Fuente.LINKEDIN);
        assertThat(avance().omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.LINKEDIN);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.DESDE_MAQUINA);
        });

        RecoleccionResponse general = lanzar(null);
        assertThat(general.omitidas()).extracting(o -> o.fuente() + ":" + o.motivo()).contains("LINKEDIN:DESDE_MAQUINA");
        esperarFin();
        postIdentificado("/api/recoleccion/LINKEDIN").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("DESDE_MAQUINA");

        assertThat(fuente(Fuente.LINKEDIN).ultimaLectura()).isEqualTo(leida);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).isEqualTo(1);
    }

    @Test
    void ningunaRespuestaNiRegistroContieneElCodigoDeLaCorridaNiElToken() {
        StringWriter registro = new StringWriter();
        LoggerContext contexto = (LoggerContext) LogManager.getContext(false);
        Configuration configuracion = contexto.getConfiguration();
        WriterAppender captura = WriterAppender.newBuilder().setName("captura-corrida").setTarget(registro)
                .setLayout(PatternLayout.newBuilder().withPattern("%msg %throwable%n").build()).build();
        captura.start();
        configuracion.getRootLogger().addAppender(captura, Level.ALL, null);
        contexto.updateLoggers();
        String codigo;
        try {
            codigo = abiertaLeyendo();
            entregar(codigo, EntregasDePrueba.avisos("f", 2, reloj.instant()));
            respuestas.add(entregar("B".repeat(43), Fuente.LINKEDIN, List.of(), null, ACCESS.codigoDelLector())
                    .expectStatus().isForbidden().expectBody(String.class).returnResult().getResponseBody());
            respuestas.add(lector("/iniciar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isEqualTo(
                    HttpStatus.CONFLICT).expectBody(String.class).returnResult().getResponseBody());
            terminar(codigo);
            respuestas.add(lector("/terminar", codigo(codigo), ACCESS.codigoDelLector()).expectStatus().isForbidden()
                    .expectBody(String.class).returnResult().getResponseBody());
            respuestas.add(getIdentificado("/api/recoleccion").expectBody(String.class).returnResult().getResponseBody());
            respuestas.add(getIdentificado("/api/fuentes").expectBody(String.class).returnResult().getResponseBody());
        } finally {
            configuracion.getRootLogger().removeAppender("captura-corrida");
            contexto.updateLoggers();
            captura.stop();
        }

        assertThat(registro.toString()).as("la captura funciona").contains("[CORRIDA] abierta desde el panel");
        // Ningún código de Access (un JWT empieza por «eyJ») ni el identificador del token de servicio.
        assertThat(registro.toString()).doesNotContain(codigo).doesNotContain(AccessDePrueba.TOKEN_LECTOR)
                .doesNotContain("eyJ");
        assertThat(respuestas).isNotEmpty().allSatisfy(r -> assertThat(r)
                .doesNotContain(codigo).doesNotContain(AccessDePrueba.TOKEN_LECTOR).doesNotContain("eyJ"));
    }
}
