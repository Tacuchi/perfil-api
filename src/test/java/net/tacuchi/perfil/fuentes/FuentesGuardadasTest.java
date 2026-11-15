package net.tacuchi.perfil.fuentes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cada pieza convierte la respuesta real guardada de su sitio. */
class FuentesGuardadasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void getOnBoardTraeEmpresaModalidadYNivel() {
        List<OfertaRecogida> ofertas = new ArrayList<>();
        for (String archivo : List.of("getonbrd-programming.json", "getonbrd-mobile-developer.json")) {
            leer(archivo).path("data").forEach(o -> ofertas.add(GetOnBoardFuente.convertir(o)));
        }

        assertThat(ofertas).hasSize(6).allSatisfy(FuentesGuardadasTest::tieneLoQueLaListaMuestra);
        OfertaRecogida hibrida = porId(ofertas, "semi-senior-full-stack-java-developer-qa-ai-bilingue-23people-santiago");
        assertThat(hibrida.empresa()).isEqualTo("23people");
        assertThat(hibrida.modalidad()).isEqualTo(Modalidad.HIBRIDO);
        assertThat(hibrida.ubicacion()).isEqualTo("Chile");
        assertThat(hibrida.ubicacionAdmite()).isFalse();
        assertThat(hibrida.nivelJunior()).isFalse();
        assertThat(hibrida.url()).startsWith("https://www.getonbrd.com/jobs/");
        assertThat(hibrida.descripcion()).doesNotContain("<li>").contains("Spring Boot");

        OfertaRecogida remotaTotal = porId(ofertas, "founding-ios-engineer-swift-hostop-remote");
        assertThat(remotaTotal.modalidad()).isEqualTo(Modalidad.REMOTO);
        assertThat(remotaTotal.ubicacionAdmite()).isTrue();
        assertThat(remotaTotal.idioma()).isEqualTo("en");

        OfertaRecogida remotaLocal = porId(ofertas, "android-software-engineer-improving-remote");
        assertThat(remotaLocal.modalidad()).isEqualTo(Modalidad.REMOTO);
        assertThat(remotaLocal.ubicacionAdmite()).as("remoto local sin países: no se sabe").isNull();
    }

    @Test
    void remotiveSeQuedaConLasDeTecnologiaYLeeSuUbicacion() {
        JsonNode respuesta = leer("remotive.json");
        List<OfertaRecogida> ofertas = new ArrayList<>();
        for (JsonNode oferta : respuesta.path("jobs")) {
            if (RemotiveFuente.CATEGORIAS.contains(oferta.path("category").asString())) {
                ofertas.add(RemotiveFuente.convertir(oferta));
            }
        }

        assertThat(ofertas).hasSize(4).allSatisfy(FuentesGuardadasTest::tieneLoQueLaListaMuestra);
        assertThat(ofertas).allSatisfy(o -> assertThat(o.url()).startsWith("https://remotive.com/remote-jobs/"));
        OfertaRecogida latam = porId(ofertas, "2091136");
        assertThat(latam.ubicacionAdmite()).isTrue();
        assertThat(latam.publicadaEn()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(porId(ofertas, "2069746").ubicacionAdmite()).isFalse();
        assertThat(porId(ofertas, "2091165").correoContacto()).isEqualTo("postulaciones@empresa-inventada.test");
        assertThat(porId(ofertas, "2091136").correoContacto()).isNull();
    }

    @Test
    void remoteOkSaltaSusMetadatosYSeQuedaConLasDeTecnologia() {
        JsonNode respuesta = leer("remoteok.json");
        List<OfertaRecogida> ofertas = new ArrayList<>();
        for (JsonNode oferta : respuesta) {
            if (oferta.hasNonNull("id") && RemoteOkFuente.esDeTecnologia(oferta)) {
                ofertas.add(RemoteOkFuente.convertir(oferta));
            }
        }

        assertThat(ofertas).extracting(OfertaRecogida::idExterno)
                .containsExactlyInAnyOrder("1137571", "1137597", "1137704", "1137062");
        assertThat(ofertas).allSatisfy(FuentesGuardadasTest::tieneLoQueLaListaMuestra);
        assertThat(ofertas).allSatisfy(o -> assertThat(o.url()).startsWith("https://remoteOK.com/remote-jobs/"));
    }

    @Test
    void elCorreoSaleDelTextoYSeIgnoranLosQueNoSonDeContacto() {
        assertThat(ExtractorCorreo.extraer("<a href=\"mailto:RRHH@Empresa-Inventada.test?subject=x\">aquí</a>"))
                .contains("rrhh@empresa-inventada.test");
        assertThat(ExtractorCorreo.extraer("Escribe a talento@empresa-inventada.test con tu CV."))
                .contains("talento@empresa-inventada.test");
        assertThat(ExtractorCorreo.extraer("noreply@empresa-inventada.test")).isEqualTo(Optional.empty());
        assertThat(ExtractorCorreo.extraer("Postula en el enlace.")).isEmpty();
    }

    /** Título, empresa, fecha, ubicación o modalidad y enlace: lo que la lista muestra de cada oferta. */
    private static void tieneLoQueLaListaMuestra(OfertaRecogida oferta) {
        assertThat(oferta.titulo()).isNotBlank();
        assertThat(oferta.empresa()).isNotBlank();
        assertThat(oferta.publicadaEn()).isNotNull();
        assertThat(oferta.ubicacion() != null || oferta.modalidad() != Modalidad.NO_INFORMA).isTrue();
        assertThat(oferta.url()).startsWith("https://");
        assertThat(oferta.idExterno()).isNotBlank();
        assertThat(oferta.fuente()).isIn((Object[]) Fuente.values());
    }

    private static OfertaRecogida porId(List<OfertaRecogida> ofertas, String id) {
        return ofertas.stream().filter(o -> o.idExterno().equals(id)).findFirst().orElseThrow();
    }

    private static JsonNode leer(String archivo) {
        return JSON.readTree(FuentesDePrueba.guardada(archivo));
    }
}
