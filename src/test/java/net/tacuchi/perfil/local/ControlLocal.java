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
import net.tacuchi.perfil.dto.CorridaLectorResponse;
import net.tacuchi.perfil.dto.EntregaRequest;
import net.tacuchi.perfil.dto.RecoleccionResponse;
import net.tacuchi.perfil.exception.RecoleccionRechazadaException;
import net.tacuchi.perfil.model.FalloMaquina;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.service.CorridaLocalService;
import net.tacuchi.perfil.service.RecoleccionService;
import net.tacuchi.perfil.soporte.ApifyDePrueba;
import net.tacuchi.perfil.soporte.EntregasDePrueba;
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
 * Mandos del arranque local para recorrer el monitor de ofertas: recoger ahora, disparar la programada sin
 * esperarla, pasar al día siguiente, adelantar el reloj sin leer nada, hacer que una fuente falle, bloquee o quede en
 * pausa, recorrer una corrida local sin el lector real, contar las consultas que recibió el doble de Apify y ver los
 * correos recibidos. Solo existe en el classpath de pruebas
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
    private final ApifyDePrueba apify;
    private final CorridaLocalService corridaLocal;

    /** Lo que el lector simulado entrega de cada conexión de la máquina. */
    public enum Resultado { AVISOS, SIN_SESION, VERIFICACION, RITMO, VACIA }

    @PostMapping("/recoger")
    public Map<String, Object> recoger() {
        recoleccion.recoger();
        return Map.of("ahora", reloj.instant().toString());
    }

    /** Dispara la recolección programada en otro hilo y responde sin esperarla, como el horario. */
    @PostMapping("/programada")
    public Map<String, Object> programada() {
        Thread.ofVirtual().name("programada-local").start(recoleccion::recoger);
        return Map.of("ahora", reloj.instant().toString());
    }

    /** Adelanta el reloj un día y dispara la programada sin esperarla: la recolección del día siguiente. */
    @PostMapping("/dia-siguiente")
    public Map<String, Object> diaSiguiente() {
        reloj.adelantar(Duration.ofDays(1));
        return programada();
    }

    /** Adelanta el reloj sin leer nada, como pasan los días cuando la persona no pide ninguna lectura. */
    @PostMapping("/adelantar")
    public Map<String, Object> adelantar(@RequestParam(defaultValue = "1") int dias) {
        reloj.adelantar(Duration.ofDays(dias));
        return Map.of("ahora", reloj.instant().toString());
    }

    /** Cuántas consultas recibió el doble de Apify desde que arrancó la API local. */
    @GetMapping("/apify")
    public Map<String, Object> apify() {
        return Map.of("consultas", apify.consultas(), "lanzamientos", Map.of(
                "LINKEDIN", apify.lanzamientos(ApifyDePrueba.LINKEDIN),
                "BUMERAN", apify.lanzamientos(ApifyDePrueba.BUMERAN),
                "COMPUTRABAJO", apify.lanzamientos(ApifyDePrueba.ACTOR)));
    }

    @PutMapping("/apify/{fuente}")
    public Map<String, Object> modoApify(@PathVariable Fuente fuente, @RequestParam ApifyDePrueba.Modo modo,
            @RequestParam(defaultValue = "*") String termino,
            @RequestParam(defaultValue = "Peru") String ambito) {
        String actor = switch (fuente) {
            case LINKEDIN -> ApifyDePrueba.LINKEDIN;
            case BUMERAN -> ApifyDePrueba.BUMERAN;
            case COMPUTRABAJO -> ApifyDePrueba.ACTOR;
            default -> throw new IllegalArgumentException("la fuente debe usar Apify");
        };
        apify.modo(actor, ambito, termino, modo);
        return Map.of("fuente", fuente, "modo", modo);
    }

    @PutMapping("/fuentes/{fuente}")
    public Map<String, Object> modo(@PathVariable Fuente fuente, @RequestParam FuentesDePrueba.Modo modo) {
        fuentes.modo(fuente, modo);
        return Map.of("fuente", fuente, "modo", modo);
    }

    /** Con activa=true la fuente recibe las peticiones y retiene la respuesta hasta que se la suelta. */
    @PutMapping("/fuentes/{fuente}/pausa")
    public Map<String, Object> pausa(@PathVariable Fuente fuente, @RequestParam boolean activa) {
        if (activa) {
            fuentes.pausar(fuente);
        } else {
            fuentes.soltar(fuente);
        }
        return Map.of("fuente", fuente, "pausa", fuentes.enPausa(fuente));
    }

    /**
     * Hace de lector: recorre la corrida abierta con ese código (o abre una, si no llega código) y entrega el mismo
     * resultado por cada conexión de la máquina. Una conexión que la API manda no leer se salta.
     */
    @PostMapping("/lector")
    public RecoleccionResponse lector(@RequestParam(required = false) String codigo,
            @RequestParam(defaultValue = "AVISOS") Resultado resultado, @RequestParam(defaultValue = "3") int avisos) {
        String corrida = codigo != null ? codigo : corridaLocal.abrir().codigo();
        CorridaLectorResponse aLeer = corridaLocal.iniciar(corrida);
        for (CorridaLectorResponse.FuenteALeer fuente : aLeer.fuentes()) {
            try {
                corridaLocal.empezar(corrida, fuente.fuente());
            } catch (RecoleccionRechazadaException e) {
                continue;
            }
            EntregaRequest entrega = resultado == Resultado.AVISOS
                    ? new EntregaRequest(corrida, EntregasDePrueba.avisos("local-" + reloj.millis(),
                            Math.min(avisos, fuente.avisosMaximos()), reloj.instant(),
                            avisoOriginal(fuente.fuente())), null)
                    : new EntregaRequest(corrida, null, FalloMaquina.valueOf(resultado.name()));
            corridaLocal.entregar(corrida, fuente.fuente(), entrega);
        }
        return corridaLocal.terminar(corrida);
    }

    /** Cada conexión de la máquina enlaza sus avisos a su propio sitio, como los entrega el lector real. */
    private static String avisoOriginal(Fuente fuente) {
        return fuente == Fuente.BUMERAN ? "https://www.bumeran.com.pe/empleos/" : "https://www.linkedin.com/jobs/view/";
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
