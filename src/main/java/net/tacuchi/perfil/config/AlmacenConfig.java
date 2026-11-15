package net.tacuchi.perfil.config;

import java.net.URI;
import net.tacuchi.perfil.almacen.AlmacenArchivos;
import net.tacuchi.perfil.almacen.AlmacenR2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AlmacenConfig {

    /** R2 usa la región "auto" y no admite todas las sumas de verificación que el SDK manda por defecto. */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(PerfilProperties propiedades) {
        PerfilProperties.Almacen almacen = propiedades.almacen();
        return S3Client.builder()
                .endpointOverride(URI.create(almacen.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(almacen.acceso(), almacen.secreto())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    public AlmacenArchivos almacenArchivos(S3Client s3Client, PerfilProperties propiedades) {
        return new AlmacenR2(s3Client, propiedades.almacen().bucket());
    }
}
