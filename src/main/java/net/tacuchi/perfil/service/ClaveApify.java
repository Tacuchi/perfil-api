package net.tacuchi.perfil.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.fuentes.LecturaFallidaException;
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.EstadoLectura;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.repository.ConexionRepository;
import org.springframework.stereotype.Component;

/** La fila y el dato asociado originales se conservan para poder leer la clave ya guardada. */
@Component
@RequiredArgsConstructor
public class ClaveApify {
    private static final Fuente ALMACENAMIENTO = Fuente.COMPUTRABAJO;
    private final ConexionRepository conexiones;
    private final CifradoClaves cifrado;
    private final Clock clock;

    public String leer() {
        byte[] guardada = conexiones.claveCifrada(ALMACENAMIENTO)
                .orElseThrow(() -> new LecturaFallidaException(EstadoLectura.FALLO, Conexion.FALTA_CLAVE));
        return cifrado.descifrar(ALMACENAMIENTO, guardada);
    }

    public void guardar(String clave) {
        conexiones.guardarClave(ALMACENAMIENTO, cifrado.cifrar(ALMACENAMIENTO, clave.strip()), clock.instant());
    }

    public Instant cargadaEn() {
        return conexiones.buscar(ALMACENAMIENTO).claveCargadaEn();
    }

    public Conexion proyectar(Conexion conexion) {
        return new Conexion(conexion.fuente(), conexion.pausada(), conexion.motivoPausa(),
                conexion.topeMensual(), cargadaEn());
    }
}
