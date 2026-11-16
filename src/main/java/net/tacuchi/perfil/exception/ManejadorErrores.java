package net.tacuchi.perfil.exception;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.model.Reglas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Único punto que traduce errores a respuestas (RFC 9457). Al cliente nunca le llega el detalle interno. */
@Slf4j
@RestControllerAdvice
public class ManejadorErrores {

    static final String ERROR_INESPERADO = "Ocurrió un error inesperado. Inténtalo de nuevo.";
    static final String CUERPO_ILEGIBLE = "El cuerpo no es válido: se espera JSON como {\"estado\": \"GUARDADA\"}, "
            + "con estado GUARDADA, DESCARTADA o POSTULADA.";

    @ExceptionHandler(ArchivoInvalidoException.class)
    ProblemDetail archivoInvalido(ArchivoInvalidoException ex) {
        HttpStatus estado = switch (ex.getMotivo()) {
            case TAMANO -> HttpStatus.CONTENT_TOO_LARGE;
            case TIPO -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case FALTA -> HttpStatus.BAD_REQUEST;
        };
        return rechazo(estado, ex.getMessage(), ex.getCampo(), ex.getMotivo());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail parteFaltante(MissingServletRequestPartException ex) {
        return rechazo(HttpStatus.BAD_REQUEST, "Falta el archivo «" + ex.getRequestPartName() + "».",
                ex.getRequestPartName(), ArchivoInvalidoException.Motivo.FALTA);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail cargaDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return rechazo(HttpStatus.CONTENT_TOO_LARGE,
                "La carga supera el tamaño máximo de " + Reglas.TAMANO_MAXIMO_TEXTO + " por archivo.",
                null, ArchivoInvalidoException.Motivo.TAMANO);
    }

    @ExceptionHandler(VersionNoEncontradaException.class)
    ProblemDetail versionNoEncontrada(VersionNoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OfertaNoEncontradaException.class)
    ProblemDetail ofertaNoEncontrada(OfertaNoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EstadoNoPermitidoException.class)
    ProblemDetail estadoNoPermitido(EstadoNoPermitidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RecoleccionRechazadaException.class)
    ProblemDetail recoleccionRechazada(RecoleccionRechazadaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    /** Una conexión que no es de pago no tiene clave ni tope (400); sin la llave del servidor no se cifra (409). */
    @ExceptionHandler(ConfiguracionRechazadaException.class)
    ProblemDetail configuracionRechazada(ConfiguracionRechazadaException ex) {
        HttpStatus estado = ex.getMotivo() == ConfiguracionRechazadaException.Motivo.NO_ES_DE_PAGO
                ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    /** Un código que no abre ninguna corrida es 403; un paso fuera de orden, 409. */
    @ExceptionHandler(CorridaRechazadaException.class)
    ProblemDetail corridaRechazada(CorridaRechazadaException ex) {
        HttpStatus estado = ex.getMotivo() == CorridaRechazadaException.Motivo.CODIGO
                ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    /** Un cuerpo ilegible o con un valor fuera de catálogo: se dice qué se esperaba, sin el texto interno. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, CUERPO_ILEGIBLE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ex.getName() + ": valor no válido «" + ex.getValue() + "»" + validos(ex.getRequiredType()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail peticionInvalida(MethodArgumentNotValidException ex) {
        List<String> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? error.getField() + ": valor no válido «" + error.getRejectedValue() + "»"
                                + validos(tipoDeCampo(ex, error.getField()))
                        : error.getField() + ": " + error.getDefaultMessage())
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, String.join("; ", errores));
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        // Las excepciones propias de Spring MVC (método no admitido, ruta inexistente...) ya traen su estado.
        if (ex instanceof ErrorResponse respuesta) {
            return respuesta.getBody();
        }
        log.error("[ERROR] fallo no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INESPERADO);
    }

    /** Valores admitidos cuando el parámetro es un catálogo. */
    private static String validos(Class<?> tipo) {
        if (tipo == null || !tipo.isEnum()) {
            return "";
        }
        return "; valores válidos: " + Arrays.stream(tipo.getEnumConstants()).map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    /** Tipo de un campo de una consulta en record; de una lista, el de sus elementos. */
    private static Class<?> tipoDeCampo(MethodArgumentNotValidException ex, String campo) {
        String nombre = campo.replaceAll("\\[.*", "");
        Class<?> objetivo = ex.getParameter().getParameterType();
        if (objetivo.isRecord()) {
            for (RecordComponent componente : objetivo.getRecordComponents()) {
                if (componente.getName().equals(nombre)) {
                    return componente.getGenericType() instanceof ParameterizedType lista
                            && lista.getActualTypeArguments()[0] instanceof Class<?> elemento
                            ? elemento : componente.getType();
                }
            }
        }
        return ex.getBindingResult().getFieldType(nombre);
    }

    private static ProblemDetail rechazo(HttpStatus estado, String mensaje, String campo,
            ArchivoInvalidoException.Motivo motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensaje);
        problema.setProperty("campo", campo);
        problema.setProperty("motivo", motivo.name());
        return problema;
    }
}
