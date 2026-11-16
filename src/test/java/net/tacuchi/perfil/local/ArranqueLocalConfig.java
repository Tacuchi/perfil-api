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

    @Bean
    org.springframework.boot.ApplicationRunner sembrarLecturas(
            net.tacuchi.perfil.repository.HistorialLecturasRepository historial,
            net.tacuchi.perfil.repository.FuenteRepository ultima, RelojDePrueba reloj) {
        return args -> {
            var ahora = reloj.instant();
            for (int i = 24; i >= 0; i--) {
                historial.registrar(net.tacuchi.perfil.model.Fuente.GET_ON_BOARD, ahora.minusSeconds(i * 3600L),
                        54000, net.tacuchi.perfil.model.EstadoLectura.CORRECTA, 50, i == 24 ? 50 : 0, null, null);
            }
            ultima.registrar(net.tacuchi.perfil.model.Fuente.GET_ON_BOARD, net.tacuchi.perfil.model.EstadoLectura.CORRECTA,
                    null, ahora, 50);
            historial.registrar(net.tacuchi.perfil.model.Fuente.BUMERAN, ahora.minusSeconds(3600), 12000,
                    net.tacuchi.perfil.model.EstadoLectura.CORRECTA, 12, 3, new java.math.BigDecimal("0.0075"),
                    "Una búsqueda no respondió; se conservaron las demás ofertas");
            historial.registrar(net.tacuchi.perfil.model.Fuente.LINKEDIN, ahora.minusSeconds(60), 8000,
                    net.tacuchi.perfil.model.EstadoLectura.FALLO, 0, 0, new java.math.BigDecimal("0.0015"),
                    "La lectura no trajo respuestas");
            ultima.registrar(net.tacuchi.perfil.model.Fuente.LINKEDIN, net.tacuchi.perfil.model.EstadoLectura.FALLO,
                    "La lectura no trajo respuestas", ahora.minusSeconds(60), 0);
            ultima.registrar(net.tacuchi.perfil.model.Fuente.BUMERAN, net.tacuchi.perfil.model.EstadoLectura.CORRECTA,
                    "Una búsqueda no respondió; se conservaron las demás ofertas", ahora.minusSeconds(3600), 12);
        };
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
