package net.tacuchi.perfil.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("perfil.apify")
public record ActoresApifyProperties(@Valid @NotNull Actor linkedin, @Valid @NotNull Actor bumeran) {
    public record Actor(@NotBlank @Pattern(regexp = "https?://\\S+") String url, @NotBlank String actor) { }
}
