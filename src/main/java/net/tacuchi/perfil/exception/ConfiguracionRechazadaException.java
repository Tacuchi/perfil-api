package net.tacuchi.perfil.exception;

import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.Fuente;

/** Un cambio de la configuración de una conexión que no se acepta. El mensaje es para la persona. */
public class ConfiguracionRechazadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        NO_ES_DE_PAGO, FALTA_LLAVE
    }

    private final Motivo motivo;

    private ConfiguracionRechazadaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public static ConfiguracionRechazadaException noEsDePago(Fuente fuente) {
        return new ConfiguracionRechazadaException(Motivo.NO_ES_DE_PAGO,
                fuente.nombre() + " no se lee por un servicio de pago: no tiene clave ni tope mensual.");
    }

    public static ConfiguracionRechazadaException faltaLlave() {
        String motivo = Conexion.FALTA_LLAVE;
        return new ConfiguracionRechazadaException(Motivo.FALTA_LLAVE, "No se puede guardar la clave: "
                + motivo + ". Pide que la carguen en el servidor y vuelve a intentarlo.");
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
