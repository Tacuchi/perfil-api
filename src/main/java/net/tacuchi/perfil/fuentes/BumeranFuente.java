package net.tacuchi.perfil.fuentes;

import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.clasificacion.Clasificador;
import net.tacuchi.perfil.config.ActoresApifyProperties;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class BumeranFuente implements FuenteOfertas {
    private final BusquedasApify lector;
    private final ActoresApifyProperties actores;
    private final PerfilProperties propiedades;
    private final JsonMapper json;
    @Override public Fuente fuente() { return Fuente.BUMERAN; }

    @Override public Lectura leer() {
        var terminos = propiedades.computrabajo().terminos();
        List<BusquedasApify.Busqueda> busquedas = new ArrayList<>();
        for (int i = 0; i < terminos.size(); i++) {
            int cuota = i == 2 ? 7 : 9;
            var entrada = json.createObjectNode();
            entrada.put("query", terminos.get(i));
            entrada.put("country", "PE");
            entrada.put("sort", "RECIENTES");
            entrada.put("maxResults", cuota);
            entrada.put("includeDetails", true);
            entrada.put("descriptionFormat", "text");
            busquedas.add(new BusquedasApify.Busqueda("Perú", terminos.get(i), cuota, entrada));
        }
        return lector.leer(fuente(), actores.bumeran(), busquedas, new BigDecimal("0.0005"),
                new BigDecimal("0.001"), (busqueda, aviso) -> convertir(aviso));
    }

    static OfertaRecogida convertir(JsonNode aviso) {
        var enlace = AvisoApify.enlace(aviso, Fuente.BUMERAN);
        String texto = AvisoApify.descripcion(aviso);
        Modalidad modalidad = AvisoApify.modalidad(AvisoApify.texto(aviso, "workModality"));
        return new OfertaRecogida(Fuente.BUMERAN, enlace.id(), AvisoApify.titulo(aviso),
                AvisoApify.texto(aviso, "company"), AvisoApify.fecha(aviso),
                AvisoApify.texto(aviso, "location"), modalidad,
                net.tacuchi.perfil.clasificacion.Ubicaciones.admiteEnPeru(modalidad, AvisoApify.texto(aviso, "location")),
                AvisoApify.junior(AvisoApify.texto(aviso, "seniorityLevel")),
                Clasificador.detectarIdioma(texto), texto, enlace.url(), null);
    }
}
