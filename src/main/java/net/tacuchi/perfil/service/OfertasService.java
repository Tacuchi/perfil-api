package net.tacuchi.perfil.service;

import java.util.List;
import net.tacuchi.perfil.dto.ConsultaOfertas;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.dto.TecnologiasResponse;
import net.tacuchi.perfil.model.EstadoOferta;

public interface OfertasService {

    List<OfertaResponse> listar(ConsultaOfertas consulta);

    OfertaDetalleResponse detalle(long id);

    OfertaDetalleResponse cambiarEstado(long id, EstadoOferta estado);

    TecnologiasResponse tecnologias();
}
