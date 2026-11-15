package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.tacuchi.perfil.exception.RecoleccionRechazadaException;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tope de lecturas por fuente y día de Lima, contando programadas y a demanda. Lo que se mide es lo que recibió el
 * sitio de prueba, no lo que dice la base.
 */
class TopeDiarioTest extends PruebaOfertas {

    @Autowired
    private LecturaDiaRepository lecturaDiaRepository;

    private LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), Reglas.ZONA_PERSONA);
    }

    private void leerConElServicio(Fuente fuente) {
        recoleccion.lanzar(fuente);
        esperarFin();
    }

    @Test
    void unaFuenteRecibeTresLecturasAlDiaYLaCuartaSeRechazaSinLlegarAlSitio() {
        recoleccion.recoger();
        leerConElServicio(Fuente.REMOTIVE);
        leerConElServicio(Fuente.REMOTIVE);

        assertThatThrownBy(() -> recoleccion.lanzar(Fuente.REMOTIVE))
                .isInstanceOf(RecoleccionRechazadaException.class)
                .extracting(e -> ((RecoleccionRechazadaException) e).getMotivo())
                .isEqualTo(RecoleccionRechazadaException.Motivo.TOPE_DIARIO);
        recoleccion.recoger();

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).isEqualTo(3);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isZero();
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK, hoy())).as("la programada la leyó una vez").isEqualTo(1);
    }

    @Test
    void unaLecturaQueFallaConsumeUnaYLaFuenteSeVuelveALeerElMismoDia() {
        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.FALLA);
        leerConElServicio(Fuente.REMOTIVE);

        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(2);

        FUENTES.modo(Fuente.REMOTIVE, FuentesDePrueba.Modo.BIEN);
        leerConElServicio(Fuente.REMOTIVE);

        assertThat(fuente(Fuente.REMOTIVE).estado()).isEqualTo(EstadoLectura.CORRECTA);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).isEqualTo(2);
    }

    @Test
    void laProgramadaNoReleeLoYaLeidoHoyNiReintentaLaQueFallo() {
        FUENTES.modo(Fuente.REMOTE_OK, FuentesDePrueba.Modo.FALLA);
        leerConElServicio(Fuente.REMOTIVE);

        recoleccion.recoger();
        recoleccion.recoger();

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).as("la lectura a demanda fue la del día").isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK, hoy())).as("sin reintento tras el fallo").isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.GET_ON_BOARD, hoy())).as("una página por categoría").isEqualTo(4);
        assertThat(fuente(Fuente.REMOTE_OK).lecturasRestantes()).isEqualTo(2);
    }

    @Test
    void elCambioDeDiaDevuelveLasTresLecturas() {
        leerConElServicio(Fuente.REMOTIVE);
        leerConElServicio(Fuente.REMOTIVE);
        leerConElServicio(Fuente.REMOTIVE);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isZero();

        reloj.adelantar(Duration.ofDays(1));

        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(3);
        recoleccion.recoger();
        assertThat(FUENTES.lecturas(Fuente.REMOTIVE, hoy())).as("la programada del día nuevo").isEqualTo(1);
        assertThat(fuente(Fuente.REMOTIVE).lecturasRestantes()).isEqualTo(2);
    }

    @Test
    void unaFuenteNuncaLeidaTieneSusTresLecturas() {
        assertThat(fuentes()).allSatisfy(f -> {
            assertThat(f.estado()).isEqualTo(EstadoLectura.SIN_LEER);
            assertThat(f.lecturasRestantes()).isEqualTo(3);
        });
    }

    @Test
    void reservasSimultaneasNuncaPasanElTope() throws Exception {
        LocalDate dia = hoy();
        List<Callable<Boolean>> reservas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            reservas.add(() -> lecturaDiaRepository.reservar(Fuente.REMOTE_OK, dia, Reglas.TOPE_LECTURAS_DIA));
        }
        int aceptadas = 0;
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            for (Future<Boolean> reserva : hilos.invokeAll(reservas)) {
                aceptadas += reserva.get() ? 1 : 0;
            }
        }

        assertThat(aceptadas).isEqualTo(3);
        assertThat(lecturaDiaRepository.delDia(dia)).containsEntry(Fuente.REMOTE_OK, 3);
    }
}
