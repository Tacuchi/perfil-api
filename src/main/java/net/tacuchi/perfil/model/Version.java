package net.tacuchi.perfil.model;

import java.time.Instant;

/** Una carga del CV: su PDF y, si se adjuntó, su Word. */
public record Version(String id, Instant fecha, boolean conWord) {

    public String clavePdf() {
        return "versiones/" + id + "/cv.pdf";
    }

    public String claveWord() {
        return "versiones/" + id + "/cv.docx";
    }
}
