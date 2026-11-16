package net.tacuchi.perfil.soporte;

import java.util.List;
import net.tacuchi.perfil.model.Fuente;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

public abstract class PruebaApify extends PruebaOfertas {
    protected RestTestClient.ResponseSpec configurar(Fuente fuente, String campo, String cuerpo) {
        return cliente.put().uri("/api/fuentes/" + fuente + "/" + campo)
                .header(CABECERA_ACCESS, ACCESS.codigoValido()).header("Origin", ORIGEN_PANEL)
                .contentType(MediaType.APPLICATION_JSON).body(cuerpo).exchange();
    }
    protected void clave() {
        configurar(Fuente.COMPUTRABAJO, "clave", "{\"clave\":\"" + ApifyDePrueba.CLAVE + "\"}")
                .expectStatus().isOk();
        for (Fuente fuente : List.of(Fuente.LINKEDIN, Fuente.BUMERAN, Fuente.COMPUTRABAJO)) {
            tope(fuente, "1");
        }
    }
    protected void tope(Fuente fuente, String valor) {
        configurar(fuente, "tope", "{\"topeMensual\":" + valor + "}").expectStatus().isOk();
    }
}
