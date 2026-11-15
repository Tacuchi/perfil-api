package net.tacuchi.perfil.fuentes;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import net.tacuchi.perfil.model.EstadoLectura;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pide JSON a las fuentes identificándose como este monitor. Un rechazo (401, 403, 429) o una página de
 * verificación se registra como bloqueo; cualquier otro problema, como fallo. Nunca reintenta.
 */
@Component
public class ClienteFuentes {

    static final String AGENTE = "perfil-monitor-ofertas/1.0 (+https://tacuchi.dev)";
    private static final Duration ESPERA_CONEXION = Duration.ofSeconds(15);
    private static final Duration ESPERA_RESPUESTA = Duration.ofSeconds(60);
    private static final Pattern VERIFICACION = Pattern.compile(
            "(?i)captcha|cf-chl|challenge-platform|just a moment|attention required|access denied");

    private final RestClient cliente;
    private final JsonMapper jsonMapper;

    public ClienteFuentes(RestClient.Builder builder, JsonMapper jsonMapper) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(ESPERA_CONEXION)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(ESPERA_RESPUESTA);
        this.cliente = builder.clone().requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, AGENTE)
                .build();
        this.jsonMapper = jsonMapper;
    }

    public JsonNode json(String url) {
        Respuesta respuesta;
        try {
            respuesta = cliente.get().uri(url).accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, r) -> new Respuesta(r.getStatusCode().value(),
                            new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)), false);
        } catch (RestClientException e) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "sin respuesta del sitio", e);
        }
        if (respuesta == null) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "sin respuesta del sitio");
        }
        int estado = respuesta.estado();
        if (estado == 401 || estado == 403 || estado == 429) {
            throw new LecturaFallidaException(EstadoLectura.BLOQUEO, "el sitio rechazó la lectura (HTTP " + estado + ")");
        }
        if (estado >= 300) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "el sitio respondió HTTP " + estado);
        }
        try {
            return jsonMapper.readTree(respuesta.cuerpo());
        } catch (JacksonException e) {
            if (VERIFICACION.matcher(respuesta.cuerpo()).find()) {
                throw new LecturaFallidaException(EstadoLectura.BLOQUEO, "el sitio pidió una verificación");
            }
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la respuesta no es JSON", e);
        }
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}
