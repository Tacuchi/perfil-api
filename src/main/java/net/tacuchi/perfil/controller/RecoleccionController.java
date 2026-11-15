package net.tacuchi.perfil.controller;

import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.RecoleccionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recolección a demanda y su avance. Exige a la persona identificada; lanzar exige además venir del panel. Un
 * lanzamiento responde al instante con lo que se va a leer y lo omitido; la lectura sigue en otro hilo.
 */
@RestController
@RequestMapping("/api/recoleccion")
@RequiredArgsConstructor
public class RecoleccionController {

    private final RecoleccionService recoleccionService;

    @GetMapping
    public RecoleccionResponse avance() {
        return recoleccionService.avance();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecoleccionResponse lanzar() {
        return recoleccionService.lanzar();
    }

    @PostMapping("/{fuente}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecoleccionResponse lanzar(@PathVariable Fuente fuente) {
        return recoleccionService.lanzar(fuente);
    }
}
