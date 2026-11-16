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

    /** Los avisos de las fuentes de Perú comparten las mismas reglas de ciudad y modalidad. */
    public static Boolean admiteEnPeru(net.tacuchi.perfil.model.Modalidad modalidad, String ubicacion) {
        return switch (modalidad) {
            case REMOTO -> Boolean.TRUE;
            case HIBRIDO, PRESENCIAL -> enLima(ubicacion);
            case NO_INFORMA -> null;
        };
    }

    /** Una ubicación que declara Perú confirma el país; otro país nunca confirma elegibilidad. */
    public static boolean declaraPeru(String ubicacion) {
        return Pattern.compile("(?<![a-z])peru(?![a-z])").matcher(Texto.normalizar(ubicacion)).find();
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
