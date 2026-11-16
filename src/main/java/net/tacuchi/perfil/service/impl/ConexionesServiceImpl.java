package net.tacuchi.perfil.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.exception.ConfiguracionRechazadaException;
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.LecturaFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.service.CifradoClaves;
import net.tacuchi.perfil.service.ConexionesPago;
import net.tacuchi.perfil.service.ConexionesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConexionesServiceImpl implements ConexionesService {

    private final FuenteRepository fuenteRepository;
    private final LecturaDiaRepository lecturaDiaRepository;
    private final ConexionRepository conexionRepository;
    private final ConexionesPago conexionesPago;
    private final CifradoClaves cifrado;
    private final Clock clock;
    private final net.tacuchi.perfil.service.ClaveApify claveApify;

    @Override
    @Transactional(readOnly = true)
    public List<FuenteResponse> listar() {
        Map<Fuente, LecturaFuente> lecturas = fuenteRepository.todas().stream()
                .collect(Collectors.toMap(LecturaFuente::fuente, Function.identity()));
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), Reglas.ZONA_PERSONA);
        Map<Fuente, Integer> deHoy = lecturaDiaRepository.delDia(hoy);
        Map<Fuente, Conexion> conexiones = conexionRepository.todas();
        boolean llave = cifrado.disponible();
        return Arrays.stream(Fuente.values())
                .map(fuente -> respuesta(fuente, lecturas.get(fuente), deHoy.getOrDefault(fuente, 0),
                        fuente.dePago() ? claveApify.proyectar(conexiones.get(fuente)) : conexiones.get(fuente), hoy, llave,
                        fuente.dePago() ? conexionesPago.consumoDelMes(fuente) : BigDecimal.ZERO))
                .toList();
    }

    @Override
    @Transactional
    public FuenteResponse pausar(Fuente fuente) {
        conexionRepository.pausar(fuente, true, null);
        log.info("[CONEXIONES] {} pausada por la persona", fuente.nombre());
        return de(fuente);
    }

    @Override
    @Transactional
    public FuenteResponse reanudar(Fuente fuente) {
        conexionRepository.pausar(fuente, false, null);
        log.info("[CONEXIONES] {} reanudada", fuente.nombre());
        return de(fuente);
    }

    @Override
    @Transactional
    public FuenteResponse cargarClave(Fuente fuente, String clave) {
        exigirPago(fuente);
        if (!cifrado.disponible()) {
            throw ConfiguracionRechazadaException.faltaLlave();
        }
        claveApify.guardar(clave);
        log.info("[CONEXIONES] {}: clave cargada", fuente.nombre());
        return de(fuente);
    }

    @Override
    @Transactional
    public FuenteResponse fijarTope(Fuente fuente, BigDecimal tope) {
        exigirPago(fuente);
        conexionRepository.fijarTope(fuente, tope);
        log.info("[CONEXIONES] {}: tope mensual fijado en {} USD", fuente.nombre(), tope.toPlainString());
        return de(fuente);
    }

    private static void exigirPago(Fuente fuente) {
        if (!fuente.dePago()) {
            throw ConfiguracionRechazadaException.noEsDePago(fuente);
        }
    }

    private FuenteResponse de(Fuente fuente) {
        return listar().stream().filter(f -> f.fuente() == fuente).findFirst().orElseThrow();
    }

    private static FuenteResponse respuesta(Fuente fuente, LecturaFuente lectura, int lecturasHoy, Conexion conexion,
            LocalDate hoy, boolean llave, BigDecimal consumoMes) {
        int restantes = fuente.conTopeDiario() ? Math.max(0, fuente.lecturasDia() - lecturasHoy) : 0;
        boolean leida = lectura != null;
        boolean pago = fuente.dePago();
        return new FuenteResponse(fuente, fuente.nombre(),
                leida ? lectura.estado() : EstadoLectura.SIN_LEER,
                leida ? lectura.detalle() : null,
                leida ? lectura.ultimaLectura() : null,
                leida ? lectura.ultimaCorrecta() : null,
                leida ? lectura.ofertasLeidas() : 0,
                restantes,
                fuente.via(), fuente.necesita(), conexion.tieneLoQueNecesita(lectura, llave), conexion.pausada(),
                conexion.motivoPausa(), conexion.estado(lectura, hoy, llave, consumoMes), fuente.lecturasDia(),
                fuente.avisosPorEntrega(),
                conexion.claveCargadaEn() != null,
                conexion.claveCargadaEn(),
                pago ? conexion.topeMensual() : null,
                pago ? consumoMes : null,
                conexion.queFalta(llave).orElse(null), leida && lectura.estado().parcial(lectura.detalle()));
    }
}
