package net.tacuchi.perfil.soporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Sustituye R2 por el almacén en memoria. Vive en el classpath de pruebas: el jar de producción no lo trae. */
@TestConfiguration(proxyBeanMethods = false)
public class AlmacenDePruebaConfig {

    @Bean
    @Primary
    AlmacenEnMemoria almacenEnMemoria() {
        return new AlmacenEnMemoria();
    }
}
