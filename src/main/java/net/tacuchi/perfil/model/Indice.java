package net.tacuchi.perfil.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El historial completo: las versiones en orden de carga y cuál es la vigente.
 * Inmutable: cada cambio devuelve un índice nuevo, que se guarda entero.
 */
public record Indice(String vigente, List<Version> versiones) {

    public Indice {
        versiones = List.copyOf(versiones);
    }

    public static Indice vacio() {
        return new Indice(null, List.of());
    }

    public Optional<Version> buscar(String id) {
        return versiones.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    public Optional<Version> versionVigente() {
        return vigente == null ? Optional.empty() : buscar(vigente);
    }

    /** La versión nueva entra al final y pasa a ser la vigente. */
    public Indice agregar(Version nueva) {
        List<Version> lista = new ArrayList<>(versiones);
        lista.add(nueva);
        return new Indice(nueva.id(), lista);
    }

    public Indice conVigente(String id) {
        return new Indice(id, versiones);
    }

    /** Las que sobran sobre el tope: las más antiguas, nunca una protegida. */
    public List<Version> excedentes(int tope, Set<String> protegidas) {
        int sobran = versiones.size() - tope;
        if (sobran <= 0) {
            return List.of();
        }
        return versiones.stream()
                .filter(v -> !protegidas.contains(v.id()))
                .sorted(Comparator.comparing(Version::fecha))
                .limit(sobran)
                .toList();
    }

    public Indice sin(Collection<Version> fuera) {
        Set<String> ids = fuera.stream().map(Version::id).collect(Collectors.toSet());
        return new Indice(vigente, versiones.stream().filter(v -> !ids.contains(v.id())).toList());
    }
}
