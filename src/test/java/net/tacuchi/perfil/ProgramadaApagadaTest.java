package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.tacuchi.perfil.soporte.BaseDeDatosDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** Nada se lee solo: con la configuración por defecto la API arranca sin programar ninguna recolección. */
class ProgramadaApagadaTest {

    /** Lo mínimo para arrancar; el horario, si no se indica, es el de application.properties. */
    private static Map<String, Object> propiedades() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("perfil.access.emisor", "https://equipo.cloudflareaccess.com");
        propiedades.put("perfil.access.aud", "aud");
        propiedades.put("perfil.access.correo", "persona@ejemplo.test");
        propiedades.put("perfil.almacen.endpoint", "https://cuenta.r2.cloudflarestorage.com");
        propiedades.put("perfil.almacen.bucket", "cv");
        propiedades.put("perfil.almacen.acceso", "id");
        propiedades.put("perfil.almacen.secreto", "secreto");
        propiedades.put("perfil.cv.archivo", "Nombre-CV.pdf");
        propiedades.put("perfil.sitio.url", "https://sitio.test");
        propiedades.put("perfil.panel.origen", "https://panel.test");
        propiedades.put("perfil.aviso.activo", "false");
        propiedades.put("server.port", "0");
        propiedades.put("spring.datasource.url", BaseDeDatosDePrueba.url());
        propiedades.put("spring.datasource.username", BaseDeDatosDePrueba.usuario());
        propiedades.put("spring.datasource.password", BaseDeDatosDePrueba.clave());
        return propiedades;
    }

    /**
     * Las propiedades del arranque son las de menor prioridad; los argumentos, como una variable de entorno en
     * producción, pasan por encima de application.properties.
     */
    private static Set<String> horarios(String... argumentos) {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(PerfilApiApplication.class)
                .web(WebApplicationType.SERVLET).properties(propiedades()).run(argumentos)) {
            return contexto.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                    .flatMap(h -> h.getScheduledTasks().stream())
                    .map(ScheduledTask::getTask)
                    .filter(CronTask.class::isInstance)
                    .map(t -> ((CronTask) t).getExpression())
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void conLaConfiguracionPorDefectoNoSeProgramaNingunaRecoleccion() {
        assertThat(horarios()).isEmpty();
    }

    @Test
    void conUnHorarioIndicadoLaRecoleccionSeProgramaComoAntes() {
        assertThat(horarios("--perfil.ofertas.cron=0 0 * * * *")).containsExactly("0 0 * * * *");
    }
}
