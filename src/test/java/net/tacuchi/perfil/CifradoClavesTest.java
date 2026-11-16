package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.CifradoClaves;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** La clave guardada solo se lee con la misma llave y para la misma fuente. */
class CifradoClavesTest {

    private static final String CLAVE = "clave-de-prueba";

    private static CifradoClaves con(String llave) {
        return new CifradoClaves(new PerfilProperties(null, null, null, null, null, null, null, null,
                new PerfilProperties.Claves(llave), null));
    }

    private static String llave(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void laMismaLlaveDescifraLoQueCifro() {
        CifradoClaves cifrado = con(llave("llave-de-prueba-de-32-bytes-0000"));

        assertThat(cifrado.descifrar(Fuente.COMPUTRABAJO, cifrado.cifrar(Fuente.COMPUTRABAJO, CLAVE))).isEqualTo(CLAVE);
    }

    @Test
    void sinLaLlaveCorrectaOParaOtraFuenteNoSeDescifra() {
        CifradoClaves cifrado = con(llave("llave-de-prueba-de-32-bytes-0000"));
        CifradoClaves otra = con(llave("otra-llave-de-prueba-de-32-bytes"));
        byte[] guardada = cifrado.cifrar(Fuente.COMPUTRABAJO, CLAVE);

        assertThatThrownBy(() -> otra.descifrar(Fuente.COMPUTRABAJO, guardada))
                .isInstanceOf(CifradoClaves.ClaveIlegibleException.class)
                .hasMessageNotContaining(CLAVE);
        assertThatThrownBy(() -> cifrado.descifrar(Fuente.REMOTIVE, guardada))
                .isInstanceOf(CifradoClaves.ClaveIlegibleException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bGxhdmUtY29ydGE=", "no es base64 %%%"})
    void sinLlaveOConUnaDeLongitudInvalidaNoHayCifrado(String llave) {
        CifradoClaves cifrado = con(llave);

        assertThat(cifrado.disponible()).isFalse();
        assertThatThrownBy(() -> cifrado.cifrar(Fuente.COMPUTRABAJO, CLAVE)).isInstanceOf(IllegalStateException.class);
    }
}
