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
public class LinkedinFuente implements FuenteOfertas {
    private final BusquedasApify lector;
    private final ActoresApifyProperties actores;
    private final PerfilProperties propiedades;
    private final JsonMapper json;
    @Override public Fuente fuente() { return Fuente.LINKEDIN; }

    @Override public Lectura leer() {
        var terminos = propiedades.computrabajo().terminos();
        List<BusquedasApify.Busqueda> busquedas = new ArrayList<>();
        for (String ambito : List.of("Perú", "Remoto para Latinoamérica")) {
            for (int i = 0; i < terminos.size(); i++) {
                int cuota = i == 2 ? 7 : 9;
                var entrada = json.createObjectNode();
                entrada.put("keywords", terminos.get(i));
                entrada.put("datePosted", "r604800");
                entrada.put("limit", cuota);
                entrada.put("location", ambito.equals("Perú") ? "Peru" : "Latin America");
                if (!ambito.equals("Perú")) {
                    entrada.putArray("urlParam").addObject().put("key", "f_WT").put("value", "2");
                }
                busquedas.add(new BusquedasApify.Busqueda(ambito, terminos.get(i), cuota, entrada));
            }
        }
        return lector.leer(fuente(), actores.linkedin(), busquedas, new BigDecimal("0.001"),
                new BigDecimal("0.0004"), (busqueda, aviso) -> convertir(aviso, busqueda.entrada().has("urlParam")));
    }

    static OfertaRecogida convertir(JsonNode aviso) {
        return convertir(aviso, false);
    }

    static OfertaRecogida convertir(JsonNode aviso, boolean remoto) {
        var enlace = AvisoApify.enlace(aviso, Fuente.LINKEDIN);
        String texto = AvisoApify.descripcion(aviso);
        Modalidad modalidad = remoto ? Modalidad.REMOTO : Modalidad.NO_INFORMA;
        Boolean admite = remoto && net.tacuchi.perfil.clasificacion.Ubicaciones.declaraPeru(
                AvisoApify.texto(aviso, "location")) ? Boolean.TRUE : null;
        return new OfertaRecogida(Fuente.LINKEDIN, enlace.id(), AvisoApify.titulo(aviso),
                AvisoApify.texto(aviso, "companyName"), AvisoApify.fecha(aviso),
                AvisoApify.texto(aviso, "location"), modalidad, admite,
                AvisoApify.junior(AvisoApify.texto(aviso, "experienceLevel")),
                Clasificador.detectarIdioma(texto), texto, enlace.url(), null);
    }
}
