package net.tacuchi.perfil.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.clasificacion.Clasificador;
import net.tacuchi.perfil.clasificacion.Texto;
import net.tacuchi.perfil.fuentes.FuenteOfertas;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.repository.OfertaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Guarda lo que entregó una lectura: una ficha por oferta y fuente, clasificada. */
@Component
@RequiredArgsConstructor
public class RegistroOfertas {

    private final Clasificador clasificador;
    private final OfertaRepository ofertaRepository;

    @Transactional
    public void registrar(Fuente fuente, FuenteOfertas.Lectura lectura, Instant inicio) {
        for (OfertaRecogida oferta : lectura.ofertas()) {
            ofertaRepository.registrar(oferta, clasificador.clasificar(oferta), claveSimilitud(oferta), inicio);
        }
        if (lectura.completa()) {
            ofertaRepository.marcarNoPublicadas(fuente, inicio);
        }
    }

    /** Posible repetida: misma empresa y mismo título, sin importar mayúsculas, acentos ni signos. */
    static String claveSimilitud(OfertaRecogida oferta) {
        return Texto.clave(oferta.empresa()) + "|" + Texto.clave(oferta.titulo());
    }
}
