package net.tacuchi.perfil.clasificacion;

import static net.tacuchi.perfil.soporte.Ofertas.oferta;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import net.tacuchi.perfil.model.Clasificacion;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.soporte.Ofertas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

/** Tabla de casos de clasificación, en español y en inglés, con el motivo que cada regla deja. */
class ClasificadorTest {

    private final Clasificador clasificador = new Clasificador(new CatalogoTecnologias(JsonMapper.builder().build()));

    static Stream<Arguments> casos() {
        return Stream.of(
                caso("principal en español", oferta(), Encaje.ALTO,
                        "Menciona tecnologías principales: Angular."),
                caso("principal en inglés sin nivel de inglés",
                        oferta().idioma("en").titulo("Senior Flutter Engineer")
                                .descripcion("You will build our mobile app with Flutter and Dart."),
                        Encaje.ALTO, Clasificador.EN_INGLES_SIN_NIVEL),
                caso("solo secundarias", oferta().media(), Encaje.MEDIO,
                        "Solo menciona tecnologías secundarias: Java, Docker."),
                caso("javascript no es java", oferta().descripcion("React y JavaScript. Inglés técnico."),
                        Encaje.NO_ENCAJA, Clasificador.SIN_TECNOLOGIAS),
                caso("inglés avanzado", oferta().descripcion("Angular. Inglés avanzado (C1) excluyente."),
                        Encaje.NO_ENCAJA, Clasificador.INGLES_CONVERSADO),
                caso("bilingüe en el título", oferta().titulo("Desarrollador Angular Bilingüe"),
                        Encaje.NO_ENCAJA, Clasificador.INGLES_CONVERSADO),
                caso("fluent english", oferta().idioma("en").titulo("Senior Android Developer")
                                .descripcion("Android and Kotlin. Fluent English is required for daily meetings."),
                        Encaje.NO_ENCAJA, Clasificador.INGLES_CONVERSADO),
                caso("strong verbal and written english", oferta().idioma("en").titulo("Senior Angular Developer")
                                .descripcion("Angular. Strong verbal and written English communication skills."),
                        Encaje.NO_ENCAJA, Clasificador.INGLES_CONVERSADO),
                caso("inglés avanzado deseable no descarta",
                        oferta().descripcion("Angular. Inglés avanzado deseable."),
                        Encaje.ALTO, "Menciona tecnologías principales: Angular."),
                caso("junior en el título", oferta().titulo("Desarrollador Junior Angular").junior(null),
                        Encaje.NO_ENCAJA, Clasificador.NIVEL_JUNIOR),
                caso("prácticas", oferta().titulo("Practicante de desarrollo Flutter").junior(null),
                        Encaje.NO_ENCAJA, Clasificador.NIVEL_JUNIOR),
                caso("intern en inglés", oferta().idioma("en").titulo("Software Engineering Intern (Angular)")
                                .junior(null).descripcion("Angular internship for students."),
                        Encaje.NO_ENCAJA, Clasificador.NIVEL_JUNIOR),
                caso("junior según la fuente", oferta().junior(true), Encaje.NO_ENCAJA, Clasificador.NIVEL_JUNIOR),
                caso("sin tecnologías del CV", oferta().titulo("Contador senior")
                                .descripcion("Contabilidad y Excel. Inglés técnico."),
                        Encaje.NO_ENCAJA, Clasificador.SIN_TECNOLOGIAS),
                caso("jefatura en español", oferta().titulo("Jefe de Desarrollo Angular"),
                        Encaje.NO_ENCAJA, Clasificador.JEFATURA),
                caso("engineering manager", oferta().idioma("en").titulo("Engineering Manager, Mobile")
                                .descripcion("Lead a team of Android and Flutter engineers."),
                        Encaje.NO_ENCAJA, Clasificador.JEFATURA),
                caso("líder técnico no es jefatura", oferta().titulo("Tech Lead Android")
                                .descripcion("Android y Kotlin. Inglés técnico."),
                        Encaje.ALTO, "Menciona tecnologías principales: Android."),
                caso("híbrida fuera de Lima", oferta().modalidad(Modalidad.HIBRIDO).ubicacion("Chile").admite(false),
                        Encaje.NO_ENCAJA, "Es híbrida fuera de Lima: Chile."),
                caso("presencial fuera de Lima", oferta().modalidad(Modalidad.PRESENCIAL).ubicacion("Bogotá")
                                .admite(false),
                        Encaje.NO_ENCAJA, "Es presencial fuera de Lima: Bogotá."),
                caso("híbrida en Lima", oferta().modalidad(Modalidad.HIBRIDO).ubicacion("Lima, Perú").admite(true),
                        Encaje.ALTO, "Híbrida en Lima."),
                caso("remota solo para otra región", oferta().ubicacion("USA").admite(false),
                        Encaje.NO_ENCAJA, "Es remota solo para: USA."),
                caso("remota sin decir desde dónde", oferta().ubicacion(null).admite(null),
                        Encaje.ALTO, Clasificador.NO_INFORMA_PAISES),
                caso("nivel no informado", oferta().titulo("Desarrollador Angular").junior(null),
                        Encaje.ALTO, Clasificador.NO_INFORMA_NIVEL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void cadaReglaDejaSuEncajeYSuMotivo(String caso, Ofertas oferta, Encaje encaje, String motivo) {
        Clasificacion clasificacion = clasificador.clasificar(oferta.build());

        assertThat(clasificacion.encaje()).as(clasificacion.motivos().toString()).isEqualTo(encaje);
        assertThat(clasificacion.motivos()).contains(motivo);
    }

    @Test
    void unDatoQueFaltaNoDescartaLaOfertaYElMotivoLoDice() {
        Clasificacion clasificacion = clasificador.clasificar(oferta()
                .titulo("Desarrollador Senior")
                .modalidad(Modalidad.NO_INFORMA).ubicacion(null).admite(null)
                .descripcion("Desarrollo de apps con Flutter y Angular para clientes de banca.")
                .build());

        assertThat(clasificacion.encaje()).isNotEqualTo(Encaje.NO_ENCAJA);
        assertThat(clasificacion.motivos())
                .contains(Clasificador.NO_INFORMA_MODALIDAD, Clasificador.NO_INFORMA_IDIOMA);
        assertThat(clasificacion.tecnologias()).containsExactly("Flutter", "Angular");
    }

    @Test
    void variasCausasDeDescarteQuedanTodasEnElMotivo() {
        Clasificacion clasificacion = clasificador.clasificar(oferta()
                .titulo("Junior Developer").junior(null).ubicacion("USA").admite(false)
                .descripcion("Ruby. Inglés fluido.")
                .build());

        assertThat(clasificacion.encaje()).isEqualTo(Encaje.NO_ENCAJA);
        assertThat(clasificacion.motivos()).contains(Clasificador.INGLES_CONVERSADO, Clasificador.NIVEL_JUNIOR,
                Clasificador.SIN_TECNOLOGIAS, "Es remota solo para: USA.");
    }

    @Test
    void elIdiomaDeLaPublicacionSeDetectaCuandoLaFuenteNoLoDa() {
        assertThat(Clasificador.detectarIdioma("We are looking for an engineer with experience in our team"))
                .isEqualTo("en");
        assertThat(Clasificador.detectarIdioma("Buscamos una persona con experiencia en el desarrollo de apps"))
                .isEqualTo("es");
        assertThat(Clasificador.detectarIdioma("")).isNull();
    }

    @Test
    void lasUbicacionesEscritasAManoSeLeen() {
        assertThat(Ubicaciones.remotoAdmitePeru("Worldwide")).isTrue();
        assertThat(Ubicaciones.remotoAdmitePeru("LATAM, Europe, USA, Canada, APAC")).isTrue();
        assertThat(Ubicaciones.remotoAdmitePeru("Anywhere in the World")).isTrue();
        assertThat(Ubicaciones.remotoAdmitePeru("USA, Canada, USA timezones")).isFalse();
        assertThat(Ubicaciones.remotoAdmitePeru("North America")).isFalse();
        assertThat(Ubicaciones.remotoAdmitePeru("Remote")).isNull();
        assertThat(Ubicaciones.remotoAdmitePeru("")).isNull();
        assertThat(Ubicaciones.enLima("Lima, Perú")).isTrue();
        assertThat(Ubicaciones.enLima("Arequipa, Perú")).isFalse();
        assertThat(Ubicaciones.enLima(null)).isNull();
    }

    private static Arguments caso(String nombre, Ofertas oferta, Encaje encaje, String motivo) {
        return Arguments.of(nombre, oferta, encaje, motivo);
    }
}
