package net.tacuchi.perfil.dto;

/** Un archivo listo para entregar: su contenido, su tipo y el nombre con que se guarda. */
public record ArchivoDescarga(byte[] contenido, String tipo, String nombre) {
}
