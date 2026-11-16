package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.ConsumoRepository;
import net.tacuchi.perfil.service.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Un lanzamiento por vez; su cargo queda asentado antes de autorizar el siguiente. */
@lombok.extern.slf4j.Slf4j
@Component
@RequiredArgsConstructor
public class CicloApify {
    private static final Set<String> TERMINADAS = Set.of("SUCCEEDED", "FAILED", "TIMED-OUT", "ABORTED");
    private static final String EVENTO_AVISO = "apify-default-dataset-item";
    private final ClienteApify apify;
    private final PerfilProperties propiedades;
    private final ConexionRepository conexiones;
    private final ConsumoRepository consumo;
    private final ConexionesPago pagos;
    private final ClaveApify claveApify;
    private final JsonMapper json;
    private final Clock clock;

    public record Resultado(JsonNode avisos, BigDecimal costo, String detalle, EstadoLectura estado, boolean detener) { }

    public Resultado ejecutar(Fuente fuente, String url, String actor, JsonNode entrada, int cuota,
            BigDecimal arranque, BigDecimal precioAviso) {
        var conexion = claveApify.proyectar(conexiones.buscar(fuente));
        if (conexion.pausada()) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la conexión está pausada");
        }
        conexion.queFalta(pagos.llaveDisponible()).ifPresent(falta -> {
            throw new LecturaFallidaException(EstadoLectura.FALLO, falta);
        });
        BigDecimal gastado = pagos.consumoDelMes(fuente);
        BigDecimal saldo = conexion.saldo(gastado).setScale(4, RoundingMode.DOWN);
        if (saldo.compareTo(arranque.add(precioAviso)) < 0) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "saldo insuficiente para un aviso; gastado "
                    + gastado.toPlainString() + " USD, tope del mes " + conexion.topeMensual().toPlainString() + " USD");
        }
        String clave;
        try {
            clave = claveApify.leer();
        } catch (CifradoClaves.ClaveIlegibleException e) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, e.getMessage());
        }
        BigDecimal autorizado = saldo.min(arranque.add(precioAviso.multiply(BigDecimal.valueOf(cuota))));
        var config = propiedades.computrabajo();
        var mes = ConsumoRepository.mesDe(clock.instant());
        JsonNode corrida = null;
        JsonNode avisos = json.createArrayNode();
        String detalle = null;
        EstadoLectura estado = EstadoLectura.CORRECTA;
        boolean detener = false;
        boolean sinCargo = false;
        boolean incierto = false;
        try {
            Instant limite = Instant.now().plus(config.plazoCorrida());
            corrida = apify.lanzar(url, actor, clave, entrada, autorizado, cuota, config.plazoCorrida(),
                    ClienteApify.ESPERA_MAXIMA_SEGUNDOS);
            if (corrida.path("id").asString("").isBlank()) {
                throw new LecturaFallidaException(EstadoLectura.FALLO, "el servicio no identificó la corrida");
            }
            while (!TERMINADAS.contains(corrida.path("status").asString(""))) {
                long restantes = Duration.between(Instant.now(), limite).toSeconds();
                if (restantes <= 0) {
                    throw new LecturaFallidaException(EstadoLectura.FALLO, "la corrida del servicio no terminó a tiempo");
                }
                corrida = apify.corrida(url, clave, corrida.path("id").asString(),
                        (int) Math.min(restantes, ClienteApify.ESPERA_MAXIMA_SEGUNDOS));
            }
            if (!"SUCCEEDED".equals(corrida.path("status").asString())) {
                String estadoCorrida = corrida.path("status").asString("");
                log.warn("[APIFY] {}: corrida en estado {}", fuente, estadoCorrida);
                String motivo = switch (estadoCorrida) {
                    case "TIMED-OUT" -> "el servicio agotó el plazo de la búsqueda";
                    case "ABORTED" -> "el servicio interrumpió la búsqueda";
                    default -> "el servicio terminó con fallo";
                };
                throw new LecturaFallidaException(EstadoLectura.FALLO, motivo);
            }
            avisos = apify.avisos(url, clave, corrida.path("defaultDatasetId").asString(""));
            Instant limiteCosto = Instant.now().plus(config.plazoCosto());
            while (cobrados(corrida) < avisos.size() && Instant.now().isBefore(limiteCosto)) {
                dormir(config.pausaConsulta());
                corrida = apify.corrida(url, clave, corrida.path("id").asString(), 0);
            }
            if (cobrados(corrida) < avisos.size()) {
                detalle = "el costo no se asentó a tiempo; se calcula por los precios de la corrida";
            }
        } catch (LecturaFallidaException e) {
            estado = e.getEstado();
            detalle = e.getMessage();
            sinCargo = corrida == null && estado == EstadoLectura.BLOQUEO;
            incierto = !sinCargo && (corrida == null || !TERMINADAS.contains(corrida.path("status").asString(""))
                    || "SUCCEEDED".equals(corrida.path("status").asString("")));
            detener = incierto || estado == EstadoLectura.BLOQUEO;
        }
        BigDecimal costo = sinCargo ? BigDecimal.ZERO : incierto ? autorizado : costo(corrida, avisos.size());
        if (costo == null) {
            costo = autorizado;
            incierto = true;
            detener = true;
        }
        if (incierto) {
            detalle = (detalle == null ? "" : detalle + "; ")
                    + "costo incierto: se cuenta el gasto autorizado y se detienen las búsquedas";
        }
        consumo.sumar(fuente, mes, costo);
        return new Resultado(avisos, costo, detalle, estado, detener);
    }

    private static BigDecimal costo(JsonNode corrida, int entregados) {
        if (corrida == null) { return null; }
        JsonNode uso = corrida.path("usageTotalUsd");
        if (uso.isNumber() && uso.decimalValue().signum() >= 0 && cobrados(corrida) >= entregados) {
            return uso.decimalValue();
        }
        JsonNode eventos = corrida.path("pricingInfo").path("pricingPerEvent").path("actorChargeEvents");
        if (!eventos.isObject() || eventos.isEmpty()) { return null; }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, JsonNode> evento : eventos.properties()) {
            JsonNode precio = evento.getValue().path("eventPriceUsd");
            if (!precio.isNumber() || precio.decimalValue().signum() < 0) { return null; }
            int cobrados = corrida.path("chargedEventCounts").path(evento.getKey()).asInt(0);
            int cantidad = evento.getKey().equals(eventoPrincipal(corrida)) ? Math.max(cobrados, entregados)
                    : Math.max(cobrados, evento.getValue().path("isOneTimeEvent").asBoolean(false) ? 1 : 0);
            total = total.add(precio.decimalValue().multiply(BigDecimal.valueOf(cantidad)));
        }
        return uso.isNumber() ? total.max(uso.decimalValue()) : total;
    }

    private static int cobrados(JsonNode corrida) {
        return corrida.path("chargedEventCounts").path(eventoPrincipal(corrida)).asInt(-1);
    }

    private static String eventoPrincipal(JsonNode corrida) {
        for (var evento : corrida.path("pricingInfo").path("pricingPerEvent").path("actorChargeEvents").properties()) {
            if (evento.getValue().path("isPrimaryEvent").asBoolean(false)) { return evento.getKey(); }
        }
        return EVENTO_AVISO;
    }

    private static void dormir(Duration pausa) {
        try { Thread.sleep(pausa); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la lectura se interrumpió");
        }
    }
}
