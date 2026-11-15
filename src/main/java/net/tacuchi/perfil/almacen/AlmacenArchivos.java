package net.tacuchi.perfil.almacen;

import java.util.Optional;

/** Dónde viven los archivos del CV y el índice. Un solo contrato para la forma real y la de pruebas. */
public interface AlmacenArchivos {

    void guardar(String clave, byte[] contenido, String tipo);

    /** Vacío solo si la clave no existe; cualquier otro fallo se lanza. */
    Optional<byte[]> leer(String clave);

    /** Borrar una clave que no existe no es un error. */
    void borrar(String clave);
}
