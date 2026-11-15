package net.tacuchi.perfil.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.fuentes.LecturaFallidaException;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.LecturaFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.OfertaRepository;
import net.tacuchi.perfil.service.AvisoService;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Una lectura por fuente y por día de Lima, contada en la base de datos: ni un reinicio ni un fallo
 * provocan una segunda lectura ese día. Un fallo deja lo ya recogido y no prueba otra vía.
 */
@Slf4j
@Service
public class RecoleccionServiceImpl implements RecoleccionService {

    private final List<FuenteOfertas> fuentes;
    private final RegistroOfertas registro;
    private final FuenteRepository fuenteRepository;
    private final OfertaRepository ofertaRepository;
    private final AvisoService avisoService;
    private final Clock clock;
    private final ReentrantLock enCurso = new ReentrantLock();

    public RecoleccionServiceImpl(List<FuenteOfertas> fuentes, RegistroOfertas registro,
            FuenteRepository fuenteRepository, OfertaRepository ofertaRepository, AvisoService avisoService,
            Clock clock) {
        this.fuentes = fuentes;
        this.registro = registro;
        this.fuenteRepository = fuenteRepository;
        this.ofertaRepository = ofertaRepository;
        this.avisoService = avisoService;
        this.clock = clock;
    }

    @Scheduled(cron = "${perfil.ofertas.cron}", zone = "America/Lima")
    public void programada() {
        recoger();
    }

    @Override
    public void recoger() {
        if (!enCurso.tryLock()) {
            log.info("[OFERTAS] ya hay una recolección en curso; esta se omite");
            return;
        }
        try {
            Instant inicio = clock.instant();
            LocalDate hoy = LocalDate.ofInstant(inicio, Reglas.ZONA_PERSONA);
            for (FuenteOfertas fuente : fuentes) {
                if (leidaEl(fuente, hoy)) {
                    continue;
                }
                leer(fuente, inicio);
            }
            int eliminadas = ofertaRepository.eliminarVistasAntesDe(inicio.minus(Reglas.CONSERVACION_OFERTAS));
            if (eliminadas > 0) {
                log.info("[OFERTAS] {} ofertas eliminadas por conservación", eliminadas);
            }
            avisoService.avisarSiCorresponde();
        } finally {
            enCurso.unlock();
        }
    }

    private boolean leidaEl(FuenteOfertas fuente, LocalDate dia) {
        return fuenteRepository.buscar(fuente.fuente())
                .map(LecturaFuente::ultimaLectura)
                .map(ultima -> LocalDate.ofInstant(ultima, Reglas.ZONA_PERSONA).equals(dia))
                .orElse(false);
    }

    private void leer(FuenteOfertas fuente, Instant inicio) {
        try {
            FuenteOfertas.Lectura lectura = fuente.leer();
            registro.registrar(fuente.fuente(), lectura, inicio);
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.CORRECTA, null, inicio, lectura.ofertas().size());
            log.info("[OFERTAS] {}: {} ofertas leídas", fuente.fuente().nombre(), lectura.ofertas().size());
        } catch (LecturaFallidaException e) {
            fuenteRepository.registrar(fuente.fuente(), e.getEstado(), e.getMessage(), inicio, 0);
            log.warn("[OFERTAS] {}: {} — {}", fuente.fuente().nombre(), e.getEstado(), e.getMessage());
        } catch (RuntimeException e) {
            fuenteRepository.registrar(fuente.fuente(), EstadoLectura.FALLO, "no se pudo procesar la respuesta",
                    inicio, 0);
            log.error("[OFERTAS] {}: fallo al procesar la lectura", fuente.fuente().nombre(), e);
        }
    }
}
