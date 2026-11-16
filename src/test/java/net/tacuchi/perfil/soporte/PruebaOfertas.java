package net.tacuchi.perfil.soporte;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import org.junit.jupiter.api.AfterEach;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.model.Via;
import net.tacuchi.perfil.service.CorridaLocalService;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/** Base de las pruebas del monitor de ofertas: registrar lo que entregaría una lectura y leerlo por HTTP. */
public abstract class PruebaOfertas extends PruebaHttp {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired
    protected RegistroOfertas registro;

    @Autowired
    protected RecoleccionService recoleccion;

    @Autowired
    protected CorridaLocalService corridaLocal;

    /** Lo que entregaría una lectura completa de la fuente en este instante del reloj de prueba. */
    protected void leidas(Fuente fuente, OfertaRecogida... ofertas) {
        registro.registrar(fuente, new FuenteOfertas.Lectura(List.of(ofertas), true), reloj.instant());
    }

    protected void leidasEnParte(Fuente fuente, OfertaRecogida... ofertas) {
        registro.registrar(fuente, new FuenteOfertas.Lectura(List.of(ofertas), false), reloj.instant());
    }

    protected List<OfertaResponse> lista(String consulta) {
        return getIdentificado("/api/ofertas" + consulta).expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<OfertaResponse>>() { })
                .returnResult().getResponseBody();
    }

    protected List<OfertaResponse> todas() {
        return lista("?encaje=ALTO,MEDIO,NO_ENCAJA&estado=NUEVA,GUARDADA,DESCARTADA,POSTULADA");
    }

    protected OfertaResponse unica(String consulta) {
        List<OfertaResponse> ofertas = lista(consulta);
        if (ofertas.size() != 1) {
            throw new AssertionError("se esperaba una oferta para «" + consulta + "» y llegaron " + ofertas.size());
        }
        return ofertas.getFirst();
    }

    protected OfertaDetalleResponse detalle(long id) {
        return getIdentificado("/api/ofertas/" + id).expectStatus().isOk()
                .expectBody(OfertaDetalleResponse.class).returnResult().getResponseBody();
    }

    protected OfertaDetalleResponse marcar(long id, String estado) {
        return patchIdentificado("/api/ofertas/" + id, "{\"estado\":\"" + estado + "\"}").expectStatus().isOk()
                .expectBody(OfertaDetalleResponse.class).returnResult().getResponseBody();
    }

    protected List<FuenteResponse> fuentes() {
        return getIdentificado("/api/fuentes").expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<FuenteResponse>>() { })
                .returnResult().getResponseBody();
    }

    /** Las conexiones que lee el servidor; las de la máquina tienen sus propias pruebas. */
    protected List<FuenteResponse> fuentesDelServidor() {
        return fuentes().stream().filter(f -> f.via() == Via.SERVIDOR).toList();
    }

    /** Omitidas por pausa o tope; sin las de la máquina, que toda recolección del servidor omite. */
    protected static List<FuenteOmitida> omitidasDelServidor(RecoleccionResponse avance) {
        return avance.omitidas().stream().filter(o -> o.motivo() != MotivoOmision.DESDE_MAQUINA).toList();
    }

    protected FuenteResponse fuente(Fuente fuente) {
        return fuentes().stream().filter(f -> f.fuente() == fuente).findFirst().orElseThrow();
    }

    /** Lanza por HTTP la recolección general (sin fuente) o de una fuente, y espera la aceptación. */
    protected RecoleccionResponse lanzar(Fuente fuente) {
        return postIdentificado(fuente == null ? "/api/recoleccion" : "/api/recoleccion/" + fuente.name())
                .expectStatus().isAccepted()
                .expectBody(RecoleccionResponse.class).returnResult().getResponseBody();
    }

    protected RecoleccionResponse avance() {
        return getIdentificado("/api/recoleccion").expectStatus().isOk()
                .expectBody(RecoleccionResponse.class).returnResult().getResponseBody();
    }

    /** Espera, consultando por HTTP, a que el avance cumpla la condición. */
    protected RecoleccionResponse esperarAvance(Predicate<RecoleccionResponse> condicion, String que) {
        Instant limite = Instant.now().plus(ESPERA);
        RecoleccionResponse actual = avance();
        while (!condicion.test(actual)) {
            if (Instant.now().isAfter(limite)) {
                throw new AssertionError("el avance no llegó a «" + que + "»; quedó en " + actual);
            }
            dormir();
            actual = avance();
        }
        return actual;
    }

    protected RecoleccionResponse esperarFin() {
        return esperarAvance(a -> a.estado() != EstadoRecoleccion.EN_CURSO, "terminada");
    }

    /** Lanza y espera el resultado: una lectura a demanda completa. */
    protected RecoleccionResponse leerADemanda(Fuente fuente) {
        lanzar(fuente);
        return esperarFin();
    }

    protected static FuenteEnRecoleccion enAvance(RecoleccionResponse avance, Fuente fuente) {
        return avance.fuentes().stream().filter(f -> f.fuente() == fuente).findFirst()
                .orElseThrow(() -> new AssertionError(fuente + " no está entre las lanzadas: " + avance));
    }

    /** Ninguna recolección de una prueba sigue viva en la siguiente; una corrida local abierta se vence. */
    @AfterEach
    void terminarRecoleccionEnCurso() {
        reloj.adelantar(Reglas.PLAZO_CORRIDA_LOCAL.plus(Duration.ofMinutes(1)));
        corridaLocal.revisarPlazos();
        for (Fuente fuente : Fuente.values()) {
            FUENTES.soltar(fuente);
        }
        Instant limite = Instant.now().plus(ESPERA);
        while (recoleccion.avance().estado() == EstadoRecoleccion.EN_CURSO && Instant.now().isBefore(limite)) {
            dormir();
        }
    }

    private static void dormir() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("espera interrumpida", e);
        }
    }
}
