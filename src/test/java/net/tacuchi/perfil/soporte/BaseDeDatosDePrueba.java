package net.tacuchi.perfil.soporte;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Un PostgreSQL real, sin Docker, para todas las pruebas de la JVM: se levanta una vez y se apaga al salir.
 * Las pruebas usan el mismo esquema y las mismas restricciones que producción.
 */
public final class BaseDeDatosDePrueba {

    private static EmbeddedPostgres postgres;

    private BaseDeDatosDePrueba() {
    }

    public static synchronized String url() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("no se pudo levantar el PostgreSQL de pruebas", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        }
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    public static String usuario() {
        return "postgres";
    }

    public static String clave() {
        return "postgres";
    }
}
