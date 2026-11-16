package net.tacuchi.perfil.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.ConexionesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conexiones del monitor. Exige a la persona identificada; pausar y reanudar exigen además venir del panel. */
@RestController
@RequestMapping("/api/fuentes")
@RequiredArgsConstructor
public class ConexionesController {

    private final ConexionesService conexionesService;

    @GetMapping
    public List<FuenteResponse> listar() {
        return conexionesService.listar();
    }

    @PostMapping("/{fuente}/pausar")
    public FuenteResponse pausar(@PathVariable Fuente fuente) {
        return conexionesService.pausar(fuente);
    }

    @PostMapping("/{fuente}/reanudar")
    public FuenteResponse reanudar(@PathVariable Fuente fuente) {
        return conexionesService.reanudar(fuente);
    }
}
