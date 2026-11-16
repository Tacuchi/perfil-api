package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import net.tacuchi.perfil.model.*;
import net.tacuchi.perfil.soporte.*;
import org.junit.jupiter.api.Test;

class BumeranApifyTest extends PruebaApify {
    @Test void leer_usaTresTerminosConDetallesYConservaIdentidadDelLector() {
        clave();
        leidas(Fuente.BUMERAN, Ofertas.oferta().fuente(Fuente.BUMERAN).id("1118631884").build(),
                Ofertas.oferta().fuente(Fuente.BUMERAN).id("1000000000").build());
        reloj.adelantar(Duration.ofMinutes(1));
        var fin = enAvance(leerADemanda(Fuente.BUMERAN), Fuente.BUMERAN);
        assertThat(fin.ofertasLeidas()).isEqualTo(2);
        assertThat(fin.costoUsd()).isEqualByComparingTo("0.0075");
        assertThat(APIFY.lanzamientos(ApifyDePrueba.BUMERAN)).isEqualTo(3);
        var entradas = APIFY.peticiones().stream().filter(x -> x.metodo().equals("POST")).toList();
        assertThat(entradas).extracting(x -> x.cuerpo().path("maxResults").asInt()).containsExactly(9,9,7);
        assertThat(entradas).extracting(x -> x.cuerpo().path("query").asString())
                .containsExactly("desarrollador de software", "programador", "full stack");
        assertThat(entradas).allSatisfy(x -> {
            assertThat(x.conClave()).isTrue();
            assertThat(x.cuerpo().path("country").asString()).isEqualTo("PE");
            assertThat(x.cuerpo().path("includeDetails").asBoolean()).isTrue();
        });
        var primera = todas().getFirst();
        marcar(primera.id(), "GUARDADA");
        var antes = detalle(primera.id());
        reloj.adelantar(Duration.ofHours(1));
        leerADemanda(Fuente.BUMERAN);
        assertThat(todas()).hasSize(3).allSatisfy(x -> assertThat(x.publicada()).isTrue());
        assertThat(detalle(primera.id()).oferta().estado()).isEqualTo(EstadoOferta.GUARDADA);
        assertThat(detalle(primera.id()).oferta().fecha()).isEqualTo(antes.oferta().fecha());
        assertThat(fuente(Fuente.BUMERAN).consumoMes()).isEqualByComparingTo("0.015");
        assertThat(jdbc.sql("SELECT id_externo FROM oferta").query(String.class).list())
                .allSatisfy(id -> assertThat(id).matches("[0-9]+"));
    }

    @Test void listaVacia_muestraFalloConMotivoYCuentaArranques() {
        clave(); APIFY.modo(ApifyDePrueba.Modo.VACIA);
        var fin = enAvance(leerADemanda(Fuente.BUMERAN), Fuente.BUMERAN);
        assertThat(fin.resultado()).isEqualTo(EstadoLectura.FALLO);
        assertThat(fin.detalle()).contains("no devolvió avisos válidos", "Perú");
        assertThat(fin.costoUsd()).isEqualByComparingTo("0.0015");
    }

    @Test void pausarYReanudar_noLeeSinSolicitudExplicita() {
        clave();
        postIdentificado("/api/fuentes/BUMERAN/pausar").expectStatus().isOk();
        postIdentificado("/api/recoleccion/BUMERAN").expectStatus().isEqualTo(409);
        assertThat(APIFY.consultas()).isZero();
        postIdentificado("/api/fuentes/BUMERAN/reanudar").expectStatus().isOk();
        assertThat(APIFY.consultas()).isZero();
        leerADemanda(Fuente.BUMERAN);
        assertThat(APIFY.lanzamientos(ApifyDePrueba.BUMERAN)).isEqualTo(3);
    }

    @Test void claveCompartida_seReemplazaDesdeCadaConexionSinDuplicarCifrado() {
        for (Fuente fuente : java.util.List.of(Fuente.LINKEDIN, Fuente.BUMERAN, Fuente.COMPUTRABAJO)) {
            configurar(fuente, "clave", "{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}").expectStatus().isOk();
            assertThat(fuentes().stream().filter(x -> x.via() == Via.PAGO)).hasSize(3)
                    .allSatisfy(x -> assertThat(x.tieneClave()).isTrue());
            assertThat(fuentes().stream().filter(x -> x.via() == Via.PAGO).map(x -> x.claveCargadaEn()).distinct())
                    .hasSize(1);
        }
        assertThat(jdbc.sql("SELECT fuente FROM conexion WHERE clave_cifrada IS NOT NULL")
                .query(String.class).list()).containsExactly("COMPUTRABAJO");
        assertThat(APIFY.consultas()).isZero();
    }
}
