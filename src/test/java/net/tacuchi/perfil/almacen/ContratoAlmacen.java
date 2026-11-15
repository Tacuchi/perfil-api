package net.tacuchi.perfil.almacen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Lo que la API espera de cualquier almacén. Corre igual contra el de pruebas y contra R2,
 * para que el de pruebas no sea más permisivo que el real.
 */
abstract class ContratoAlmacen {

    private AlmacenArchivos almacen;
    private String prefijo;

    protected abstract AlmacenArchivos crear();

    @BeforeEach
    void preparar() {
        almacen = crear();
        prefijo = "contrato/" + UUID.randomUUID() + "/";
    }

    @AfterEach
    void limpiar() {
        almacen.borrar(prefijo + "a");
    }

    @Test
    void loGuardadoSeLeeIgual() {
        byte[] contenido = "%PDF-contenido".getBytes(StandardCharsets.UTF_8);
        almacen.guardar(prefijo + "a", contenido, "application/pdf");
        assertThat(almacen.leer(prefijo + "a")).hasValue(contenido);
    }

    @Test
    void guardarDeNuevoReemplaza() {
        almacen.guardar(prefijo + "a", new byte[] {1}, "application/octet-stream");
        almacen.guardar(prefijo + "a", new byte[] {2}, "application/octet-stream");
        assertThat(almacen.leer(prefijo + "a")).hasValue(new byte[] {2});
    }

    @Test
    void unaClaveInexistenteSeLeeVacia() {
        assertThat(almacen.leer(prefijo + "no-existe")).isEmpty();
    }

    @Test
    void loBorradoYaNoSeLeeYBorrarDosVecesNoFalla() {
        almacen.guardar(prefijo + "a", new byte[] {1}, "application/octet-stream");
        almacen.borrar(prefijo + "a");
        almacen.borrar(prefijo + "a");
        assertThat(almacen.leer(prefijo + "a")).isEmpty();
    }
}
