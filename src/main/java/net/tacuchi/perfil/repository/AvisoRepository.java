package net.tacuchi.perfil.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Correos de aviso enviados, uno por día como máximo. */
@Repository
@RequiredArgsConstructor
public class AvisoRepository {

    private final JdbcClient jdbc;

    /** Reserva el correo del día. false si ese día ya tiene uno. */
    public boolean reservar(LocalDate dia, Instant cuando, int ofertas) {
        return jdbc.sql("""
                INSERT INTO aviso (dia, enviado_en, ofertas) VALUES (:dia, :cuando, :ofertas)
                ON CONFLICT (dia) DO NOTHING""")
                .param("dia", dia)
                .param("cuando", Timestamp.from(cuando))
                .param("ofertas", ofertas)
                .update() == 1;
    }
}
