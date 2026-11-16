package net.tacuchi.perfil.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Conexion;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.MotivoOmision;
import net.tacuchi.perfil.repository.ConexionRepository;
import net.tacuchi.perfil.repository.ConsumoRepository;
import org.springframework.stereotype.Component;

/** Si una conexión de pago se puede leer ahora: tiene llave, clave y tope, y no consumió su tope del mes. */
@Component
@RequiredArgsConstructor
public class ConexionesPago {

    private final ConexionRepository conexionRepository;
    private final ConsumoRepository consumoRepository;
    private final CifradoClaves cifrado;
    private final Clock clock;
    private final ClaveApify clave;

    /** Por qué no se lee ahora; vacío si se puede leer. Siempre vacío para las que no son de pago. */
    public Optional<MotivoOmision> impedimento(Fuente fuente) {
        if (!fuente.dePago()) {
            return Optional.empty();
        }
        Conexion conexion = clave.proyectar(conexionRepository.buscar(fuente));
        if (conexion.queFalta(cifrado.disponible()).isPresent()) {
            return Optional.of(MotivoOmision.FALTA_CONFIGURAR);
        }
        return conexion.topeAlcanzado(consumoDelMes(fuente))
                ? Optional.of(MotivoOmision.TOPE_MENSUAL) : Optional.empty();
    }

    /** Lo consumido en el mes de Lima en curso. */
    public BigDecimal consumoDelMes(Fuente fuente) {
        return consumoRepository.delMes(fuente, ConsumoRepository.mesDe(clock.instant()));
    }

    public boolean llaveDisponible() {
        return cifrado.disponible();
    }
}
