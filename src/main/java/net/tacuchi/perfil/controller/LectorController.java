package net.tacuchi.perfil.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.CodigoCorridaRequest;
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.FuenteEnRecoleccion;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.CorridaLocalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas del lector de la máquina de la persona. Solo aceptan el código de Access del token de servicio del lector
 * y, en el cuerpo, el código de la corrida que la persona abrió desde el panel. Ninguna respuesta lo devuelve.
 */
@RestController
@RequestMapping(LectorController.RUTA)
@RequiredArgsConstructor
public class LectorController {

    public static final String RUTA = "/api/lector";

    private final CorridaLocalService corridaLocalService;

    @PostMapping("/corrida/iniciar")
    public CorridaLectorResponse iniciar(@Valid @RequestBody CodigoCorridaRequest peticion) {
        return corridaLocalService.iniciar(peticion.codigo());
    }

    @PostMapping("/corrida/fuentes/{fuente}/empezar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empezar(@PathVariable Fuente fuente, @Valid @RequestBody CodigoCorridaRequest peticion) {
        corridaLocalService.empezar(peticion.codigo(), fuente);
    }

    @PostMapping("/corrida/fuentes/{fuente}/entregar")
    public FuenteEnRecoleccion entregar(@PathVariable Fuente fuente, @Valid @RequestBody EntregaRequest entrega) {
        return corridaLocalService.entregar(entrega.codigo(), fuente, entrega);
    }

    @PostMapping("/corrida/terminar")
    public RecoleccionResponse terminar(@Valid @RequestBody CodigoCorridaRequest peticion) {
        return corridaLocalService.terminar(peticion.codigo());
    }
}
