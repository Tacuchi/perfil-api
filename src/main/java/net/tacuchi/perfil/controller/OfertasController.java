package net.tacuchi.perfil.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.dto.CambioEstadoRequest;
import net.tacuchi.perfil.dto.ConsultaOfertas;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.TecnologiasResponse;
import net.tacuchi.perfil.service.OfertasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Monitor de ofertas. Todas sus rutas exigen a la persona identificada. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfertasController {

    private final OfertasService ofertasService;

    @GetMapping("/ofertas")
    public List<OfertaResponse> listar(@ModelAttribute ConsultaOfertas consulta) {
        return ofertasService.listar(consulta);
    }

    @GetMapping("/ofertas/{id}")
    public OfertaDetalleResponse detalle(@PathVariable long id) {
        return ofertasService.detalle(id);
    }

    @PatchMapping("/ofertas/{id}")
    public OfertaDetalleResponse cambiarEstado(@PathVariable long id, @Valid @RequestBody CambioEstadoRequest peticion) {
        return ofertasService.cambiarEstado(id, peticion.estado());
    }

    @GetMapping("/fuentes")
    public List<FuenteResponse> fuentes() {
        return ofertasService.fuentes();
    }

    @GetMapping("/tecnologias")
    public TecnologiasResponse tecnologias() {
        return ofertasService.tecnologias();
    }
}
