package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.tacuchi.perfil.dto.CorridaAbiertaResponse;
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaEntregada;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoConexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.FalloMaquina;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Requisito;
import net.tacuchi.perfil.model.Via;
import net.tacuchi.perfil.soporte.EntregasDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Bumeran como conexión de la máquina, por HTTP: la prueba hace de lector con el token de servicio de Access. */
class CorridaBumeranTest extends PruebaOfertas {

    private static final String AVISOS_BUMERAN = "https://www.bumeran.com.pe/empleos/aviso-";

    private RestTestClient.ResponseSpec lector(String ruta, Object cuerpo) {
        return cliente.post().uri("/api/lector/corrida" + ruta).contentType(MediaType.APPLICATION_JSON)
                .header(CABECERA_ACCESS, ACCESS.codigoDelLector()).body(cuerpo).exchange();
    }

    private String abrir() {
        return postIdentificado("/api/recoleccion/maquina").expectStatus().isAccepted()
                .expectBody(CorridaAbiertaResponse.class).returnResult().getResponseBody().codigo();
    }

    private CorridaLectorResponse iniciar(String codigo) {
        return lector("/iniciar", Map.of("codigo", codigo)).expectStatus().isOk()
                .expectBody(CorridaLectorResponse.class).returnResult().getResponseBody();
    }

    private RestTestClient.ResponseSpec empezar(String codigo) {
        return lector("/fuentes/BUMERAN/empezar", Map.of("codigo", codigo));
    }

    private FuenteEnRecoleccion entregar(String codigo, List<OfertaEntregada> avisos, FalloMaquina fallo) {
        return lector("/fuentes/BUMERAN/entregar", new EntregaRequest(codigo, avisos, fallo)).expectStatus().isOk()
                .expectBody(FuenteEnRecoleccion.class).returnResult().getResponseBody();
    }

    private RecoleccionResponse terminar(String codigo) {
        return lector("/terminar", Map.of("codigo", codigo)).expectStatus().isOk()
                .expectBody(RecoleccionResponse.class).returnResult().getResponseBody();
    }

    /** Una corrida que lee solo Bumeran y entrega esos avisos; LinkedIn no se empieza y conserva su lectura. */
    private RecoleccionResponse leerBumeran(String prefijo, int cuantos) {
        String codigo = abrir();
        iniciar(codigo);
        empezar(codigo).expectStatus().isNoContent();
        entregar(codigo, EntregasDePrueba.avisos(prefijo, cuantos, reloj.instant(), AVISOS_BUMERAN), null);
        return terminar(codigo);
    }

    @Test
    void bumeranSeLeeDesdeLaMaquinaSinNecesitarNadaConTresLecturasAlDia() {
        FuenteResponse bumeran = fuente(Fuente.BUMERAN);

        assertThat(bumeran.nombre()).isEqualTo("Bumeran");
        assertThat(bumeran.via()).isEqualTo(Via.MAQUINA);
        assertThat(bumeran.necesita()).isEqualTo(Requisito.NADA);
        assertThat(bumeran.tieneLoQueNecesita()).isTrue();
        assertThat(bumeran.estadoConexion()).as("no tiene «falta iniciar sesión»")
                .isEqualTo(EstadoConexion.PENDIENTE_MAQUINA);
        assertThat(bumeran.lecturasDia()).isEqualTo(Reglas.TOPE_LECTURAS_DIA);
        assertThat(bumeran.lecturasRestantes()).isEqualTo(Reglas.TOPE_LECTURAS_DIA);
        assertThat(bumeran.avisosPorCorrida()).isEqualTo(Reglas.TOPE_AVISOS_ENTREGA);
    }

    @Test
    void unaCorridaLocalEntregaOfertasDeBumeranGuardadasYClasificadas() {
        String codigo = abrir();

        assertThat(iniciar(codigo).fuentes()).extracting(CorridaLectorResponse.FuenteALeer::fuente)
                .containsExactlyInAnyOrder(Fuente.LINKEDIN, Fuente.BUMERAN);
        empezar(codigo).expectStatus().isNoContent();
        FuenteEnRecoleccion entregada = entregar(codigo,
                EntregasDePrueba.avisos("b", 3, reloj.instant(), AVISOS_BUMERAN), null);
        terminar(codigo);

        assertThat(entregada.resultado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=BUMERAN")).hasSize(3).allSatisfy(o -> {
            assertThat(o.fuenteNombre()).isEqualTo("Bumeran");
            assertThat(o.motivos()).isNotEmpty();
            assertThat(o.url()).startsWith(AVISOS_BUMERAN);
            assertThat(o.publicada()).isTrue();
        });
        FuenteResponse bumeran = fuente(Fuente.BUMERAN);
        assertThat(bumeran.estadoConexion()).isEqualTo(EstadoConexion.LISTA);
        assertThat(bumeran.lecturasRestantes()).isEqualTo(Reglas.TOPE_LECTURAS_DIA - 1);
        assertThat(fuente(Fuente.LINKEDIN).lecturasRestantes()).as("LinkedIn no se empezó").isEqualTo(1);
    }

    @Test
    void laCuartaLecturaDelDiaDeBumeranSeRechaza() {
        for (int n = 1; n <= Reglas.TOPE_LECTURAS_DIA; n++) {
            leerBumeran("r" + n, 1);
        }
        assertThat(fuente(Fuente.BUMERAN).lecturasRestantes()).isZero();

        String codigo = abrir();
        CorridaLectorResponse aLeer = iniciar(codigo);

        assertThat(aLeer.fuentes()).extracting(CorridaLectorResponse.FuenteALeer::fuente)
                .containsExactly(Fuente.LINKEDIN);
        empezar(codigo).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        assertThat(avance().omitidas()).anySatisfy(o -> {
            assertThat(o.fuente()).isEqualTo(Fuente.BUMERAN);
            assertThat(o.motivo()).isEqualTo(MotivoOmision.TOPE_DIARIO);
        });
        terminar(codigo);
        assertThat(lista("?encaje=ALTO,MEDIO,NO_ENCAJA&fuente=BUMERAN")).hasSize(Reglas.TOPE_LECTURAS_DIA);
    }

    @Test
    void unaVerificacionDeBumeranLaPausaHastaReanudarla() {
        String codigo = abrir();
        iniciar(codigo);
        empezar(codigo).expectStatus().isNoContent();

        entregar(codigo, null, FalloMaquina.VERIFICACION);
        terminar(codigo);

        assertThat(fuente(Fuente.BUMERAN).estadoConexion()).isEqualTo(EstadoConexion.PAUSADA);
        postIdentificado("/api/fuentes/BUMERAN/reanudar").expectStatus().isOk();
        assertThat(fuente(Fuente.BUMERAN).pausada()).isFalse();
    }

    @Test
    void laProgramadaYElGeneralNoLeenBumeranYLoDicen() {
        recoleccion.recoger();
        assertThat(avance().origen()).isEqualTo(OrigenRecoleccion.PROGRAMADA);
        assertThat(avance().fuentes()).extracting(FuenteEnRecoleccion::fuente).doesNotContain(Fuente.BUMERAN);
        assertThat(avance().omitidas()).extracting(o -> o.fuente() + ":" + o.motivo())
                .contains("BUMERAN:DESDE_MAQUINA");
        esperarFin();

        assertThat(lanzar(null).omitidas()).extracting(o -> o.fuente() + ":" + o.motivo())
                .contains("BUMERAN:DESDE_MAQUINA");
        esperarFin();
        postIdentificado("/api/recoleccion/BUMERAN").expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.motivo").isEqualTo("DESDE_MAQUINA");
        assertThat(fuente(Fuente.BUMERAN).lecturasRestantes()).isEqualTo(Reglas.TOPE_LECTURAS_DIA);
    }
}
