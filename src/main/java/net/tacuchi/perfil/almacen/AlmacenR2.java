package net.tacuchi.perfil.almacen;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Cloudflare R2 por su API compatible con S3. */
@RequiredArgsConstructor
public class AlmacenR2 implements AlmacenArchivos {

    private final S3Client s3;
    private final String bucket;

    @Override
    public void guardar(String clave, byte[] contenido, String tipo) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(clave).contentType(tipo).build(),
                RequestBody.fromBytes(contenido));
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(clave).build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void borrar(String clave) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
    }
}
