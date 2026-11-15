package net.tacuchi.perfil.clasificacion;

import java.util.regex.Pattern;

/** Lee ubicaciones escritas a mano por cada sitio («Worldwide», «LATAM, Europe», «USA timezones»...). */
public final class Ubicaciones {

    private static final Pattern ADMITE_PERU = Pattern.compile("(?<![a-z])(worldwide|anywhere|global|latam"
            + "|latin america|latinoamerica|south america|americas|sudamerica|peru|lima)(?![a-z])");
    private static final Pattern SIN_RESTRICCION_INFORMADA = Pattern.compile("^(remote|remoto|remota|100% remote)?$");
    private static final Pattern LIMA = Pattern.compile("(?<![a-z])lima(?![a-z])");

    private Ubicaciones() {
    }

    /** Si un trabajo remoto admite candidatos desde Perú: null cuando el texto no dice ninguna región. */
    public static Boolean remotoAdmitePeru(String ubicacion) {
        String texto = Texto.normalizar(ubicacion);
        if (SIN_RESTRICCION_INFORMADA.matcher(texto).matches()) {
            return null;
        }
        return ADMITE_PERU.matcher(texto).find();
    }

    /** Si un trabajo híbrido o presencial es en Lima: null cuando no dice dónde. */
    public static Boolean enLima(String ubicacion) {
        String texto = Texto.normalizar(ubicacion);
        if (texto.isEmpty()) {
            return null;
        }
        return LIMA.matcher(texto).find();
    }
}
