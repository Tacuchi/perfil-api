package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.tacuchi.perfil.model.EstadoLectura;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La API de Apify: lanzar un actor con un gasto y un número de avisos máximos, consultar su corrida y leer sus
 * avisos. La clave viaja solo en la cabecera de autorización, nunca en la dirección, y no aparece en ningún
 * mensaje. Un rechazo (401, 403, 429) es bloqueo; lo demás, fallo. Nunca reintenta.
 */
@Component
public class ClienteApify {

    /** Lo más que Apify retiene una petición esperando que termine la corrida. */
    public static final int ESPERA_MAXIMA_SEGUNDOS = 60;
    private static final Duration ESPERA_CONEXION = Duration.ofSeconds(15);
    private static final Duration ESPERA_RESPUESTA = Duration.ofSeconds(ESPERA_MAXIMA_SEGUNDOS + 30L);

    private final RestClient cliente;
    private final JsonMapper jsonMapper;

    public ClienteApify(RestClient.Builder builder, JsonMapper jsonMapper) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(ESPERA_CONEXION).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(ESPERA_RESPUESTA);
        this.cliente = builder.clone().requestFactory(fabrica)
                .defaultHeader(HttpHeaders.USER_AGENT, ClienteFuentes.AGENTE)
                .build();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Lanza el actor y espera hasta {@code esperaSegundos} a que termine.
     *
     * @return el objeto de la corrida ({@code data})
     */
    public JsonNode lanzar(String base, String actor, String clave, JsonNode entrada, BigDecimal gastoMaximo,
            int maxAvisos, Duration plazoCorrida, int esperaSegundos) {
        String ruta = "/v2/acts/" + actor + "/runs?maxTotalChargeUsd="
                + gastoMaximo.setScale(4, RoundingMode.DOWN).toPlainString()
                + "&maxItems=" + maxAvisos
                + "&timeout=" + plazoCorrida.toSeconds()
                + "&waitForFinish=" + esperaSegundos;
        return pedir(HttpMethod.POST, base + ruta, clave, jsonMapper.writeValueAsString(entrada)).path("data");
    }

    /** La corrida, esperando hasta {@code esperaSegundos} a que termine (0: sin esperar). */
    public JsonNode corrida(String base, String clave, String id, int esperaSegundos) {
        return pedir(HttpMethod.GET, base + "/v2/actor-runs/" + id + "?waitForFinish=" + esperaSegundos, clave, null)
                .path("data");
    }

    /** Los avisos que dejó la corrida, en el orden en que los guardó. */
    public JsonNode avisos(String base, String clave, String dataset) {
        JsonNode avisos = pedir(HttpMethod.GET, base + "/v2/datasets/" + dataset + "/items?clean=true&format=json",
                clave, null);
        if (!avisos.isArray()) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "el servicio no entregó una lista de avisos");
        }
        return avisos;
    }

    private JsonNode pedir(HttpMethod metodo, String url, String clave, String cuerpo) {
        Respuesta respuesta;
        try {
            RestClient.RequestBodySpec peticion = cliente.method(metodo).uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + clave)
                    .accept(MediaType.APPLICATION_JSON);
            if (cuerpo != null) {
                peticion.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
            }
            respuesta = peticion.exchange((p, r) -> new Respuesta(r.getStatusCode().value(),
                    new String(r.getBody().readAllBytes(), StandardCharsets.UTF_8)), false);
        } catch (RestClientException e) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "sin respuesta del servicio");
        }
        if (respuesta == null) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "sin respuesta del servicio");
        }
        int estado = respuesta.estado();
        if (estado == 401 || estado == 403) {
            throw new LecturaFallidaException(EstadoLectura.BLOQUEO,
                    "el servicio rechazó la clave (HTTP " + estado + "); revísala en Conexiones");
        }
        if (estado == 429) {
            throw new LecturaFallidaException(EstadoLectura.BLOQUEO, "el servicio limitó las consultas (HTTP 429)");
        }
        if (estado >= 300) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "el servicio respondió HTTP " + estado);
        }
        try {
            return jsonMapper.readTree(respuesta.cuerpo());
        } catch (JacksonException e) {
            throw new LecturaFallidaException(EstadoLectura.FALLO, "la respuesta del servicio no es JSON");
        }
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}
