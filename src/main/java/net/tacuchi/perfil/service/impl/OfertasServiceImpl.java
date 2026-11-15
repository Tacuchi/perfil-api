package net.tacuchi.perfil.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.clasificacion.CatalogoTecnologias;
import net.tacuchi.perfil.dto.ConsultaOfertas;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.RepetidaResponse;
import net.tacuchi.perfil.dto.TecnologiasResponse;
import net.tacuchi.perfil.exception.EstadoNoPermitidoException;
import net.tacuchi.perfil.exception.OfertaNoEncontradaException;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.EstadoOferta;
import net.tacuchi.perfil.model.FiltroOfertas;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.LecturaFuente;
import net.tacuchi.perfil.model.OfertaGuardada;
import net.tacuchi.perfil.model.Repetida;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.FuenteRepository;
import net.tacuchi.perfil.repository.LecturaDiaRepository;
import net.tacuchi.perfil.repository.OfertaRepository;
import net.tacuchi.perfil.service.OfertasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfertasServiceImpl implements OfertasService {

    static final Set<Encaje> ENCAJES_POR_DEFECTO = EnumSet.of(Encaje.ALTO, Encaje.MEDIO);
    static final Set<EstadoOferta> ESTADOS_POR_DEFECTO = EnumSet.of(EstadoOferta.NUEVA);

    private final OfertaRepository ofertaRepository;
    private final FuenteRepository fuenteRepository;
    private final LecturaDiaRepository lecturaDiaRepository;
    private final CatalogoTecnologias catalogo;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<OfertaResponse> listar(ConsultaOfertas consulta) {
        List<OfertaGuardada> ofertas = ofertaRepository.listar(filtro(consulta));
        Map<String, List<Repetida>> porClave = ofertaRepository
                .conClaves(ofertas.stream().map(OfertaGuardada::claveSimilitud).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(Repetida::claveSimilitud));
        return ofertas.stream().map(o -> resumen(o, porClave.getOrDefault(o.claveSimilitud(), List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OfertaDetalleResponse detalle(long id) {
        OfertaGuardada oferta = ofertaRepository.buscar(id).orElseThrow(() -> new OfertaNoEncontradaException(id));
        List<Repetida> similares = ofertaRepository.conClaves(Set.of(oferta.claveSimilitud()));
        return new OfertaDetalleResponse(resumen(oferta, similares), oferta.descripcion(), oferta.correoContacto());
    }

    @Override
    @Transactional
    public OfertaDetalleResponse cambiarEstado(long id, EstadoOferta estado) {
        if (estado == EstadoOferta.NUEVA) {
            throw new EstadoNoPermitidoException(estado.name());
        }
        Instant ahora = clock.instant();
        if (!ofertaRepository.cambiarEstado(id, estado, ahora)) {
            throw new OfertaNoEncontradaException(id);
        }
        return detalle(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FuenteResponse> fuentes() {
        Map<Fuente, LecturaFuente> lecturas = fuenteRepository.todas().stream()
                .collect(Collectors.toMap(LecturaFuente::fuente, Function.identity()));
        Map<Fuente, Integer> deHoy = lecturaDiaRepository.delDia(LocalDate.ofInstant(clock.instant(), Reglas.ZONA_PERSONA));
        return Arrays.stream(Fuente.values()).map(fuente -> {
            LecturaFuente lectura = lecturas.get(fuente);
            int restantes = Math.max(0, Reglas.TOPE_LECTURAS_DIA - deHoy.getOrDefault(fuente, 0));
            if (lectura == null) {
                return new FuenteResponse(fuente, fuente.nombre(), EstadoLectura.SIN_LEER, null, null, null, 0,
                        restantes);
            }
            return new FuenteResponse(fuente, fuente.nombre(), lectura.estado(), lectura.detalle(),
                    lectura.ultimaLectura(), lectura.ultimaCorrecta(), lectura.ofertasLeidas(), restantes);
        }).toList();
    }

    @Override
    public TecnologiasResponse tecnologias() {
        return new TecnologiasResponse(
                catalogo.principales().stream().map(CatalogoTecnologias.Tecnologia::nombre).toList(),
                catalogo.secundarias().stream().map(CatalogoTecnologias.Tecnologia::nombre).toList());
    }

    static FiltroOfertas filtro(ConsultaOfertas c) {
        String tecnologia = c.tecnologia() == null || c.tecnologia().isBlank() ? null : c.tecnologia().strip();
        return new FiltroOfertas(
                vacioEs(c.encaje(), ENCAJES_POR_DEFECTO),
                vacioEs(c.estado(), ESTADOS_POR_DEFECTO),
                vacioEs(c.fuente(), null),
                vacioEs(c.modalidad(), null),
                tecnologia,
                inicioDelDia(c.desde()),
                inicioDelDia(c.hasta() == null ? null : c.hasta().plusDays(1)));
    }

    private static <E extends Enum<E>> Set<E> vacioEs(List<E> valores, Set<E> porDefecto) {
        return valores == null || valores.isEmpty() ? porDefecto : Set.copyOf(valores);
    }

    private static Instant inicioDelDia(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(Reglas.ZONA_PERSONA).toInstant();
    }

    private static OfertaResponse resumen(OfertaGuardada o, List<Repetida> similares) {
        List<RepetidaResponse> repetidas = similares.stream()
                .filter(r -> r.fuente() != o.fuente())
                .map(r -> new RepetidaResponse(r.id(), r.fuente(), r.fuente().nombre()))
                .toList();
        return new OfertaResponse(o.id(), o.fuente(), o.fuente().nombre(), o.titulo(), o.empresa(), o.fecha(),
                o.ubicacion(), o.modalidad(), o.url(), o.encaje(), o.motivos(), o.tecnologias(), o.estado(),
                o.postuladaEn(), o.publicada(), o.correoContacto() != null, repetidas);
    }
}
