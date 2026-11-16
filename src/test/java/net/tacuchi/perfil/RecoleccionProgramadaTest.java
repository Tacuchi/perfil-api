package net.tacuchi.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;

/**
 * La recolección programada se conserva aunque en producción esté apagada: aquí el horario se dispara cada segundo y
 * nadie la llama.
 */
@TestPropertySource(properties = "prueba.cron=* * * * * *")
class RecoleccionProgramadaTest extends PruebaOfertas {

    @Test
    void laRecoleccionCorreSinQueNadieLaDispareYLeeCadaFuenteUnaVezAlDia() throws InterruptedException {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        while (FUENTES.lecturas(Fuente.REMOTE_OK) == 0 && Instant.now().isBefore(limite)) {
            Thread.sleep(100);
        }
        Thread.sleep(2500);

        assertThat(FUENTES.lecturas(Fuente.REMOTIVE)).isEqualTo(1);
        assertThat(FUENTES.lecturas(Fuente.REMOTE_OK)).isEqualTo(1);
        assertThat(todas()).isNotEmpty();
    }

    /** En producción nada se lee solo: el horario por defecto está apagado y se enciende por variable de entorno. */
    @Test
    void elHorarioDeProduccionEstaApagado() throws IOException {
        Properties propiedades = new Properties();
        try (var entrada = new ClassPathResource("application.properties").getInputStream()) {
            propiedades.load(entrada);
        }

        assertThat(propiedades.getProperty("perfil.ofertas.cron")).isEqualTo(Scheduled.CRON_DISABLED);
    }
}
