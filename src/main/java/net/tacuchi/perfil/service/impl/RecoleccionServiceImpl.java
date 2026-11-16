package net.tacuchi.perfil.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.exception.RecoleccionRechazadaException;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.fuentes.LecturaFallidaException;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.EstadoRecoleccion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.PasoFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.repository.OfertaRepository;
import net.tacuchi.perfil.service.AvisoService;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cada lectura se cuenta por fuente y día de Lima antes de pedirla al sitio: ni un reinicio ni un fallo dejan
 * pasar el tope. Un fallo deja lo ya recogido y no prueba otra vía. Una conexión pausada no se lee ni se cuenta.
 *
 * <p>Una sola recolección a la vez, programada o a demanda. El candado se toma en el hilo que la pide (para
 * responder al instante si se acepta) y lo suelta el hilo que lee; por eso es una bandera guardada por un monitor y
 * no un {@code ReentrantLock}, que solo puede soltar el hilo que lo tomó. El avance vive en memoria: con una sola
 * instancia de la API basta.
 */
@Slf4j
@Service
public class RecoleccionServiceImpl implements RecoleccionService {

    private final List<FuenteOfertas> fuentes;
    private final RegistroOfertas registro;
    private final FuenteRepository fuenteRepository;
    private final LecturaDiaRepository lecturaDiaRepository;
    private final ConexionRepository conexionRepository;
    private final OfertaRepository ofertaRepository;
    private final AvisoService avisoService;
    private final TaskExecutor ejecutor;
    private final Clock clock;

    private final Object candado = new Object();
    /** Guardada por {@link #candado}. */
    private boolean enCurso;
    /** Se cambia solo dentro de {@link #candado}; se lee sin él. */
    private volatile RecoleccionResponse avance = RecoleccionResponse.NINGUNA;

    public RecoleccionServiceImpl(List<FuenteOfertas> fuentes, RegistroOfertas registro,
            FuenteRepository fuenteRepository, LecturaDiaRepository lecturaDiaRepository,
            ConexionRepository conexionRepository, OfertaRepository ofertaRepository, AvisoService avisoService,
            @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor, Clock clock) {
        this.fuentes = fuentes.stream().sorted(Comparator.comparing(FuenteOfertas::fuente)).toList();
        this.registro = registro;
        this.fuenteRepository = fuenteRepository;
        this.lecturaDiaRepository = lecturaDiaRepository;
        this.conexionRepository = conexionRepository;
        this.ofertaRepository = ofertaRepository;
        this.avisoService = avisoService;
        this.ejecutor = ejecutor;
        this.clock = clock;
    }

    @Scheduled(cron = "${perfil.ofertas.cron}", zone = "America/Lima")
    public void programada() {
        recoger();
    }

    @Override
    public void recoger() {
        if (!tomar()) {
            log.info("[OFERTAS] ya hay una recolección en curso; la programada se omite");
            return;
        }
        Instant inicio = clock.instant();
        List<FuenteOfertas> lanzadas;
        try {
            lanzadas = reservarProgramada(inicio);
        } catch (RuntimeException e) {
            soltar(true);
            throw e;
        }
        correr(lanzadas, inicio);
    }

    /**
     * Reserva la lectura del día de cada fuente activa que hoy no tiene ninguna y muestra el avance si hay algo que
     * leer, con las pausadas como omitidas.
     */
    private List<FuenteOfertas> reservarProgramada(Instant inicio) {
        LocalDate hoy = LocalDate.ofInstant(inicio, Reglas.ZONA_PERSONA);
        Set<Fuente> pausadas = conexionRepository.pausadas();
        List<FuenteOmitida> omitidas = new ArrayList<>();
        List<FuenteOfertas> lanzadas = new ArrayList<>();
        for (FuenteOfertas fuente : fuentes) {
            if (pausadas.contains(fuente.fuente())) {
                omitidas.add(FuenteOmitida.de(fuente.fuente(), MotivoOmision.PAUSADA));
            } else if (lecturaDiaRepository.reservar(fuente.fuente(), hoy, Reglas.LECTURAS_PROGRAMADAS_DIA)) {
                lanzadas.add(fuente);
            }
        }
        if (!lanzadas.isEmpty()) {
            // Una programada sin nada que leer no reemplaza el último resultado.
            cambiarAvance(a -> RecoleccionResponse.iniciada(OrigenRecoleccion.PROGRAMADA, inicio,
                    fuentesDe(lanzadas), omitidas));
            if (!omitidas.isEmpty()) {
                log.info("[OFERTAS] la programada omite {} conexiones pausadas", omitidas.size());
            }
        }
        return List.copyOf(lanzadas);
    }

    @Override
    public RecoleccionResponse lanzar() {
        return lanzarDemanda(fuentes, true);
    }

    @Override
    public RecoleccionResponse lanzar(Fuente fuente) {
        FuenteOfertas pedida = fuentes.stream().filter(f -> f.fuente() == fuente).findFirst()
                .orElseThrow(() -> new IllegalStateException("fuente sin lector: " + fuente));
        return lanzarDemanda(List.of(pedida), false);
    }

    @Override
    public RecoleccionResponse avance() {
        return avance;
    }

    private RecoleccionResponse lanzarDemanda(List<FuenteOfertas> pedidas, boolean general) {
        if (!tomar()) {
            throw RecoleccionRechazadaException.enCurso();
        }
        boolean iniciada = false;
        try {
            Instant inicio = clock.instant();
            LocalDate hoy = LocalDate.ofInstant(inicio, Reglas.ZONA_PERSONA);
            Set<Fuente> pausadas = conexionRepository.pausadas();
            List<FuenteOfertas> lanzadas = new ArrayList<>();
            List<FuenteOmitida> omitidas = new ArrayList<>();
            for (FuenteOfertas fuente : pedidas) {
                if (pausadas.contains(fuente.fuente())) {
                    omitidas.add(FuenteOmitida.de(fuente.fuente(), MotivoOmision.PAUSADA));
                } else if (lecturaDiaRepository.reservar(fuente.fuente(), hoy, Reglas.TOPE_LECTURAS_DIA)) {
                    lanzadas.add(fuente);
                } else {
                    omitidas.add(FuenteOmitida.de(fuente.fuente(), MotivoOmision.TOPE_DIARIO));
                }
            }
            if (lanzadas.isEmpty()) {
                throw general ? RecoleccionRechazadaException.sinFuentes(omitidas, Reglas.TOPE_LECTURAS_DIA)
                        : RecoleccionRechazadaException.omitida(omitidas.getFirst(), Reglas.TOPE_LECTURAS_DIA);
            }
            RecoleccionResponse respuesta = cambiarAvance(a -> RecoleccionResponse.iniciada(
                    OrigenRecoleccion.DEMANDA, inicio, fuentesDe(lanzadas), omitidas));
            iniciada = true;
            log.info("[OFERTAS] recolección a demanda lanzada: {} fuentes, {} omitidas",
                    lanzadas.size(), omitidas.size());
            ejecutor.execute(() -> correr(List.copyOf(lanzadas), inicio));
            return respuesta;
        } catch (RuntimeException e) {
            soltar(!iniciada);
            throw e;
        }
    }

    /** Lee las fuentes ya reservadas, depura y avisa; pase lo que pase, cierra el avance y suelta el candado. */
    private void correr(List<FuenteOfertas> lanzadas, Instant inicio) {
        try {
            for (FuenteOfertas fuente : lanzadas) {
                cambiarAvance(a -> a.conFuente(fuente.fuente(), FuenteEnRecoleccion::leyendo));
                Resultado resultado = leer(fuente, inicio);
                cambiarAvance(a -> a.conFuente(fuente.fuente(),
                        f -> f.terminada(resultado.estado(), resultado.ofertas())));
            }
            int eliminadas = ofertaRepository.eliminarVistasAntesDe(inicio.minus(Reglas.CONSERVACION_OFERTAS));
            if (eliminadas > 0) {
                log.info("[OFERTAS] {} ofertas eliminadas por conservación", eliminadas);
            }
            avisoService.avisarSiCorresponde();
        } catch (RuntimeException e) {
            log.error("[OFERTAS] la recolección se interrumpió por un fallo inesperado", e);
        } finally {
            soltar(lanzadas.isEmpty());
        }
    }

    private Resultado leer(FuenteOfertas fuente, Instant inicio) {
        try {
            FuenteOfertas.Lectura lectura = fuente.leer();
            registro.registrar(fuente.fuente(), lectura, inicio);
            int leidas = lectura.ofertas().size();
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.CORRECTA, null, inicio, leidas);
            log.info("[OFERTAS] {}: {} ofertas leídas", fuente.fuente().nombre(), leidas);
            return new Resultado(EstadoLectura.CORRECTA, leidas);
        } catch (LecturaFallidaException e) {
            fuenteRepository.registrar(fuente.fuente(), e.getEstado(), e.getMessage(), inicio, 0);
            log.warn("[OFERTAS] {}: {} — {}", fuente.fuente().nombre(), e.getEstado(), e.getMessage());
            return new Resultado(e.getEstado(), 0);
        } catch (RuntimeException e) {
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.FALLO, "no se pudo procesar la respuesta",
                    inicio, 0);
            log.error("[OFERTAS] {}: fallo al procesar la lectura", fuente.fuente().nombre(), e);
            return new Resultado(EstadoLectura.FALLO, 0);
        }
    }

    private boolean tomar() {
        synchronized (candado) {
            if (enCurso) {
                return false;
            }
            enCurso = true;
            return true;
        }
    }

    /**
     * Suelta el candado y, si la recolección llegó a mostrarse, la cierra en el mismo paso: quien ve el resultado
     * final ya puede lanzar otra. Una fuente que no llegó a terminar figura como fallida.
     */
    private void soltar(boolean sinAvance) {
        synchronized (candado) {
            enCurso = false;
            if (sinAvance || avance.estado() != EstadoRecoleccion.EN_CURSO) {
                return;
            }
            RecoleccionResponse cerrado = avance;
            for (FuenteEnRecoleccion fuente : avance.fuentes()) {
                if (fuente.paso() != PasoFuente.TERMINADA) {
                    cerrado = cerrado.conFuente(fuente.fuente(), f -> f.terminada(EstadoLectura.FALLO, 0));
                }
            }
            avance = cerrado.terminada(clock.instant());
        }
    }

    private RecoleccionResponse cambiarAvance(UnaryOperator<RecoleccionResponse> cambio) {
        synchronized (candado) {
            avance = cambio.apply(avance);
            return avance;
        }
    }

    private static List<Fuente> fuentesDe(List<FuenteOfertas> lectores) {
        return lectores.stream().map(FuenteOfertas::fuente).toList();
    }

    private record Resultado(EstadoLectura estado, int ofertas) {
    }
}
