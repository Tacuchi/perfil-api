package net.tacuchi.perfil.local;

import com.icegreen.greenmail.util.GreenMail;
import net.tacuchi.perfil.soporte.AlmacenDePruebaConfig;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.RelojDePruebaConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Dobles del arranque local: almacén en memoria, reloj adelantable, fuentes guardadas y correo de prueba. */
@TestConfiguration(proxyBeanMethods = false)
@Import({AlmacenDePruebaConfig.class, RelojDePruebaConfig.class, ControlLocal.class})
public class ArranqueLocalConfig {

    private static FuentesDePrueba fuentes;
    private static GreenMail correo;

    /** El arranque local levanta fuentes y correo antes del contexto, para pasarle sus direcciones. */
    public static void preparar(FuentesDePrueba fuentesLevantadas, GreenMail correoLevantado) {
        fuentes = fuentesLevantadas;
        correo = correoLevantado;
    }

    @Bean
    FuentesDePrueba fuentesDePrueba() {
        return fuentes;
    }

    @Bean
    GreenMail servidorDeCorreo() {
        return correo;
    }
}
