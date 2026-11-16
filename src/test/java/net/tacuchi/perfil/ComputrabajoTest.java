package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Requisito;
import net.tacuchi.perfil.model.Via;
import net.tacuchi.perfil.soporte.ApifyDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Computrabajo se lee por un actor de Apify con la clave y el tope mensual que la persona carga en Conexiones. Todo
 * por HTTP, contra el doble de Apify con la salida guardada de la prueba real.
 */
class ComputrabajoTest extends PruebaOfertas {

    private static final String RUTA = "/api/fuentes/COMPUTRABAJO";

    private RestTestClient.ResponseSpec put(String ruta, String json) {
        return cliente.put().uri(ruta)
                .header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private FuenteResponse cargarClave(String clave) {
        return put(RUTA + "/clave", "{\"clave\":\"" + clave + "\"}").expectStatus().isOk()
                .expectBody(FuenteResponse.class).returnResult().getResponseBody();
    }

    private FuenteResponse fijarTope(String tope) {
        return put(RUTA + "/tope", "{\"topeMensual\":" + tope + "}").expectStatus().isOk()
                .expectBody(FuenteResponse.class).returnResult().getResponseBody();
    }

    private FuenteResponse computrabajo() {
        return fuente(Fuente.COMPUTRABAJO);
    }

    private List<OfertaResponse> deComputrabajo() {
        return todas().stream().filter(o -> o.fuente() == Fuente.COMPUTRABAJO).toList();
    }

    /** Lleva el reloj a esa hora de Lima. */
    private void irA(LocalDateTime enLima) {
        reloj.adelantar(Duration.between(reloj.instant(), enLima.atZone(Reglas.ZONA_PERSONA).toInstant()));
    }

    private LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
    }

    @Test
    void sinClaveNiTopeQuedaFaltaConfigurarYNadieLaLee() {
        FuenteResponse sinNada = computrabajo();
        assertThat(sinNada.via()).isEqualTo(Via.PAGO);
        assertThat(sinNada.necesita()).isEqualTo(Requisito.CLAVE);
        assertThat(sinNada.tieneLoQueNecesita()).isFalse();
        assertThat(sinNada.estadoConexion()).isEqualTo(EstadoConexion.FALTA_CONFIGURAR);
        assertThat(sinNada.queFalta()).isEqualTo("falta la clave del servicio");
        assertThat(sinNada.tieneClave()).isFalse();
        assertThat(sinNada.consumoMes()).isEqualByComparingTo("0");
        assertThat(sinNada.estado()).as("nunca como una lectura sin ofertas").isEqualTo(EstadoLectura.SIN_LEER);

        RecoleccionResponse general = lanzar(null);
        assertThat(general.omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.COMPUTRABAJO);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.FALTA_CONFIGURAR);
        });
        esperarFin();
        postIdentificado("/api/recoleccion/COMPUTRABAJO").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("FALTA_CONFIGURAR");

        FuenteResponse sinTope = cargarClave(ApifyDePrueba.CLAVE);
        assertThat(sinTope.estadoConexion()).isEqualTo(EstadoConexion.FALTA_CONFIGURAR);
        assertThat(sinTope.queFalta()).isEqualTo("falta el tope mensual");
        postIdentificado("/api/recoleccion/COMPUTRABAJO").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("FALTA_CONFIGURAR");
        recoleccion.recoger();

        assertThat(APIFY.consultas()).as("sin clave o sin tope no se consulta al servicio").isZero();
    }

    @Test
    void laClaveCargadaYReemplazadaNuncaVuelveNiQuedaEnLosRegistros() {
        String anterior = "clave-de-prueba-anterior";
        StringWriter registro = new StringWriter();
        LoggerContext contexto = (LoggerContext) LogManager.getContext(false);
        Configuration configuracion = contexto.getConfiguration();
        WriterAppender captura = WriterAppender.newBuilder().setName("captura-claves").setTarget(registro)
                .setLayout(PatternLayout.newBuilder().withPattern("%msg %throwable%n").build()).build();
        captura.start();
        configuracion.getRootLogger().addAppender(captura, Level.ALL, null);
        contexto.updateLoggers();
        List<String> respuestas = new ArrayList<>();
        FuenteResponse primera;
        FuenteResponse reemplazada;
        try {
            respuestas.add(put(RUTA + "/clave", "{\"clave\":\"" + anterior + "\"}").expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody());
            primera = computrabajo();
            reloj.adelantar(Duration.ofDays(1));
            reemplazada = cargarClave(ApifyDePrueba.CLAVE);
            fijarTope("5");
            respuestas.add(getIdentificado("/api/fuentes").expectBody(String.class).returnResult().getResponseBody());
            leerADemanda(Fuente.COMPUTRABAJO);
            respuestas.add(getIdentificado("/api/recoleccion").expectBody(String.class).returnResult().getResponseBody());
            respuestas.add(getIdentificado("/api/fuentes").expectBody(String.class).returnResult().getResponseBody());
            respuestas.add(put(RUTA + "/clave", "{\"clave\":\"\"}").expectStatus().isBadRequest()
                    .expectBody(String.class).returnResult().getResponseBody());
        } finally {
            configuracion.getRootLogger().removeAppender("captura-claves");
            contexto.updateLoggers();
            captura.stop();
        }

        assertThat(primera.tieneClave()).isTrue();
        assertThat(reemplazada.tieneClave()).isTrue();
        assertThat(reemplazada.claveCargadaEn()).as("la fecha es la del reemplazo").isAfter(primera.claveCargadaEn());
        assertThat(APIFY.peticiones()).as("el servicio recibió la clave nueva").allSatisfy(p -> assertThat(p.conClave())
                .isTrue());
        assertThat(registro.toString()).as("la captura funciona").contains("[CONEXIONES] Computrabajo: clave cargada");
        assertThat(registro.toString()).doesNotContain(anterior).doesNotContain(ApifyDePrueba.CLAVE);
        assertThat(respuestas).hasSize(5).allSatisfy(r -> assertThat(r)
                .doesNotContain(anterior).doesNotContain(ApifyDePrueba.CLAVE));
    }

    @Test
    void laClaveGuardadaNoEsLegibleEnLaBase() {
        cargarClave(ApifyDePrueba.CLAVE);

        byte[] guardada = jdbc.sql("SELECT clave_cifrada FROM conexion WHERE fuente = 'COMPUTRABAJO'")
                .query(byte[].class).single();
        assertThat(new String(guardada, StandardCharsets.ISO_8859_1)).doesNotContain(ApifyDePrueba.CLAVE);
        assertThat(guardada.length).as("vector y etiqueta junto a la clave")
                .isGreaterThan(ApifyDePrueba.CLAVE.length() + 12);

        cargarClave(ApifyDePrueba.CLAVE);
        byte[] otraVez = jdbc.sql("SELECT clave_cifrada FROM conexion WHERE fuente = 'COMPUTRABAJO'")
                .query(byte[].class).single();
        assertThat(otraVez).as("cada carga lleva su propio vector").isNotEqualTo(guardada);
    }

    @Test
    void conClaveYTopeSeLeeConLaSalidaGuardadaYSusOfertasEntranALaLista() {
        cargarClave(ApifyDePrueba.CLAVE);
        FuenteResponse lista = fijarTope("5");
        assertThat(lista.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
        assertThat(lista.topeMensual()).isEqualByComparingTo("5");
        assertThat(lista.queFalta()).isNull();

        RecoleccionResponse fin = leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(enAvance(fin, Fuente.COMPUTRABAJO).resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(enAvance(fin, Fuente.COMPUTRABAJO).ofertasLeidas()).isEqualTo(APIFY.avisosGuardados());
        List<OfertaResponse> ofertas = deComputrabajo();
        assertThat(ofertas).hasSize(APIFY.avisosGuardados()).allSatisfy(o -> {
            assertThat(o.url()).startsWith("https://pe.computrabajo.com/ofertas-de-trabajo/");
            assertThat(o.encaje()).isNotNull();
            assertThat(o.fuenteNombre()).isEqualTo("Computrabajo");
        });
        assertThat(ofertas).extracting(OfertaResponse::titulo).contains("Programador Junior PHP/Laravel");
        assertThat(ofertas).extracting(OfertaResponse::modalidad)
                .contains(Modalidad.REMOTO, Modalidad.HIBRIDO, Modalidad.PRESENCIAL, Modalidad.NO_INFORMA);
        assertThat(ofertas).filteredOn(o -> o.titulo().startsWith("Full Stack Developer"))
                .singleElement().satisfies(o -> assertThat(o.empresa()).isNull());

        ApifyDePrueba.Peticion lanzamiento = APIFY.peticiones().getFirst();
        assertThat(lanzamiento.metodo()).isEqualTo("POST");
        assertThat(lanzamiento.ruta()).isEqualTo("/v2/acts/" + ApifyDePrueba.ACTOR + "/runs");
        assertThat(lanzamiento.parametros()).containsEntry("maxTotalChargeUsd", "5.0000")
                .containsEntry("maxItems", "25").doesNotContainValue(ApifyDePrueba.CLAVE);
        assertThat(lanzamiento.cuerpo().path("searchQueries")).hasSize(3);
        assertThat(lanzamiento.cuerpo().path("country").asString()).isEqualTo("pe");
        assertThat(lanzamiento.cuerpo().path("sortBy").asString()).isEqualTo("newest");
        assertThat(lanzamiento.cuerpo().path("maxResults").asInt()).isEqualTo(25);
        assertThat(APIFY.peticiones()).allSatisfy(p -> assertThat(p.conClave()).isTrue());

        FuenteResponse leida = computrabajo();
        assertThat(leida.consumoMes()).as("el costo asentado que informó la corrida")
                .isEqualByComparingTo(ApifyDePrueba.costo(APIFY.avisosGuardados()));
        assertThat(leida.lecturasRestantes()).as("sin tope diario").isZero();
        assertThat(leida.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
    }

    @Test
    void sinTopeDiarioSeLeeMasDeTresVecesAlDia() {
        cargarClave(ApifyDePrueba.CLAVE);
        fijarTope("5");

        for (int i = 0; i < Reglas.TOPE_LECTURAS_DIA + 1; i++) {
            assertThat(enAvance(leerADemanda(Fuente.COMPUTRABAJO), Fuente.COMPUTRABAJO).resultado())
                    .isEqualTo(EstadoLectura.CORRECTA);
        }

        assertThat(computrabajo().consumoMes())
                .isEqualByComparingTo(ApifyDePrueba.costo(APIFY.avisosGuardados()).multiply(BigDecimal.valueOf(4)));
    }

    @Test
    void unCostoQueNoSeAsientaSeCalculaPorElConteoDeAvisos() {
        cargarClave(ApifyDePrueba.CLAVE);
        fijarTope("5");
        APIFY.asentarTras(-1);

        leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(computrabajo().consumoMes()).as("no el costo de recién terminada (solo el arranque)")
                .isEqualByComparingTo(ApifyDePrueba.costo(APIFY.avisosGuardados()));
    }

    @Test
    void unaCorridaQueTardaSeEsperaHastaQueTermina() {
        cargarClave(ApifyDePrueba.CLAVE);
        fijarTope("5");
        APIFY.modo(ApifyDePrueba.Modo.TARDA);

        RecoleccionResponse fin = leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(enAvance(fin, Fuente.COMPUTRABAJO).ofertasLeidas()).isEqualTo(APIFY.avisosGuardados());
        assertThat(computrabajo().consumoMes()).isEqualByComparingTo(ApifyDePrueba.costo(APIFY.avisosGuardados()));
    }

    @Test
    void unaCorridaFallidaEsFalloYSuArranqueSeCuenta() {
        cargarClave(ApifyDePrueba.CLAVE);
        fijarTope("5");
        APIFY.modo(ApifyDePrueba.Modo.FALLA);

        RecoleccionResponse fin = leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(enAvance(fin, Fuente.COMPUTRABAJO).resultado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(computrabajo().detalle()).contains("FAILED");
        assertThat(computrabajo().consumoMes()).isEqualByComparingTo(ApifyDePrueba.PRECIO_ARRANQUE);
        assertThat(deComputrabajo()).isEmpty();
    }

    @Test
    void elConsumoSeSumaPorMesDeLima() {
        cargarClave(ApifyDePrueba.CLAVE);
        fijarTope("5");
        LocalDate primeroDelSiguiente = hoy().plusMonths(1).withDayOfMonth(1);
        irA(primeroDelSiguiente.minusDays(1).atTime(23, 30));

        leerADemanda(Fuente.COMPUTRABAJO);
        BigDecimal unaCorrida = ApifyDePrueba.costo(APIFY.avisosGuardados());
        assertThat(computrabajo().consumoMes()).isEqualByComparingTo(unaCorrida);

        irA(primeroDelSiguiente.atTime(0, 30));
        assertThat(computrabajo().consumoMes()).as("mes nuevo de Lima, aunque en UTC aún no cambie el día")
                .isEqualByComparingTo("0");
        leerADemanda(Fuente.COMPUTRABAJO);
        leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(computrabajo().consumoMes()).isEqualByComparingTo(unaCorrida.multiply(BigDecimal.TWO));
        assertThat(jdbc.sql("SELECT mes, consumo FROM consumo_mes ORDER BY mes")
                .query((rs, n) -> rs.getDate("mes").toLocalDate() + "=" + rs.getBigDecimal("consumo").stripTrailingZeros()
                        .toPlainString())
                .list())
                .containsExactly(primeroDelSiguiente.minusMonths(1) + "=" + unaCorrida.toPlainString(),
                        primeroDelSiguiente + "=" + unaCorrida.multiply(BigDecimal.TWO).toPlainString());
    }

    @Test
    void alcanzadoElTopeNoSeLeeYElServicioNoRecibeNingunaConsulta() {
        irA(hoy().plusMonths(1).withDayOfMonth(10).atTime(12, 0));
        cargarClave(ApifyDePrueba.CLAVE);
        // Una corrida completa (0,016) deja un saldo que solo alcanza para el arranque y un aviso.
        fijarTope("0.02");
        leerADemanda(Fuente.COMPUTRABAJO);
        assertThat(computrabajo().estadoConexion()).isEqualTo(EstadoConexion.LISTA);

        leerADemanda(Fuente.COMPUTRABAJO);
        ApifyDePrueba.Peticion segunda = APIFY.peticiones().stream().filter(p -> p.metodo().equals("POST"))
                .toList().getLast();
        assertThat(segunda.parametros()).as("el gasto máximo es el saldo del tope").containsEntry("maxTotalChargeUsd",
                "0.0040");
        FuenteResponse enTope = computrabajo();
        assertThat(enTope.consumoMes()).isEqualByComparingTo("0.02");
        assertThat(enTope.estadoConexion()).isEqualTo(EstadoConexion.TOPE_MENSUAL);
        int consultas = APIFY.consultas();

        postIdentificado("/api/recoleccion/COMPUTRABAJO").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("TOPE_MENSUAL")
                .jsonPath("$.detail").value(d -> assertThat((String) d).contains("tope del mes"));
        RecoleccionResponse general = lanzar(null);
        assertThat(general.omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.COMPUTRABAJO);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.TOPE_MENSUAL);
        });
        esperarFin();
        reloj.adelantar(Duration.ofDays(1));
        recoleccion.recoger();
        RecoleccionResponse programada = avance();
        assertThat(programada.origen()).isEqualTo(OrigenRecoleccion.PROGRAMADA);
        assertThat(programada.omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.COMPUTRABAJO);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.TOPE_MENSUAL);
        });

        assertThat(APIFY.consultas()).as("ni a demanda ni en la programada").isEqualTo(consultas);

        FuenteResponse subido = fijarTope("1");
        assertThat(subido.estadoConexion()).as("subir el tope la vuelve a dejar lista").isEqualTo(EstadoConexion.LISTA);
    }

    @Test
    void unaClaveErradaEsBloqueoConUnMotivoQueNoLaContiene() {
        String errada = "clave-de-prueba-equivocada";
        cargarClave(errada);
        fijarTope("5");

        RecoleccionResponse fin = leerADemanda(Fuente.COMPUTRABAJO);

        assertThat(enAvance(fin, Fuente.COMPUTRABAJO).resultado()).isEqualTo(EstadoLectura.BLOQUEO);
        FuenteResponse bloqueada = computrabajo();
        assertThat(bloqueada.estado()).isEqualTo(EstadoLectura.BLOQUEO);
        assertThat(bloqueada.detalle()).contains("rechazó la clave").doesNotContain(errada);
        assertThat(bloqueada.consumoMes()).as("sin corrida no hay costo").isEqualByComparingTo("0");
        assertThat(APIFY.peticiones()).singleElement().satisfies(p -> assertThat(p.conClave()).isFalse());
    }

    @Test
    void cargarLaClaveYElTopeExigenElCodigoYVenirDelPanel() {
        cliente.put().uri(RUTA + "/clave").header("Origin", ORIGEN_PANEL).contentType(MediaType.APPLICATION_JSON)
                .body("{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}").exchange()
                .expectStatus().isUnauthorized();
        cliente.put().uri(RUTA + "/clave").header(CABECERA_ACCESS, ACCESS.codigoValido())
                .header("Origin", "https://otro-sitio.test").contentType(MediaType.APPLICATION_JSON)
                .body("{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}").exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        cliente.put().uri(RUTA + "/tope").header(CABECERA_ACCESS, ACCESS.codigoValido())
                .contentType(MediaType.APPLICATION_JSON).body("{\"topeMensual\":5}").exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        FuenteResponse sinCambios = computrabajo();
        assertThat(sinCambios.tieneClave()).isFalse();
        assertThat(sinCambios.topeMensual()).isNull();
    }

    @Test
    void soloLasDePagoTienenClaveYTopeYElTopeDebeSerPositivo() {
        put("/api/fuentes/REMOTIVE/clave", "{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.motivo").isEqualTo("NO_ES_DE_PAGO");
        put("/api/fuentes/REMOTIVE/tope", "{\"topeMensual\":5}").expectStatus().isBadRequest();
        put(RUTA + "/tope", "{\"topeMensual\":0}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(d -> assertThat((String) d).contains("topeMensual"));
        put(RUTA + "/tope", "{\"topeMensual\":1.234}").expectStatus().isBadRequest();

        assertThat(computrabajo().topeMensual()).isNull();
        assertThat(fuente(Fuente.REMOTIVE).tieneClave()).isFalse();
    }
}
