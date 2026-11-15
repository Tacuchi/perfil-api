package net.tacuchi.perfil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa la recolección programada. En pruebas se apaga con perfil.ofertas.cron=- y se dispara a mano. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
