package net.tacuchi.perfil.soporte;

import java.util.List;
import net.tacuchi.perfil.dto.FuenteResponse;
import net.tacuchi.perfil.dto.OfertaDetalleResponse;
import net.tacuchi.perfil.dto.OfertaResponse;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.service.RegistroOfertas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

/** Base de las pruebas del monitor de ofertas: registrar lo que entregaría una lectura y leerlo por HTTP. */
public abstract class PruebaOfertas extends PruebaHttp {

    @Autowired
    protected RegistroOfertas registro;

    @Autowired
    protected RecoleccionService recoleccion;

    /** Lo que entregaría una lectura completa de la fuente en este instante del reloj de prueba. */
    protected void leidas(Fuente fuente, OfertaRecogida... ofertas) {
        registro.registrar(fuente, new FuenteOfertas.Lectura(List.of(ofertas), true), reloj.instant());
    }

    protected void leidasEnParte(Fuente fuente, OfertaRecogida... ofertas) {
        registro.registrar(fuente, new FuenteOfertas.Lectura(List.of(ofertas), false), reloj.instant());
    }

    protected List<OfertaResponse> lista(String consulta) {
        return getIdentificado("/api/ofertas" + consulta).expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<OfertaResponse>>() { })
                .returnResult().getResponseBody();
    }

    protected List<OfertaResponse> todas() {
        return lista("?encaje=ALTO,MEDIO,NO_ENCAJA&estado=NUEVA,GUARDADA,DESCARTADA,POSTULADA");
    }

    protected OfertaResponse unica(String consulta) {
        List<OfertaResponse> ofertas = lista(consulta);
        if (ofertas.size() != 1) {
            throw new AssertionError("se esperaba una oferta para «" + consulta + "» y llegaron " + ofertas.size());
        }
        return ofertas.getFirst();
    }

    protected OfertaDetalleResponse detalle(long id) {
        return getIdentificado("/api/ofertas/" + id).expectStatus().isOk()
                .expectBody(OfertaDetalleResponse.class).returnResult().getResponseBody();
    }

    protected OfertaDetalleResponse marcar(long id, String estado) {
        return patchIdentificado("/api/ofertas/" + id, "{\"estado\":\"" + estado + "\"}").expectStatus().isOk()
                .expectBody(OfertaDetalleResponse.class).returnResult().getResponseBody();
    }

    protected List<FuenteResponse> fuentes() {
        return getIdentificado("/api/fuentes").expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<FuenteResponse>>() { })
                .returnResult().getResponseBody();
    }

    protected FuenteResponse fuente(Fuente fuente) {
        return fuentes().stream().filter(f -> f.fuente() == fuente).findFirst().orElseThrow();
    }
}
