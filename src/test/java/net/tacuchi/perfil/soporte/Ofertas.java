package net.tacuchi.perfil.soporte;

import java.time.Instant;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;

/** Ofertas de prueba: por defecto, remota desde Perú, senior, en español y con Angular (encaje alto). */
public final class Ofertas {

    private Fuente fuente = Fuente.GET_ON_BOARD;
    private String id = "oferta-1";
    private String titulo = "Desarrollador Senior";
    private String empresa = "Empresa Uno";
    private Instant publicadaEn = Instant.parse("2026-11-10T15:00:00Z");
    private String ubicacion = "Latinoamérica";
    private Modalidad modalidad = Modalidad.REMOTO;
    private Boolean admite = true;
    private Boolean junior = false;
    private String idioma = "es";
    private String descripcion = "Buscamos experiencia con Angular. Inglés técnico para leer documentación.";
    private String correo;

    private Ofertas() {
    }

    public static Ofertas oferta() {
        return new Ofertas();
    }

    public Ofertas fuente(Fuente valor) { fuente = valor; return this; }
    public Ofertas id(String valor) { id = valor; return this; }
    public Ofertas titulo(String valor) { titulo = valor; return this; }
    public Ofertas empresa(String valor) { empresa = valor; return this; }
    public Ofertas publicadaEn(Instant valor) { publicadaEn = valor; return this; }
    public Ofertas ubicacion(String valor) { ubicacion = valor; return this; }
    public Ofertas modalidad(Modalidad valor) { modalidad = valor; return this; }
    public Ofertas admite(Boolean valor) { admite = valor; return this; }
    public Ofertas junior(Boolean valor) { junior = valor; return this; }
    public Ofertas idioma(String valor) { idioma = valor; return this; }
    public Ofertas descripcion(String valor) { descripcion = valor; return this; }
    public Ofertas correo(String valor) { correo = valor; return this; }

    /** Solo tecnologías secundarias: encaje medio. */
    public Ofertas media() {
        return descripcion("Experiencia con Java y Docker. Inglés técnico.");
    }

    /** Ninguna tecnología del CV: no encaja. */
    public Ofertas sinTecnologias() {
        return descripcion("Experiencia con Ruby on Rails. Inglés técnico.");
    }

    public OfertaRecogida build() {
        return new OfertaRecogida(fuente, id, titulo, empresa, publicadaEn, ubicacion, modalidad, admite, junior,
                idioma, descripcion, "https://sitio.test/ofertas/" + id, correo);
    }
}
