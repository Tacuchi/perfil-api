package net.tacuchi.perfil.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import net.tacuchi.perfil.exception.ArchivoInvalidoException;
import net.tacuchi.perfil.exception.ArchivoInvalidoException.Motivo;
import net.tacuchi.perfil.model.Reglas;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Comprueba el contenido real de cada archivo, no su nombre ni el tipo que declara el navegador.
 * Primero el tamaño, que es barato; después el tipo.
 */
@Component
public class ValidadorArchivos {

    public static final String CAMPO_PDF = "pdf";
    public static final String CAMPO_WORD = "word";

    private static final byte[] FIRMA_PDF = "%PDF-".getBytes();
    private static final String ENTRADA_WORD = "word/document.xml";

    public byte[] validarPdf(MultipartFile pdf) {
        if (pdf == null || pdf.isEmpty()) {
            throw new ArchivoInvalidoException(CAMPO_PDF, Motivo.FALTA, "Falta el PDF del CV.");
        }
        validarTamano(pdf, CAMPO_PDF, "El PDF");
        byte[] contenido = leer(pdf);
        if (contenido.length < FIRMA_PDF.length
                || !Arrays.equals(Arrays.copyOf(contenido, FIRMA_PDF.length), FIRMA_PDF)) {
            throw new ArchivoInvalidoException(CAMPO_PDF, Motivo.TIPO, "El archivo principal no es un PDF.");
        }
        return contenido;
    }

    /** El Word es opcional: sin archivo devuelve null. */
    public byte[] validarWord(MultipartFile word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        validarTamano(word, CAMPO_WORD, "El Word");
        byte[] contenido = leer(word);
        if (!esDocx(contenido)) {
            throw new ArchivoInvalidoException(CAMPO_WORD, Motivo.TIPO,
                    "El adjunto no es un documento de Word (.docx).");
        }
        return contenido;
    }

    private static void validarTamano(MultipartFile archivo, String campo, String nombre) {
        if (archivo.getSize() > Reglas.TAMANO_MAXIMO_BYTES) {
            throw new ArchivoInvalidoException(campo, Motivo.TAMANO,
                    nombre + " supera el tamaño máximo de " + Reglas.TAMANO_MAXIMO_TEXTO + ".");
        }
    }

    /** Un .docx es un ZIP que contiene word/document.xml. */
    private static boolean esDocx(byte[] contenido) {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(contenido))) {
            for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                if (ENTRADA_WORD.equals(entrada.getName())) {
                    return true;
                }
            }
            return false;
        } catch (ZipException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] leer(MultipartFile archivo) {
        try (InputStream entrada = archivo.getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
