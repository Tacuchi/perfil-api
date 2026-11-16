package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.config.ActoresApifyProperties;
import net.tacuchi.perfil.model.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class BusquedasApify {
    private final CicloApify ciclo;
    public record Busqueda(String ambito, String termino, int cuota, JsonNode entrada) {
        String nombre() { return ambito + " · " + termino; }
    }

    private record Advertencia(Busqueda busqueda, String motivo, boolean fallo) { }
    private record Grupo(String ambito, String motivo, boolean fallo) { }

    private static String agrupar(List<Advertencia> advertencias) {
        Map<Grupo, Set<String>> grupos = new LinkedHashMap<>();
        for (Advertencia advertencia : advertencias) {
            var grupo = new Grupo(advertencia.busqueda().ambito(), advertencia.motivo(), advertencia.fallo());
            grupos.computeIfAbsent(grupo, g -> new LinkedHashSet<>()).add(advertencia.busqueda().termino());
        }
        List<String> motivos = new ArrayList<>();
        grupos.forEach((grupo, terminos) -> {
            if (terminos.size() == 1) {
                motivos.add(grupo.ambito() + " · " + terminos.iterator().next() + ": " + grupo.motivo());
            } else {
                motivos.add(grupo.ambito() + ": " + (grupo.fallo() ? "fallaron las " : "") + terminos.size()
                        + " búsquedas (" + String.join(", ", terminos) + "): " + grupo.motivo());
            }
        });
        return motivos.isEmpty() ? null : String.join("; ", motivos);
    }

    private static OfertaRecogida preferirModalidad(OfertaRecogida anterior, OfertaRecogida nueva) {
        return anterior.modalidad() == Modalidad.NO_INFORMA && nueva.modalidad() == Modalidad.REMOTO
                ? nueva : anterior;
    }

    public FuenteOfertas.Lectura leer(Fuente fuente, ActoresApifyProperties.Actor actor, List<Busqueda> busquedas,
            BigDecimal arranque, BigDecimal precio, BiFunction<Busqueda, JsonNode, OfertaRecogida> convertir) {
        Map<String, OfertaRecogida> ofertas = new LinkedHashMap<>();
        List<Advertencia> advertencias = new ArrayList<>();
        BigDecimal costo = BigDecimal.ZERO;
        EstadoLectura estado = EstadoLectura.FALLO;
        boolean detener = false;
        for (Busqueda busqueda : busquedas) {
            if (detener) {
                advertencias.add(new Advertencia(busqueda, "omitida para no autorizar más gasto", false));
                continue;
            }
            try {
                var resultado = ciclo.ejecutar(fuente, actor.url(), actor.actor(), busqueda.entrada(), busqueda.cuota(),
                        arranque, precio);
                costo = costo.add(resultado.costo());
                estado = resultado.estado();
                detener = resultado.detener();
                if (resultado.detalle() != null) { advertencias.add(new Advertencia(busqueda, resultado.detalle(), resultado.estado() != EstadoLectura.CORRECTA)); }
                int validos = 0;
                for (JsonNode aviso : resultado.avisos()) {
                    try {
                        OfertaRecogida oferta = convertir.apply(busqueda, aviso);
                        ofertas.merge(oferta.idExterno(), oferta, BusquedasApify::preferirModalidad);
                        validos++;
                    } catch (IllegalArgumentException e) {
                        advertencias.add(new Advertencia(busqueda, e.getMessage(), true));
                    }
                }
                if (validos == 0 && resultado.estado() == EstadoLectura.CORRECTA) {
                    advertencias.add(new Advertencia(busqueda, "el servicio no devolvió avisos válidos", true));
                }
            } catch (LecturaFallidaException e) {
                advertencias.add(new Advertencia(busqueda, e.getMessage(), true));
                estado = e.getEstado();
                detener = true;
            }
        }
        String detalle = agrupar(advertencias);
        if (ofertas.isEmpty()) {
            throw new LecturaFallidaException(estado == EstadoLectura.BLOQUEO ? estado : EstadoLectura.FALLO,
                    detalle == null ? "el servicio no devolvió avisos válidos" : detalle, costo);
        }
        return new FuenteOfertas.Lectura(List.copyOf(ofertas.values()), false, costo, detalle);
    }
}
