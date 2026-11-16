package net.tacuchi.perfil.service.impl;

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
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.LecturaFuente;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
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
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<FuenteResponse> listar() {
        Map<Fuente, LecturaFuente> lecturas = fuenteRepository.todas().stream()
                .collect(Collectors.toMap(LecturaFuente::fuente, Function.identity()));
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), Reglas.ZONA_PERSONA);
        Map<Fuente, Integer> deHoy = lecturaDiaRepository.delDia(hoy);
        Map<Fuente, Conexion> conexiones = conexionRepository.todas();
        return Arrays.stream(Fuente.values())
                .map(fuente -> respuesta(fuente, lecturas.get(fuente), deHoy.getOrDefault(fuente, 0),
                        conexiones.get(fuente), hoy))
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

    private FuenteResponse de(Fuente fuente) {
        return listar().stream().filter(f -> f.fuente() == fuente).findFirst().orElseThrow();
    }

    private static FuenteResponse respuesta(Fuente fuente, LecturaFuente lectura, int lecturasHoy, Conexion conexion,
            LocalDate hoy) {
        int restantes = Math.max(0, fuente.lecturasDia() - lecturasHoy);
        boolean leida = lectura != null;
        return new FuenteResponse(fuente, fuente.nombre(),
                leida ? lectura.estado() : EstadoLectura.SIN_LEER,
                leida ? lectura.detalle() : null,
                leida ? lectura.ultimaLectura() : null,
                leida ? lectura.ultimaCorrecta() : null,
                leida ? lectura.ofertasLeidas() : 0,
                restantes,
                fuente.via(), fuente.necesita(), conexion.tieneLoQueNecesita(lectura), conexion.pausada(),
                conexion.motivoPausa(), conexion.estado(lectura, hoy), fuente.lecturasDia(), fuente.avisosPorEntrega());
    }
}
