package net.tacuchi.perfil;

import static net.tacuchi.perfil.soporte.Ofertas.oferta;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Arrays;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.soporte.AccessDePrueba;
import net.tacuchi.perfil.soporte.FuentesDePrueba;
import net.tacuchi.perfil.soporte.PruebaOfertas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** Correo diario contra un servidor de correo de prueba: uno al día como máximo y solo a la persona. */
@TestPropertySource(properties = {"prueba.aviso-activo=true", "perfil.aviso.remitente=avisos@ejemplo.test"})
class AvisoTest extends PruebaOfertas {

    private static final GreenMail CORREO = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        CORREO.start();
    }

    @DynamicPropertySource
    static void servidorDeCorreo(DynamicPropertyRegistry registro) {
        registro.add("spring.mail.host", () -> "127.0.0.1");
        registro.add("spring.mail.port", () -> CORREO.getSmtp().getPort());
    }

    @BeforeEach
    void sinFuentesNiCorreos() throws Exception {
        CORREO.purgeEmailFromAllMailboxes();
        // Las fuentes no entregan nada nuevo: cada prueba decide qué ofertas hay.
        for (Fuente fuente : Fuente.values()) {
            FUENTES.modo(fuente, FuentesDePrueba.Modo.BLOQUEA);
        }
    }

    @AfterEach
    void soloLaPersonaRecibioCorreos() throws MessagingException {
        for (MimeMessage mensaje : CORREO.getReceivedMessages()) {
            assertThat(direcciones(mensaje.getAllRecipients())).containsExactly(AccessDePrueba.CORREO);
            assertThat(mensaje.getRecipients(Message.RecipientType.CC)).isNull();
            assertThat(mensaje.getRecipients(Message.RecipientType.BCC)).isNull();
        }
    }

    @Test
    void unDiaConOfertasNuevasDeEncajeAltoLlegaUnCorreoConTituloEmpresaYEnlace() throws Exception {
        leidas(Fuente.GET_ON_BOARD,
                oferta().id("alta").titulo("Desarrollador Angular Senior").empresa("Acme").build(),
                oferta().id("media").titulo("Desarrollador Java Senior").empresa("Beta").media().build(),
                oferta().id("descartada").titulo("Desarrollador Flutter Senior").empresa("Gamma").build());
        marcar(todas().stream().filter(o -> o.url().endsWith("/descartada")).findFirst().orElseThrow().id(),
                "DESCARTADA");

        recoleccion.recoger();

        MimeMessage[] recibidos = CORREO.getReceivedMessages();
        assertThat(recibidos).hasSize(1);
        assertThat(recibidos[0].getSubject()).isEqualTo("1 oferta nueva de encaje alto");
        assertThat(recibidos[0].getFrom()[0].toString()).isEqualTo("avisos@ejemplo.test");
        String cuerpo = GreenMailUtil.getBody(recibidos[0]);
        assertThat(cuerpo).contains("Desarrollador Angular Senior", "Acme", ORIGEN_PANEL + "/ofertas")
                .doesNotContain("Java Senior", "Flutter Senior");
    }

    @Test
    void unDiaSinOfertasNuevasDeEncajeAltoNoLlegaCorreo() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("media").media().build(), oferta().id("fuera").sinTecnologias().build());

        recoleccion.recoger();

        assertThat(CORREO.getReceivedMessages()).isEmpty();
    }

    @Test
    void dosRecoleccionesElMismoDiaDanUnSoloCorreoYAlDiaSiguienteOtro() {
        leidas(Fuente.GET_ON_BOARD, oferta().id("primera").build());
        recoleccion.recoger();
        leidas(Fuente.GET_ON_BOARD, oferta().id("primera").build(), oferta().id("segunda").build());
        recoleccion.recoger();
        assertThat(CORREO.getReceivedMessages()).hasSize(1);

        reloj.adelantar(Duration.ofDays(1));
        recoleccion.recoger();

        assertThat(CORREO.getReceivedMessages()).hasSize(2);
        assertThat(GreenMailUtil.getBody(CORREO.getReceivedMessages()[1])).contains("/ofertas/")
                .doesNotContain("ofertas/" + idDe("primera") + "\r\n");
    }

    @Test
    void unaOfertaDescartadaNoSaleEnElCorreoDelDiaSiguiente() {
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("d").build());
        marcar(idDe("d"), "DESCARTADA");
        reloj.adelantar(Duration.ofDays(1));
        leidas(Fuente.REMOTIVE, oferta().fuente(Fuente.REMOTIVE).id("d").build());

        recoleccion.recoger();

        assertThat(CORREO.getReceivedMessages()).isEmpty();
    }

    private long idDe(String idExterno) {
        return todas().stream().filter(o -> o.url().endsWith("/" + idExterno)).findFirst().orElseThrow().id();
    }

    private static String[] direcciones(Address[] destinatarios) {
        return Arrays.stream(destinatarios).map(Address::toString).toArray(String[]::new);
    }
}
