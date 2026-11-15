package net.tacuchi.perfil.almacen;

import net.tacuchi.perfil.config.AlmacenConfig;
import net.tacuchi.perfil.config.PerfilProperties;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** El mismo contrato contra R2. Corre solo con credenciales: PERFIL_ALMACEN_ENDPOINT, _BUCKET, _ACCESO y _SECRETO. */
@EnabledIfEnvironmentVariable(named = "PERFIL_ALMACEN_ENDPOINT", matches = ".+")
class AlmacenR2ContratoTest extends ContratoAlmacen {

    @Override
    protected AlmacenArchivos crear() {
        PerfilProperties.Almacen datos = new PerfilProperties.Almacen(
                System.getenv("PERFIL_ALMACEN_ENDPOINT"), System.getenv("PERFIL_ALMACEN_BUCKET"),
                System.getenv("PERFIL_ALMACEN_ACCESO"), System.getenv("PERFIL_ALMACEN_SECRETO"));
        PerfilProperties propiedades = new PerfilProperties(null, datos, null, null, null);
        AlmacenConfig config = new AlmacenConfig();
        return config.almacenArchivos(config.s3Client(propiedades), propiedades);
    }
}
