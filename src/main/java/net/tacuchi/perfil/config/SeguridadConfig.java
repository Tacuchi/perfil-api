package net.tacuchi.perfil.config;

import jakarta.servlet.DispatcherType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import net.tacuchi.perfil.controller.CvPublicoController;
import net.tacuchi.perfil.controller.LectorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
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
 *
 * <p>Dos identidades, cada una en sus rutas. El lector de la máquina entra con un token de servicio de Access: su
 * código no trae correo sino el identificador del token ({@code common_name}). Las rutas del lector solo aceptan
 * ese código y quedan fuera del filtro de origen, porque no las llama un navegador; las demás solo aceptan el de
 * la persona.
 */
@Configuration
public class SeguridadConfig {

    static final String CABECERA_ACCESS = "Cf-Access-Jwt-Assertion";
    private static final String RUTA_CLAVES = "/cdn-cgi/access/certs";
    private static final String CLAIM_CORREO = "email";
    private static final String CLAIM_TOKEN_SERVICIO = "common_name";

    @Bean
    @Order(1)
    SecurityFilterChain cadenaLector(HttpSecurity http, PerfilProperties propiedades) throws Exception {
        return http
                .securityMatcher(LectorController.RUTA + "/**")
                .authorizeHttpRequests(rutas -> rutas
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .bearerTokenResolver(new HeaderBearerTokenResolver(CABECERA_ACCESS))
                        .jwt(jwt -> jwt.decoder(decoderLector(propiedades))))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, PerfilProperties propiedades) throws Exception {
        return http
                .authorizeHttpRequests(rutas -> rutas
                        // El despacho de error solo escribe el estado que otra regla ya decidió.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, CvPublicoController.RUTA).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .bearerTokenResolver(new HeaderBearerTokenResolver(CABECERA_ACCESS))
                        .jwt(jwt -> jwt.decoder(jwtDecoder(propiedades))))
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

    /**
     * Firma (claves del equipo de Access), emisor, vigencia, aplicación del lector y el identificador del token de
     * servicio configurado; sin correo. Sin identificador configurado no acepta ningún código.
     */
    static JwtDecoder decoderLector(PerfilProperties propiedades) {
        PerfilProperties.Access access = propiedades.access();
        PerfilProperties.Lector lector = propiedades.lector();
        String aud = lector.aud() == null || lector.aud().isBlank() ? access.aud() : lector.aud();
        String tokenId = lector.tokenId();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(access.emisor() + RUTA_CLAVES).build();
        OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(access.emisor()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, valor -> valor != null && valor.contains(aud)),
                new JwtClaimValidator<String>(CLAIM_TOKEN_SERVICIO,
                        id -> tokenId != null && !tokenId.isBlank() && tokenId.equals(id)),
                sinCorreo());
        decoder.setJwtValidator(validador);
        return decoder;
    }

    /** El código de la persona trae su correo: aquí no se acepta. JwtClaimValidator rechaza un claim ausente. */
    private static OAuth2TokenValidator<Jwt> sinCorreo() {
        return jwt -> jwt.hasClaim(CLAIM_CORREO)
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
                        "el código del lector no lleva correo", null))
                : OAuth2TokenValidatorResult.success();
    }
}
