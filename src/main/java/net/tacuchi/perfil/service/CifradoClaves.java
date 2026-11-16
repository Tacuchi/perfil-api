package net.tacuchi.perfil.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.stereotype.Component;

/**
 * Cifra las claves de las conexiones de pago con AES-GCM del JDK y la llave maestra del servidor. Cada clave lleva
 * su propio vector, y la fuente va como dato asociado: una clave guardada para una fuente no se descifra como de
 * otra. Sin la llave, o con una que no mide 32 bytes, la API arranca igual y no cifra ni descifra nada.
 */
@Slf4j
@Component
public class CifradoClaves {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int BYTES_LLAVE = 32;
    private static final int BYTES_VECTOR = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKey llave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoClaves(PerfilProperties propiedades) {
        this.llave = leerLlave(propiedades.claves().llave());
        if (llave == null) {
            log.warn("[CLAVES] falta la llave de claves del servidor o no mide {} bytes en base64: las conexiones "
                    + "de pago quedan sin configurar", BYTES_LLAVE);
        }
    }

    public boolean disponible() {
        return llave != null;
    }

    /** Vector seguido del texto cifrado con su etiqueta. */
    public byte[] cifrar(Fuente fuente, String clave) {
        exigirLlave();
        byte[] vector = new byte[BYTES_VECTOR];
        azar.nextBytes(vector);
        try {
            Cipher cifrador = cifrador(Cipher.ENCRYPT_MODE, vector, fuente);
            byte[] cifrada = cifrador.doFinal(clave.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(vector.length + cifrada.length).put(vector).put(cifrada).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("no se pudo cifrar la clave", e);
        }
    }

    /** @throws ClaveIlegibleException si se cifró con otra llave, para otra fuente o está dañada */
    public String descifrar(Fuente fuente, byte[] guardada) {
        exigirLlave();
        if (guardada.length <= BYTES_VECTOR) {
            throw new ClaveIlegibleException();
        }
        try {
            Cipher cifrador = cifrador(Cipher.DECRYPT_MODE, Arrays.copyOf(guardada, BYTES_VECTOR), fuente);
            byte[] clara = cifrador.doFinal(guardada, BYTES_VECTOR, guardada.length - BYTES_VECTOR);
            return new String(clara, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new ClaveIlegibleException();
        }
    }

    private Cipher cifrador(int modo, byte[] vector, Fuente fuente) throws GeneralSecurityException {
        Cipher cifrador = Cipher.getInstance(ALGORITMO);
        cifrador.init(modo, llave, new GCMParameterSpec(BITS_ETIQUETA, vector));
        cifrador.updateAAD(fuente.name().getBytes(StandardCharsets.UTF_8));
        return cifrador;
    }

    private void exigirLlave() {
        if (llave == null) {
            throw new IllegalStateException("falta la llave de claves del servidor");
        }
    }

    private static SecretKey leerLlave(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.strip());
            return bytes.length == BYTES_LLAVE ? new SecretKeySpec(bytes, "AES") : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Una clave guardada que esta llave no puede leer. El mensaje no lleva nada de la clave ni de la llave. */
    public static class ClaveIlegibleException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ClaveIlegibleException() {
            super("la clave guardada no se puede leer con la llave del servidor; vuelve a cargarla");
        }
    }
}
