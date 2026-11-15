package net.tacuchi.perfil.soporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Archivos mínimos con el contenido real de cada tipo. */
public final class Archivos {

    private Archivos() {
    }

    public static byte[] pdf(String marca) {
        return ("%PDF-1.7\n% " + marca + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] docx(String marca) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<document>" + marca + "</document>").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return salida.toByteArray();
    }

    public static byte[] zipSinWord() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry("otra/cosa.txt"));
            zip.write(1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return salida.toByteArray();
    }

    /** Un PDF válido de más de 10 MB. */
    public static byte[] pdfGrande() {
        byte[] cabecera = pdf("grande");
        byte[] contenido = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(cabecera, 0, contenido, 0, cabecera.length);
        return contenido;
    }
}
