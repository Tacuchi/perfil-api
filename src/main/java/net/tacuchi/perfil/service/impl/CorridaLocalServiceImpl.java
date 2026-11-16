package net.tacuchi.perfil.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.dto.CorridaAbiertaResponse;
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.FuenteOmitida;
import net.tacuchi.perfil.dto.OfertaEntregada;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.exception.CorridaRechazadaException;
import net.tacuchi.perfil.exception.RecoleccionRechazadaException;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.FalloMaquina;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoCierre;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.model.OrigenRecoleccion;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.service.CandadoRecoleccion;
import net.tacuchi.perfil.service.CorridaLocalService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Una corrida local a la vez, en memoria como el avance: con una sola instancia de la API basta. Del código solo
 * se guarda su huella SHA-256, y nunca se registra.
 *
 * <p>La lectura del día de una conexión se gasta cuando el lector avisa que empieza a consultar el sitio, no al
 * abrir: un lector que no responde, una corrida que vence antes de leer y una sesión que falta no la gastan.
 *
 * <p>Orden de los monitores: primero el de esta corrida, después el del candado; nunca al revés.
 */
@Slf4j
@Service
public class CorridaLocalServiceImpl implements CorridaLocalService {

    private static final int BYTES_CODIGO = 32;

    private final CandadoRecoleccion candado;
    private final RegistroOfertas registro;
    private final FuenteRepository fuenteRepository;
    private final LecturaDiaRepository lecturaDiaRepository;
    private final ConexionRepository conexionRepository;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Guardada por {@code this}. */
    private Corrida abierta;

    public CorridaLocalServiceImpl(CandadoRecoleccion candado, RegistroOfertas registro,
            FuenteRepository fuenteRepository, LecturaDiaRepository lecturaDiaRepository,
            ConexionRepository conexionRepository, Clock clock) {
        this.candado = candado;
        this.registro = registro;
        this.fuenteRepository = fuenteRepository;
        this.lecturaDiaRepository = lecturaDiaRepository;
        this.conexionRepository = conexionRepository;
        this.clock = clock;
    }

    @Override
    public synchronized CorridaAbiertaResponse abrir() {
        revisarPlazos();
        if (!candado.tomar()) {
            throw RecoleccionRechazadaException.enCurso();
        }
        try {
            Instant inicio = clock.instant();
            LocalDate hoy = LocalDate.ofInstant(inicio, Reglas.ZONA_PERSONA);
            Set<Fuente> pausadas = conexionRepository.pausadas();
            Map<Fuente, Integer> deHoy = lecturaDiaRepository.delDia(hoy);
            List<Fuente> lanzadas = new ArrayList<>();
            List<FuenteOmitida> omitidas = new ArrayList<>();
            for (Fuente fuente : Fuente.values()) {
                if (!fuente.desdeMaquina()) {
                    continue;
                }
                if (pausadas.contains(fuente)) {
                    omitidas.add(FuenteOmitida.de(fuente, MotivoOmision.PAUSADA));
                } else if (deHoy.getOrDefault(fuente, 0) >= fuente.lecturasDia()) {
                    omitidas.add(FuenteOmitida.de(fuente, MotivoOmision.TOPE_DIARIO));
                } else {
                    lanzadas.add(fuente);
                }
            }
            if (lanzadas.isEmpty()) {
                throw RecoleccionRechazadaException.sinConexionesDeMaquina();
            }
            String codigo = nuevoCodigo();
            abierta = new Corrida(huella(codigo), inicio, inicio.plus(Reglas.PLAZO_REPORTE_LECTOR),
                    inicio.plus(Reglas.PLAZO_CORRIDA_LOCAL), lanzadas);
            RecoleccionResponse avance = candado.cambiar(a -> RecoleccionResponse.iniciada(
                    OrigenRecoleccion.MAQUINA, inicio, lanzadas, omitidas));
            log.info("[CORRIDA] abierta desde el panel: {} conexiones, {} omitidas", lanzadas.size(), omitidas.size());
            return new CorridaAbiertaResponse(codigo, avance);
        } catch (RuntimeException e) {
            abierta = null;
            candado.soltar(true);
            throw e;
        }
    }

    @Override
    public synchronized CorridaLectorResponse iniciar(String codigo) {
        Corrida corrida = validar(codigo);
        if (corrida.iniciada) {
            throw CorridaRechazadaException.paso("La corrida ya se inició.");
        }
        corrida.iniciada = true;
        log.info("[CORRIDA] el lector se reportó");
        return new CorridaLectorResponse(
                corrida.pasos.keySet().stream().map(CorridaLectorResponse.FuenteALeer::de).toList(),
                corrida.venceTotal);
    }

    @Override
    public synchronized void empezar(String codigo, Fuente fuente) {
        Corrida corrida = iniciadaCon(codigo);
        if (corrida.pasos.get(fuente) != Paso.PENDIENTE) {
            throw CorridaRechazadaException.paso(pasoFuera(fuente));
        }
        if (conexionRepository.pausadas().contains(fuente)) {
            omitir(corrida, fuente, MotivoOmision.PAUSADA);
        }
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), Reglas.ZONA_PERSONA);
        if (!lecturaDiaRepository.reservar(fuente, hoy, fuente.lecturasDia())) {
            omitir(corrida, fuente, MotivoOmision.TOPE_DIARIO);
        }
        corrida.pasos.put(fuente, Paso.LEYENDO);
        corrida.reservadas.put(fuente, hoy);
        candado.cambiar(a -> a.conFuente(fuente, FuenteEnRecoleccion::leyendo));
        log.info("[CORRIDA] {}: el lector empieza a leer", fuente.nombre());
    }

    @Override
    public synchronized FuenteEnRecoleccion entregar(String codigo, Fuente fuente, EntregaRequest entrega) {
        Corrida corrida = iniciadaCon(codigo);
        if (corrida.pasos.get(fuente) != Paso.LEYENDO) {
            throw CorridaRechazadaException.paso(pasoFuera(fuente));
        }
        List<OfertaEntregada> avisos = entrega.avisos();
        FalloMaquina fallo = entrega.fallo() != null ? entrega.fallo() : avisos.isEmpty() ? FalloMaquina.VACIA : null;
        EstadoLectura estado;
        int leidas;
        if (fallo == null) {
            // Llega cortada por el tope de la entrega: nunca es una lectura completa.
            registro.registrar(fuente, new FuenteOfertas.Lectura(
                    avisos.stream().map(o -> o.comoRecogida(fuente)).toList(), false), corrida.inicio);
            fuenteRepository.registrar(fuente, EstadoLectura.CORRECTA, null, corrida.inicio, avisos.size());
            estado = EstadoLectura.CORRECTA;
            leidas = avisos.size();
            log.info("[CORRIDA] {}: {} ofertas entregadas", fuente.nombre(), leidas);
        } else {
            registrarFallo(corrida, fuente, fallo);
            estado = fallo.estado();
            leidas = 0;
        }
        corrida.pasos.put(fuente, Paso.TERMINADA);
        RecoleccionResponse avance = candado.cambiar(a -> a.conFuente(fuente, f -> f.terminada(estado, leidas)));
        return avance.fuentes().stream().filter(f -> f.fuente() == fuente).findFirst().orElseThrow();
    }

    @Override
    public synchronized RecoleccionResponse terminar(String codigo) {
        validar(codigo);
        abierta = null;
        candado.soltar(false);
        log.info("[CORRIDA] terminada por el lector");
        return candado.avance();
    }

    @Override
    @Scheduled(fixedDelayString = "${perfil.lector.revision-ms:5000}")
    public synchronized void revisarPlazos() {
        if (abierta == null) {
            return;
        }
        Instant ahora = clock.instant();
        if (!abierta.iniciada && !ahora.isBefore(abierta.venceReporte)) {
            cerrar(MotivoCierre.LECTOR_SIN_RESPUESTA);
        } else if (!ahora.isBefore(abierta.venceTotal)) {
            cerrar(MotivoCierre.PLAZO_VENCIDO);
        }
    }

    private void cerrar(MotivoCierre motivo) {
        abierta = null;
        candado.soltar(false, motivo);
        log.warn("[CORRIDA] cerrada sin terminar: {}", motivo);
    }

    /** Falta de sesión devuelve la lectura del día; verificación y ritmo pausan la conexión con su motivo. */
    private void registrarFallo(Corrida corrida, Fuente fuente, FalloMaquina fallo) {
        fuenteRepository.registrar(fuente, fallo.estado(), fallo.detalle(), corrida.inicio, 0);
        if (fallo == FalloMaquina.SIN_SESION) {
            lecturaDiaRepository.devolver(fuente, corrida.reservadas.get(fuente));
        }
        if (fallo.pausa()) {
            conexionRepository.pausar(fuente, true, fallo.detalle());
        }
        log.warn("[CORRIDA] {}: {}", fuente.nombre(), fallo.detalle());
    }

    /** La conexión no se lee en esta corrida: pasa a las omitidas y el lector recibe el rechazo con su motivo. */
    private void omitir(Corrida corrida, Fuente fuente, MotivoOmision motivo) {
        corrida.pasos.put(fuente, Paso.TERMINADA);
        candado.cambiar(a -> a.sinFuente(fuente, motivo));
        throw RecoleccionRechazadaException.omitida(FuenteOmitida.de(fuente, motivo), fuente.lecturasDia());
    }

    private Corrida iniciadaCon(String codigo) {
        Corrida corrida = validar(codigo);
        if (!corrida.iniciada) {
            throw CorridaRechazadaException.paso("La corrida aún no se inició.");
        }
        return corrida;
    }

    /** Vence lo que corresponda y compara huellas en tiempo constante. */
    private Corrida validar(String codigo) {
        revisarPlazos();
        if (abierta == null || codigo == null || !MessageDigest.isEqual(abierta.huella, huella(codigo))) {
            throw CorridaRechazadaException.codigo();
        }
        return abierta;
    }

    private String nuevoCodigo() {
        byte[] bytes = new byte[BYTES_CODIGO];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] huella(String codigo) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("el JDK no trae SHA-256", e);
        }
    }

    private static String pasoFuera(Fuente fuente) {
        return fuente.nombre() + " no está en ese paso de la corrida.";
    }

    private enum Paso {
        PENDIENTE, LEYENDO, TERMINADA
    }

    private static final class Corrida {
        final byte[] huella;
        final Instant inicio;
        final Instant venceReporte;
        final Instant venceTotal;
        final Map<Fuente, Paso> pasos = new EnumMap<>(Fuente.class);
        final Map<Fuente, LocalDate> reservadas = new EnumMap<>(Fuente.class);
        boolean iniciada;

        Corrida(byte[] huella, Instant inicio, Instant venceReporte, Instant venceTotal, List<Fuente> fuentes) {
            this.huella = huella;
            this.inicio = inicio;
            this.venceReporte = venceReporte;
            this.venceTotal = venceTotal;
            fuentes.forEach(f -> pasos.put(f, Paso.PENDIENTE));
        }
    }
}
