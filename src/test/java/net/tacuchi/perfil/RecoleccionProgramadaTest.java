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
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.TestPropertySource;

/** La recolección corre sola: aquí el horario se dispara cada segundo y nadie la llama. */
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

    @Test
    void elHorarioDeProduccionIntentaCadaHora() throws IOException {
        Properties propiedades = new Properties();
        try (var entrada = new ClassPathResource("application.properties").getInputStream()) {
            propiedades.load(entrada);
        }
        CronExpression horario = CronExpression.parse(propiedades.getProperty("perfil.ofertas.cron"));
        var ahora = java.time.LocalDateTime.of(2026, 11, 15, 7, 30);

        assertThat(horario.next(ahora)).isEqualTo(java.time.LocalDateTime.of(2026, 11, 15, 8, 0));
    }
}
