package net.tacuchi.perfil.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.model.Reglas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Único punto que traduce errores a respuestas (RFC 9457). Al cliente nunca le llega el detalle interno. */
@Slf4j
@RestControllerAdvice
public class ManejadorErrores {

    static final String ERROR_INESPERADO = "Ocurrió un error inesperado. Inténtalo de nuevo.";

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail peticionInvalida(MethodArgumentNotValidException ex) {
        List<String> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
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

    private static ProblemDetail rechazo(HttpStatus estado, String mensaje, String campo,
            ArchivoInvalidoException.Motivo motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensaje);
        problema.setProperty("campo", campo);
        problema.setProperty("motivo", motivo.name());
        return problema;
    }
}
