package net.tacuchi.perfil.local;

import com.icegreen.greenmail.util.GreenMail;
import net.tacuchi.perfil.soporte.AlmacenDePruebaConfig;
import net.tacuchi.perfil.soporte.ApifyDePrueba;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.RelojDePrueba;
import net.tacuchi.perfil.soporte.RelojDePruebaConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Dobles del arranque local: almacén en memoria, reloj adelantable, fuentes guardadas, doble de Apify y correo de
 * prueba.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({AlmacenDePruebaConfig.class, RelojDePruebaConfig.class, ControlLocal.class})
public class ArranqueLocalConfig {

    private static FuentesDePrueba fuentes;
    private static GreenMail correo;
    private static ApifyDePrueba apify;

    /** El arranque local levanta fuentes, Apify y correo antes del contexto, para pasarle sus direcciones. */
    public static void preparar(FuentesDePrueba fuentesLevantadas, ApifyDePrueba apifyLevantado,
            GreenMail correoLevantado) {
        fuentes = fuentesLevantadas;
        apify = apifyLevantado;
        correo = correoLevantado;
    }

    /** Las peticiones se cuentan por día del mismo reloj adelantable que usa la API. */
    @Bean
    FuentesDePrueba fuentesDePrueba(RelojDePrueba reloj) {
        fuentes.usarReloj(reloj);
        return fuentes;
    }

    @Bean
    ApifyDePrueba apifyDePrueba() {
        return apify;
    }

    @Bean
    GreenMail servidorDeCorreo() {
        return correo;
    }
}
