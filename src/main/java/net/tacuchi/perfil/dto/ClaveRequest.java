package net.tacuchi.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** La clave de un servicio de pago tal como la escribe la persona. Nunca se muestra: tampoco al imprimirse. */
public record ClaveRequest(@NotBlank @Size(max = 500) String clave) {

    @Override
    public String toString() {
        return "ClaveRequest[clave=oculta]";
    }
}
