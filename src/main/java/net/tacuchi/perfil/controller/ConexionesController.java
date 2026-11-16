package net.tacuchi.perfil.controller;

import net.tacuchi.perfil.dto.HistorialLecturasResponse;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.ClaveRequest;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.TopeRequest;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.ConexionesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conexiones del monitor. Exige a la persona identificada; pausar, reanudar, cargar la clave y fijar el tope exigen
 * además venir del panel.
 */
@RestController
@RequestMapping("/api/fuentes")
@RequiredArgsConstructor
public class ConexionesController {

    private final ConexionesService conexionesService;

    @GetMapping
    public List<FuenteResponse> listar() {
        return conexionesService.listar();
    }

    @GetMapping("/{fuente}/historial")
    public HistorialLecturasResponse historial(@PathVariable Fuente fuente,
            @RequestParam(required = false) Long antesDe) {
        return conexionesService.historial(fuente, antesDe);
    }

    @PostMapping("/{fuente}/pausar")
    public FuenteResponse pausar(@PathVariable Fuente fuente) {
        return conexionesService.pausar(fuente);
    }

    @PostMapping("/{fuente}/reanudar")
    public FuenteResponse reanudar(@PathVariable Fuente fuente) {
        return conexionesService.reanudar(fuente);
    }

    @PutMapping("/{fuente}/clave")
    public FuenteResponse cargarClave(@PathVariable Fuente fuente, @Valid @RequestBody ClaveRequest peticion) {
        return conexionesService.cargarClave(fuente, peticion.clave());
    }

    @PutMapping("/{fuente}/tope")
    public FuenteResponse fijarTope(@PathVariable Fuente fuente, @Valid @RequestBody TopeRequest peticion) {
        return conexionesService.fijarTope(fuente, peticion.topeMensual());
    }
}
