package net.tacuchi.perfil.config;

import jakarta.servlet.DispatcherType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import net.tacuchi.perfil.controller.CvPublicoController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.HeaderBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Identifica a la persona con el código que Cloudflare Access pone en cada petición. La API lo verifica
 * aunque Cloudflare ya filtre: la IP del servidor es pública y alguien podría llegar sin pasar por Cloudflare.
 */
@Configuration
public class SeguridadConfig {

    static final String CABECERA_ACCESS = "Cf-Access-Jwt-Assertion";
    private static final String RUTA_CLAVES = "/cdn-cgi/access/certs";
    private static final String CLAIM_CORREO = "email";

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, PerfilProperties propiedades) throws Exception {
        return http
                .authorizeHttpRequests(rutas -> rutas
                        // El despacho de error solo escribe el estado que otra regla ya decidió.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, CvPublicoController.RUTA).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .bearerTokenResolver(new HeaderBearerTokenResolver(CABECERA_ACCESS))
                        .jwt(Customizer.withDefaults()))
                // Sin cookies propias ni sesión: la protección entre sitios la da el filtro de origen.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new OrigenPanelFilter(propiedades.panel().origen()),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    /** Firma (claves del equipo de Access), emisor, vigencia, aplicación y correo permitido. */
    @Bean
    JwtDecoder jwtDecoder(PerfilProperties propiedades) {
        PerfilProperties.Access access = propiedades.access();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(access.emisor() + RUTA_CLAVES).build();
        OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(access.emisor()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(access.aud())),
                new JwtClaimValidator<String>(CLAIM_CORREO, correo -> access.correo().equalsIgnoreCase(correo)));
        decoder.setJwtValidator(validador);
        return decoder;
    }
}
