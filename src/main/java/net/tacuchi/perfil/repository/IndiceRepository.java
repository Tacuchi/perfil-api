package net.tacuchi.perfil.repository;

import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.almacen.AlmacenArchivos;
import net.tacuchi.perfil.model.Indice;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** El índice del historial, guardado como JSON junto a los archivos. */
@Repository
@RequiredArgsConstructor
public class IndiceRepository {

    private static final String CLAVE = "indice.json";

    private final AlmacenArchivos almacen;
    private final JsonMapper jsonMapper;

    public Indice leer() {
        return almacen.leer(CLAVE)
                .map(bytes -> jsonMapper.readValue(bytes, Indice.class))
                .orElseGet(Indice::vacio);
    }

    public void guardar(Indice indice) {
        almacen.guardar(CLAVE, jsonMapper.writeValueAsBytes(indice), MediaType.APPLICATION_JSON_VALUE);
    }
}
