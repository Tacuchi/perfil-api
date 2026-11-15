package net.tacuchi.perfil.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Todo lo que cambia entre local y producción. Cada valor llega por variable de entorno
 * (PERFIL_ACCESS_EMISOR, PERFIL_ALMACEN_BUCKET, ...); si falta uno, el arranque falla nombrándolo.
 * Los grupos llevan @DefaultValue para que un grupo ausente se valide hoja por hoja y el error nombre la hoja.
 */
@Validated
@ConfigurationProperties("perfil")
public record PerfilProperties(
        @Valid @DefaultValue Access access,
        @Valid @DefaultValue Almacen almacen,
        @Valid @DefaultValue Cv cv,
        @Valid @DefaultValue Sitio sitio,
        @Valid @DefaultValue Panel panel,
        @Valid @DefaultValue Ofertas ofertas,
        @Valid @DefaultValue Aviso aviso) {

    private static final String URL = "https?://\\S+";
    private static final String URL_INVALIDA = "debe ser una URL http(s)";

    /** Aplicación de Cloudflare Access que protege el panel. */
    public record Access(
            @NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String emisor,
            @NotBlank String aud,
            @NotBlank @Email String correo) {
    }

    /** Almacenamiento de objetos compatible con S3 (Cloudflare R2). */
    public record Almacen(
            @NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String endpoint,
            @NotBlank String bucket,
            @NotBlank String acceso,
            @NotBlank String secreto) {
    }

    /** Nombre con el que el visitante guarda el PDF público. */
    public record Cv(@NotBlank @Pattern(regexp = "[^/\\\\]+\\.pdf", message = "debe ser un nombre de archivo .pdf") String archivo) {
    }

    /** Sitio público: de él sale la página de error cuando no hay CV vigente. */
    public record Sitio(@NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String url) {
    }

    /** Dirección del panel: el único origen desde el que se aceptan cambios. */
    public record Panel(@NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String origen) {
    }

    /**
     * Monitor de ofertas: dirección de cada fuente y cuándo se intenta la recolección. Cada fuente se lee
     * como máximo una vez por día aunque el horario se dispare más veces.
     */
    public record Ofertas(
            @NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String getonbrdUrl,
            @NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String remotiveUrl,
            @NotBlank @Pattern(regexp = URL, message = URL_INVALIDA) String remoteokUrl,
            @NotBlank String cron) {
    }

    /**
     * Correo diario a la persona (a {@code access.correo}). Se declara encendido o apagado: apagado, el resto
     * funciona sin proveedor de correo. Encendido exige el remitente y el servidor de envío (spring.mail.*).
     */
    public record Aviso(@NotNull Boolean activo, @Email String remitente) {
    }
}
