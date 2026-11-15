package net.tacuchi.perfil.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Los cambios solo se aceptan desde el propio panel. Cloudflare añade el código de acceso a cualquier
 * petición del navegador que lleve su cookie, también a una que otra página dispare: el origen lo distingue.
 */
public class OrigenPanelFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

    private final String origenPanel;

    public OrigenPanelFilter(String origenPanel) {
        this.origenPanel = origenPanel;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (METODOS_SEGUROS.contains(peticion.getMethod())
                || origenPanel.equals(peticion.getHeader(HttpHeaders.ORIGIN))) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        respuesta.sendError(HttpStatus.FORBIDDEN.value());
    }
}
