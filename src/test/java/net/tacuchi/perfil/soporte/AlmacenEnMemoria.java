package net.tacuchi.perfil.soporte;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.tacuchi.perfil.almacen.AlmacenArchivos;

/** Almacén de pruebas. Solo existe en el classpath de pruebas: ninguna configuración de producción lo elige. */
public class AlmacenEnMemoria implements AlmacenArchivos {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public void guardar(String clave, byte[] contenido, String tipo) {
        objetos.put(clave, contenido.clone());
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        return Optional.ofNullable(objetos.get(clave)).map(byte[]::clone);
    }

    @Override
    public void borrar(String clave) {
        objetos.remove(clave);
    }

    public Set<String> claves() {
        return Set.copyOf(objetos.keySet());
    }

    public void vaciar() {
        objetos.clear();
    }
}
