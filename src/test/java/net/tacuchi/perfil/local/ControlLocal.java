package net.tacuchi.perfil.local;

import com.icegreen.greenmail.util.GreenMail;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.RelojDePrueba;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mandos del arranque local para recorrer el monitor de ofertas: recoger ahora, pasar al día siguiente,
 * hacer que una fuente falle o bloquee y ver los correos recibidos. Solo existe en el classpath de pruebas
 * y solo se registra en el arranque local; pasa por la misma identificación que el resto de /api.
 */
@TestComponent
@RestController
@RequestMapping("/api/local")
@RequiredArgsConstructor
public class ControlLocal {

    private final RecoleccionService recoleccion;
    private final RelojDePrueba reloj;
    private final FuentesDePrueba fuentes;
    private final GreenMail correo;

    @PostMapping("/recoger")
    public Map<String, Object> recoger() {
        recoleccion.recoger();
        return Map.of("ahora", reloj.instant().toString());
    }

    /** Adelanta el reloj un día y recoge: la recolección del día siguiente. */
    @PostMapping("/dia-siguiente")
    public Map<String, Object> diaSiguiente() {
        reloj.adelantar(Duration.ofDays(1));
        return recoger();
    }

    @PutMapping("/fuentes/{fuente}")
    public Map<String, Object> modo(@PathVariable Fuente fuente, @RequestParam FuentesDePrueba.Modo modo) {
        fuentes.modo(fuente, modo);
        return Map.of("fuente", fuente, "modo", modo);
    }

    @GetMapping("/correos")
    public List<Map<String, Object>> correos() throws MessagingException {
        List<Map<String, Object>> recibidos = new ArrayList<>();
        for (MimeMessage mensaje : correo.getReceivedMessages()) {
            recibidos.add(Map.of(
                    "asunto", mensaje.getSubject(),
                    "para", Arrays.stream(mensaje.getAllRecipients()).map(Address::toString).toList()));
        }
        return recibidos;
    }
}
