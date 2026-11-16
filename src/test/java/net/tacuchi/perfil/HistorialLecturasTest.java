package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import net.tacuchi.perfil.dto.HistorialLecturasResponse;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import org.junit.jupiter.api.Test;

class HistorialLecturasTest extends PruebaOfertas {
    @Test
    void leer_repetida_conservaAmbasLecturasSinContarNuevasOtraVez() {
        leerADemanda(Fuente.REMOTIVE);
        var primera = historial(Fuente.REMOTIVE).lecturas().getFirst();
        assertThat(primera.estado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(primera.leidas()).isEqualTo(4);
        assertThat(primera.nuevas()).isEqualTo(4);
        assertThat(primera.costoUsd()).isNull();
        assertThat(primera.duracionMs()).isGreaterThanOrEqualTo(0);
        reloj.adelantar(Duration.ofMinutes(1));
        leerADemanda(Fuente.REMOTIVE);
        var filas = historial(Fuente.REMOTIVE).lecturas();
        assertThat(filas).hasSize(2);
        assertThat(filas.getFirst().leidas()).isEqualTo(4);
        assertThat(filas.getFirst().nuevas()).isZero();
        assertThat(filas.getFirst().inicio()).isAfter(primera.inicio());
    }

    @Test
    void leerTodas_conOmitidas_soloRegistraLasLanzadasYCuentaPorConexion() {
        postIdentificado("/api/fuentes/REMOTIVE/pausar").expectStatus().isOk();
        leerADemanda(null);
        assertThat(historial(Fuente.REMOTIVE).lecturas()).isEmpty();
        assertThat(historial(Fuente.LINKEDIN).lecturas()).isEmpty();
        var gob = historial(Fuente.GET_ON_BOARD).lecturas().getFirst();
        var remote = historial(Fuente.REMOTE_OK).lecturas().getFirst();
        assertThat(gob.nuevas()).isEqualTo(6);
        assertThat(remote.nuevas()).isEqualTo(4);
        assertThat(remote.inicio()).isAfter(gob.inicio());
        assertThat(historial(Fuente.GET_ON_BOARD).lecturas()).hasSize(1);
    }

    @Test
    void leer_bloqueada_registraMotivoYNoInventaOfertas() {
        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.BLOQUEA);
        leerADemanda(Fuente.REMOTIVE);
        var fila = historial(Fuente.REMOTIVE).lecturas().getFirst();
        assertThat(fila.estado()).isEqualTo(EstadoLectura.BLOQUEO);
        assertThat(fila.leidas()).isZero();
        assertThat(fila.nuevas()).isZero();
        assertThat(fila.motivo()).isNotBlank();
    }

    @Test
    void historial_conLecturaNuevaEntrePaginas_noDesplazaLasAnteriores() {
        for (int i = 0; i < 25; i++) {
            jdbc.sql("""
                    INSERT INTO lectura_historial(fuente,inicio,duracion_ms,estado,parcial,leidas,nuevas)
                    VALUES ('REMOTIVE', :inicio, 1000, 'CORRECTA', false, 4, 0)
                    """).param("inicio", java.sql.Timestamp.from(reloj.instant().minusSeconds(i))).update();
        }
        var pagina = historial(Fuente.REMOTIVE);
        assertThat(pagina.lecturas()).hasSize(20);
        assertThat(pagina.hayMas()).isTrue();
        leerADemanda(Fuente.REMOTIVE);
        var segunda = getIdentificado("/api/fuentes/REMOTIVE/historial?antesDe=" + pagina.lecturas().getLast().id())
                .expectStatus().isOk().expectBody(HistorialLecturasResponse.class).returnResult().getResponseBody();
        assertThat(segunda.lecturas()).hasSize(5);
        assertThat(segunda.hayMas()).isFalse();
        assertThat(segunda.lecturas()).noneMatch(x -> pagina.lecturas().stream().anyMatch(y -> x.id() == y.id()));
        assertThat(historial(Fuente.GET_ON_BOARD).lecturas()).isEmpty();
    }

    @Test
    void leer_conRelojAdelantado_mideDuracionPropia() {
        FUENTES.pausar(Fuente.REMOTIVE);
        var inicio = reloj.instant();
        lanzar(Fuente.REMOTIVE);
        esperarAvance(a -> a.fuentes().getFirst().paso() == net.tacuchi.perfil.model.PasoFuente.LEYENDO, "leyendo");
        reloj.adelantar(Duration.ofSeconds(30));
        FUENTES.soltar(Fuente.REMOTIVE);
        esperarFin();
        var fila = historial(Fuente.REMOTIVE).lecturas().getFirst();
        assertThat(fila.inicio()).isAfterOrEqualTo(inicio);
        assertThat(fila.duracionMs()).isBetween(30000L, 40000L);
    }

    @Test
    void historial_sinIdentificacion_rechaza() {
        cliente.get().uri("/api/fuentes/REMOTIVE/historial").exchange().expectStatus().isUnauthorized();
    }

    private HistorialLecturasResponse historial(Fuente fuente) {
        return getIdentificado("/api/fuentes/" + fuente + "/historial").expectStatus().isOk()
                .expectBody(HistorialLecturasResponse.class).returnResult().getResponseBody();
    }
}
