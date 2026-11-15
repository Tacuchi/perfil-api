package net.tacuchi.perfil.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.tacuchi.perfil.config.PerfilProperties;
import net.tacuchi.perfil.model.OfertaGuardada;
import net.tacuchi.perfil.model.Reglas;
import net.tacuchi.perfil.repository.AvisoRepository;
import net.tacuchi.perfil.repository.OfertaRepository;
import net.tacuchi.perfil.service.AvisoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El único destinatario es la persona (perfil.access.correo): el sistema nunca escribe a terceros.
 * El día se reserva antes de enviar y en la misma transacción: si el envío falla, la reserva se deshace.
 */
@Slf4j
@Service
public class AvisoServiceImpl implements AvisoService {

    static final String ASUNTO = "%d ofertas nuevas de encaje alto";
    static final String ASUNTO_UNA = "1 oferta nueva de encaje alto";

    private final OfertaRepository ofertaRepository;
    private final AvisoRepository avisoRepository;
    private final JavaMailSender correo;
    private final PerfilProperties propiedades;
    private final Clock clock;

    public AvisoServiceImpl(OfertaRepository ofertaRepository, AvisoRepository avisoRepository,
            ObjectProvider<JavaMailSender> correo, PerfilProperties propiedades, Clock clock) {
        this.ofertaRepository = ofertaRepository;
        this.avisoRepository = avisoRepository;
        this.propiedades = propiedades;
        this.clock = clock;
        PerfilProperties.Aviso aviso = propiedades.aviso();
        if (!aviso.activo()) {
            this.correo = null;
            return;
        }
        if (aviso.remitente() == null || aviso.remitente().isBlank()) {
            throw new IllegalStateException(
                    "perfil.aviso.remitente (PERFIL_AVISO_REMITENTE) es obligatorio con perfil.aviso.activo=true");
        }
        this.correo = correo.getIfAvailable();
        if (this.correo == null) {
            throw new IllegalStateException(
                    "spring.mail.host (SPRING_MAIL_HOST) es obligatorio con perfil.aviso.activo=true");
        }
    }

    @Override
    @Transactional
    public void avisarSiCorresponde() {
        if (correo == null) {
            return;
        }
        List<OfertaGuardada> nuevas = ofertaRepository.pendientesDeAviso();
        if (nuevas.isEmpty()) {
            return;
        }
        Instant ahora = clock.instant();
        if (!avisoRepository.reservar(LocalDate.ofInstant(ahora, Reglas.ZONA_PERSONA), ahora, nuevas.size())) {
            return;
        }
        correo.send(mensaje(nuevas));
        ofertaRepository.marcarAvisadas(nuevas.stream().map(OfertaGuardada::id).toList(), ahora);
        log.info("[AVISO] correo del día enviado con {} ofertas", nuevas.size());
    }

    private SimpleMailMessage mensaje(List<OfertaGuardada> nuevas) {
        String panel = propiedades.panel().origen() + "/ofertas";
        StringBuilder texto = new StringBuilder("Ofertas nuevas de encaje alto:\n\n");
        for (OfertaGuardada oferta : nuevas) {
            texto.append("- ").append(oferta.titulo());
            if (oferta.empresa() != null && !oferta.empresa().isBlank()) {
                texto.append(" — ").append(oferta.empresa());
            }
            texto.append("\n  ").append(panel).append('/').append(oferta.id()).append('\n');
        }
        texto.append("\nTodas en el panel: ").append(panel).append('\n');
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(propiedades.aviso().remitente());
        mensaje.setTo(propiedades.access().correo());
        mensaje.setSubject(nuevas.size() == 1 ? ASUNTO_UNA : ASUNTO.formatted(nuevas.size()));
        mensaje.setText(texto.toString());
        return mensaje;
    }
}
