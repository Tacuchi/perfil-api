package net.tacuchi.perfil.fuentes;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import net.tacuchi.perfil.model.Modalidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AvisosApifyTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void linkedin_recuperaNumeroYConservaIndustriaSinInventarModalidad() {
        var aviso = json.createObjectNode().put("url", "https://www.linkedin.com/jobs/view/programador-4487010792?origen=busqueda#aviso")
                .put("title", "Java").put("workType", "IT Services and IT Consulting")
                .put("description", "Desarrollo Java").put("postedDate", "2026-11-15T12:00:00Z");
        var oferta = LinkedinFuente.convertir(aviso);
        assertThat(oferta.idExterno()).isEqualTo("4487010792");
        assertThat(oferta.url()).isEqualTo("https://www.linkedin.com/jobs/view/programador-4487010792");
        assertThat(oferta.modalidad()).isEqualTo(Modalidad.NO_INFORMA);
        assertThat(oferta.publicadaEn()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
    }

    @ParameterizedTest @ValueSource(strings = {"http://www.bumeran.com.pe/empleos/aviso-123.html", "https://bumeran.com.pe.ajeno.test/empleos/aviso-123.html", "https://www.bumeran.com.pe/empleos/sin-numero.html", "https://www.linkedin.com/jobs/view/123/"})
    void bumeran_rechazaEnlacesSinIdentidadCompatible(String url) {
        var aviso = json.createObjectNode().put("canonicalUrl",url).put("jobId", "hash-del-actor").put("title","Java");
        assertThatThrownBy(() -> BumeranFuente.convertir(aviso)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enlace HTTPS");
    }

    @Test void bumeran_usaPortalAlternativoNumeroHistoricoFechaDeLimaYModalidad() {
        var aviso = json.createObjectNode().put("canonicalUrl", "https://otro.test/aviso")
                .put("portalUrl", "https://www.bumeran.com.pe/empleos/programador-1118631884.html?origen=x#aviso")
                .put("jobId", "hash-distinto").put("title", "Java junior").put("description", "Desarrollo Java")
                .put("postedDate", "2026-11-15T13:30:39").put("workModality", "Híbrido").put("seniorityLevel", "Junior");
        var oferta = BumeranFuente.convertir(aviso);
        assertThat(oferta.idExterno()).isEqualTo("1118631884");
        assertThat(oferta.url()).isEqualTo("https://www.bumeran.com.pe/empleos/programador-1118631884.html");
        assertThat(oferta.publicadaEn()).isEqualTo(Instant.parse("2026-11-15T18:30:39Z"));
        assertThat(oferta.modalidad()).isEqualTo(Modalidad.HIBRIDO);
        assertThat(oferta.nivelJunior()).isTrue();
        assertThat(oferta.ubicacionAdmite()).isNull();
        aviso.put("postedDate", "2026-02-31T13:30:39");
        assertThat(BumeranFuente.convertir(aviso).publicadaEn()).isNull();
        aviso.put("postedDate", "2026-11-15T13:30:39+02:00");
        assertThat(BumeranFuente.convertir(aviso).publicadaEn()).isEqualTo(Instant.parse("2026-11-15T11:30:39Z"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "Presencial, 'Lima, Lima', true, Presencial en Lima.",
            "Presencial, 'Cusco, Cusco', false, 'Es presencial fuera de Lima: Cusco, Cusco.'",
            "Remoto, 'Cusco, Cusco', true, 'Remota, admite candidatos desde Perú.'"
    })
    void bumeran_clasificaConLaMismaReglaDeUbicacion(String modalidad, String ubicacion, boolean admite,
            String motivo) {
        var aviso = json.createObjectNode().put("portalUrl", "https://www.bumeran.com.pe/empleos/programador-123.html")
                .put("title", "Desarrollador Angular senior").put("description", "Desarrollo con Angular en equipo")
                .put("workModality", modalidad).put("location", ubicacion);
        var oferta = BumeranFuente.convertir(aviso);
        assertThat(oferta.ubicacionAdmite()).isEqualTo(admite);
        var clasificador = new net.tacuchi.perfil.clasificacion.Clasificador(
                new net.tacuchi.perfil.clasificacion.CatalogoTecnologias(json));
        var clasificacion = clasificador.clasificar(oferta);
        assertThat(clasificacion.motivos()).contains(motivo);
        assertThat(clasificacion.encaje()).isEqualTo(admite ? net.tacuchi.perfil.model.Encaje.ALTO
                : net.tacuchi.perfil.model.Encaje.NO_ENCAJA);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "'Lima, Peru', true",
            "'Cusco, Perú', true",
            "'Santiago, Chile', null"
    })
    void linkedin_remotoUsaElAmbitoSinDescartarOtrosPaises(String ubicacion, String admite) {
        var aviso = json.createObjectNode().put("url", "https://www.linkedin.com/jobs/view/123/")
                .put("title", "Desarrollador Angular senior").put("description", "Desarrollo con Angular")
                .put("location", ubicacion);
        var remota = LinkedinFuente.convertir(aviso, true);
        assertThat(remota.modalidad()).isEqualTo(Modalidad.REMOTO);
        assertThat(remota.ubicacionAdmite()).isEqualTo(admite.equals("null") ? null : Boolean.valueOf(admite));
        var peru = LinkedinFuente.convertir(aviso, false);
        assertThat(peru.modalidad()).isEqualTo(Modalidad.NO_INFORMA);
        assertThat(peru.ubicacionAdmite()).isNull();
    }

}
