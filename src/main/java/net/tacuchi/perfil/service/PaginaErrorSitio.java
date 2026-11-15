package net.tacuchi.perfil.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.config.PerfilProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * La página «no encontrado» del sitio público, para responder /cv sin CV vigente igual que el resto del sitio.
 * Si el sitio no la entrega, una página mínima: /cv no depende de que el sitio esté arriba.
 */
@Slf4j
@Component
public class PaginaErrorSitio {

    static final byte[] PAGINA_MINIMA = """
            <!doctype html><html lang="es"><meta charset="utf-8"><title>No encontrado</title>\
            <h1>No encontrado</h1></html>""".getBytes(StandardCharsets.UTF_8);

    private static final Duration ESPERA = Duration.ofSeconds(2);
    private static final String RUTA_ERROR = "/404.html";

    private final RestClient sitio;

    public PaginaErrorSitio(RestClient.Builder builder, PerfilProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(ESPERA);
        fabrica.setReadTimeout(ESPERA);
        this.sitio = builder.clone().baseUrl(propiedades.sitio().url()).requestFactory(fabrica).build();
    }

    public byte[] html() {
        try {
            // nginx entrega /404.html con estado 404: la página es el cuerpo de esa respuesta.
            byte[] pagina = sitio.get().uri(RUTA_ERROR).accept(MediaType.TEXT_HTML)
                    .exchange((peticion, respuesta) -> esPaginaDeError(respuesta.getStatusCode().value(),
                            respuesta.getHeaders().getContentType())
                            ? respuesta.getBody().readAllBytes()
                            : null);
            if (pagina != null && pagina.length > 0) {
                return pagina;
            }
            log.warn("[CV] el sitio no entregó su página de error; se usa la mínima");
        } catch (RuntimeException e) {
            log.warn("[CV] el sitio no respondió al pedir su página de error: {}", e.getMessage());
        }
        return PAGINA_MINIMA;
    }

    private static boolean esPaginaDeError(int estado, MediaType tipo) {
        boolean estadoEsperado = estado == HttpStatus.NOT_FOUND.value() || estado == HttpStatus.OK.value();
        return estadoEsperado && tipo != null && MediaType.TEXT_HTML.isCompatibleWith(tipo);
    }
}
