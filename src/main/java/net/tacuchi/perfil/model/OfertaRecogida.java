package net.tacuchi.perfil.model;

import java.time.Instant;

/**
 * Una oferta tal como la entrega su fuente, ya convertida. Los campos que la fuente no informa van en null.
 *
 * @param ubicacionAdmite si la ubicación admite a la persona: remoto que acepta candidatos desde Perú,
 *                        o híbrido/presencial en Lima. null si la oferta no lo informa.
 * @param nivelJunior     true si la fuente la declara junior o sin experiencia, false si declara otro nivel,
 *                        null si no informa el nivel.
 * @param idioma          idioma de la publicación ("es" o "en"), null si no se pudo saber.
 */
public record OfertaRecogida(
        Fuente fuente,
        String idExterno,
        String titulo,
        String empresa,
        Instant publicadaEn,
        String ubicacion,
        Modalidad modalidad,
        Boolean ubicacionAdmite,
        Boolean nivelJunior,
        String idioma,
        String descripcion,
        String url,
        String correoContacto) {
}
