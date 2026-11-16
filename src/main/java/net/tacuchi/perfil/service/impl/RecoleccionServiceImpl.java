package net.tacuchi.perfil.service.impl;

import net.tacuchi.perfil.repository.HistorialLecturasRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.exception.RecoleccionRechazadaException;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.fuentes.LecturaFallidaException;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.repository.OfertaRepository;
import net.tacuchi.perfil.service.AvisoService;
import net.tacuchi.perfil.service.CandadoRecoleccion;
import net.tacuchi.perfil.service.ConexionesPago;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cada lectura se cuenta por fuente y día de Lima antes de pedirla al sitio: ni un reinicio ni un fallo dejan
 * pasar el tope. Un fallo deja lo ya recogido y no prueba otra vía. Una conexión pausada no se lee ni se cuenta.
 * Las conexiones de la máquina de la persona no tienen lector aquí: se omiten diciéndolo. Una de pago sin lo que
 * necesita, o que ya consumió su tope del mes, se omite sin ninguna consulta al servicio; no tiene tope diario.
 *
 * <p>Una sola recolección a la vez, con el candado y el avance de {@link CandadoRecoleccion}.
 */
@Slf4j
@Service
public class RecoleccionServiceImpl implements RecoleccionService {

    private final List<FuenteOfertas> fuentes;
    private final RegistroOfertas registro;
    private final FuenteRepository fuenteRepository;
    private final LecturaDiaRepository lecturaDiaRepository;
    private final ConexionRepository conexionRepository;
    private final ConexionesPago conexionesPago;
    private final OfertaRepository ofertaRepository;
    private final AvisoService avisoService;
    private final TaskExecutor ejecutor;
    private final CandadoRecoleccion candado;
    private final Clock clock;
    private final HistorialLecturasRepository historial;

    public RecoleccionServiceImpl(List<FuenteOfertas> fuentes, RegistroOfertas registro,
            FuenteRepository fuenteRepository, LecturaDiaRepository lecturaDiaRepository,
            ConexionRepository conexionRepository, ConexionesPago conexionesPago, OfertaRepository ofertaRepository,
            AvisoService avisoService,
            @Qualifier("applicationTaskExecutor") TaskExecutor ejecutor, CandadoRecoleccion candado, Clock clock,
            HistorialLecturasRepository historial) {
        this.fuentes = fuentes.stream().sorted(Comparator.comparing(FuenteOfertas::fuente)).toList();
        this.registro = registro;
        this.fuenteRepository = fuenteRepository;
        this.lecturaDiaRepository = lecturaDiaRepository;
        this.conexionRepository = conexionRepository;
        this.conexionesPago = conexionesPago;
        this.ofertaRepository = ofertaRepository;
        this.avisoService = avisoService;
        this.ejecutor = ejecutor;
        this.candado = candado;
        this.clock = clock;
        this.historial = historial;
    }

    @Scheduled(cron = "${perfil.ofertas.cron}", zone = "America/Lima")
    public void programada() {
        recoger();
    }

    @Override
    public void recoger() {
        if (!candado.tomar()) {
            log.info("[OFERTAS] ya hay una recolección en curso; la programada se omite");
            return;
        }
        Instant inicio = clock.instant();
        List<FuenteOfertas> lanzadas;
        try {
            lanzadas = reservarProgramada(inicio);
        } catch (RuntimeException e) {
            candado.soltar(true);
            throw e;
        }
        correr(lanzadas, inicio);
    }

    /**
     * Reserva la lectura del día de cada fuente activa que hoy no tiene ninguna y muestra el avance si hay algo que
     * leer, con las pausadas y las de la máquina como omitidas.
     */
    private List<FuenteOfertas> reservarProgramada(Instant inicio) {
        LocalDate hoy = LocalDate.ofInstant(inicio, Reglas.ZONA_PERSONA);
        Set<Fuente> pausadas = conexionRepository.pausadas();
        List<FuenteOmitida> omitidas = new ArrayList<>();
        List<FuenteOfertas> lanzadas = new ArrayList<>();
        for (FuenteOfertas fuente : fuentes) {
            Optional<MotivoOmision> impedimento = impedimento(fuente.fuente(), pausadas);
            if (impedimento.isPresent()) {
                omitidas.add(omision(fuente.fuente(), impedimento.get()));
            } else if (lecturaDiaRepository.reservar(fuente.fuente(), hoy, Reglas.LECTURAS_PROGRAMADAS_DIA)) {
                lanzadas.add(fuente);
            }
        }
        omitidas.addAll(desdeMaquina());
        if (!lanzadas.isEmpty()) {
            // Una programada sin nada que leer no reemplaza el último resultado.
            candado.cambiar(a -> RecoleccionResponse.iniciada(OrigenRecoleccion.PROGRAMADA, inicio,
                    fuentesDe(lanzadas), omitidas));
            log.info("[OFERTAS] la programada omite {} conexiones pausadas o de la máquina", omitidas.size());
        }
        return List.copyOf(lanzadas);
    }

    @Override
    public RecoleccionResponse lanzar() {
        return lanzarDemanda(fuentes, true);
    }

    @Override
    public RecoleccionResponse lanzar(Fuente fuente) {
        if (fuente.desdeMaquina()) {
            throw RecoleccionRechazadaException.omitida(FuenteOmitida.de(fuente, MotivoOmision.DESDE_MAQUINA),
                    fuente.lecturasDia());
        }
        FuenteOfertas pedida = fuentes.stream().filter(f -> f.fuente() == fuente).findFirst()
                .orElseThrow(() -> new IllegalStateException("fuente sin lector: " + fuente));
        return lanzarDemanda(List.of(pedida), false);
    }

    @Override
    public RecoleccionResponse avance() {
        return candado.avance();
    }

    private RecoleccionResponse lanzarDemanda(List<FuenteOfertas> pedidas, boolean general) {
        if (!candado.tomar()) {
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
                Optional<MotivoOmision> impedimento = impedimento(fuente.fuente(), pausadas);
                if (impedimento.isPresent()) {
                    omitidas.add(omision(fuente.fuente(), impedimento.get()));
                } else if (lecturaDiaRepository.reservar(fuente.fuente(), hoy, topeDelDia(fuente.fuente()))) {
                    lanzadas.add(fuente);
                } else {
                    omitidas.add(FuenteOmitida.de(fuente.fuente(), MotivoOmision.TOPE_DIARIO));
                }
            }
            if (general) {
                omitidas.addAll(desdeMaquina());
            }
            if (lanzadas.isEmpty()) {
                throw general ? RecoleccionRechazadaException.sinFuentes(omitidas, Reglas.TOPE_LECTURAS_DIA)
                        : RecoleccionRechazadaException.omitida(omitidas.getFirst(), Reglas.TOPE_LECTURAS_DIA);
            }
            RecoleccionResponse respuesta = candado.cambiar(a -> RecoleccionResponse.iniciada(
                    OrigenRecoleccion.DEMANDA, inicio, fuentesDe(lanzadas), omitidas));
            iniciada = true;
            log.info("[OFERTAS] recolección a demanda lanzada: {} fuentes, {} omitidas",
                    lanzadas.size(), omitidas.size());
            ejecutor.execute(() -> correr(List.copyOf(lanzadas), inicio));
            return respuesta;
        } catch (RuntimeException e) {
            candado.soltar(!iniciada);
            throw e;
        }
    }

    /** Lee las fuentes ya reservadas, depura y avisa; pase lo que pase, cierra el avance y suelta el candado. */
    private void correr(List<FuenteOfertas> lanzadas, Instant inicio) {
        try {
            for (FuenteOfertas fuente : lanzadas) {
                candado.cambiar(a -> a.conFuente(fuente.fuente(), FuenteEnRecoleccion::leyendo));
                Resultado resultado = leer(fuente, inicio);
                candado.cambiar(a -> a.conFuente(fuente.fuente(),
                        f -> f.terminada(resultado.estado(), resultado.ofertas(), resultado.costoUsd(), resultado.detalle())));
            }
            int eliminadas = ofertaRepository.eliminarVistasAntesDe(inicio.minus(Reglas.CONSERVACION_OFERTAS));
            if (eliminadas > 0) {
                log.info("[OFERTAS] {} ofertas eliminadas por conservación", eliminadas);
            }
            avisoService.avisarSiCorresponde();
        } catch (RuntimeException e) {
            log.error("[OFERTAS] la recolección se interrumpió por un fallo inesperado", e);
        } finally {
            candado.soltar(lanzadas.isEmpty());
        }
    }

    private Resultado leer(FuenteOfertas fuente, Instant inicio) {
        Instant comienzo = clock.instant();
        Resultado resultado = procesar(fuente, inicio);
        // El historial es independiente: un fallo de escritura no cambia la última lectura ni corta las demás.
        try {
            historial.registrar(fuente.fuente(), comienzo,
                    Math.max(0, Duration.between(comienzo, clock.instant()).toMillis()),
                    resultado.estado(), resultado.ofertas(), resultado.nuevas(), resultado.costoUsd(), resultado.detalle());
        } catch (RuntimeException e) {
            log.error("[OFERTAS] {}: no se pudo guardar el historial", fuente.fuente().nombre(), e);
        }
        return resultado;
    }

    private Resultado procesar(FuenteOfertas fuente, Instant inicio) {
        try {
            FuenteOfertas.Lectura lectura = fuente.leer();
            int previas = ofertaRepository.nuevasDesde(fuente.fuente(), inicio);
            registro.registrar(fuente.fuente(), lectura, inicio);
            int nuevas = ofertaRepository.nuevasDesde(fuente.fuente(), inicio) - previas;
            int leidas = lectura.ofertas().size();
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.CORRECTA, lectura.detalle(), inicio, leidas);
            log.info("[OFERTAS] {}: {} ofertas leídas", fuente.fuente().nombre(), leidas);
            return new Resultado(EstadoLectura.CORRECTA, leidas, nuevas, lectura.costoUsd(), lectura.detalle());
        } catch (LecturaFallidaException e) {
            fuenteRepository.registrar(fuente.fuente(), e.getEstado(), e.getMessage(), inicio, 0);
            log.warn("[OFERTAS] {}: {} — {}", fuente.fuente().nombre(), e.getEstado(), e.getMessage());
            return new Resultado(e.getEstado(), 0, 0, e.getCostoUsd(), e.getMessage());
        } catch (RuntimeException e) {
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.FALLO, "no se pudo procesar la respuesta",
                    inicio, 0);
            log.error("[OFERTAS] {}: fallo al procesar la lectura", fuente.fuente().nombre(), e);
            return new Resultado(EstadoLectura.FALLO, 0, 0, null, "no se pudo procesar la respuesta");
        }
    }

    private FuenteOmitida omision(Fuente fuente, MotivoOmision motivo) {
        return new FuenteOmitida(fuente, fuente.nombre(), motivo,
                fuente.dePago() ? conexionesPago.consumoDelMes(fuente) : null,
                fuente.dePago() ? conexionRepository.buscar(fuente).topeMensual() : null);
    }

    /** Pausada, o de pago sin lo que necesita o con su tope del mes consumido: no se lee ni se cuenta. */
    private Optional<MotivoOmision> impedimento(Fuente fuente, Set<Fuente> pausadas) {
        return pausadas.contains(fuente) ? Optional.of(MotivoOmision.PAUSADA) : conexionesPago.impedimento(fuente);
    }

    /** Las de pago no tienen tope diario; igual se cuentan, para que la programada no las repita en el día. */
    private static int topeDelDia(Fuente fuente) {
        return fuente.conTopeDiario() ? fuente.lecturasDia() : Integer.MAX_VALUE;
    }

    /** Las conexiones que solo lee el lector de la máquina de la persona, como omitidas. */
    private static List<FuenteOmitida> desdeMaquina() {
        return Arrays.stream(Fuente.values()).filter(Fuente::desdeMaquina)
                .map(f -> FuenteOmitida.de(f, MotivoOmision.DESDE_MAQUINA)).toList();
    }

    private static List<Fuente> fuentesDe(List<FuenteOfertas> lectores) {
        return lectores.stream().map(FuenteOfertas::fuente).toList();
    }

    private record Resultado(EstadoLectura estado, int ofertas, int nuevas, java.math.BigDecimal costoUsd, String detalle) {
    }
}
