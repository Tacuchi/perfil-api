package net.tacuchi.perfil.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.tacuchi.perfil.model.Clasificacion;
import net.tacuchi.perfil.model.Encaje;
import net.tacuchi.perfil.model.EstadoOferta;
import net.tacuchi.perfil.model.FiltroOfertas;
import net.tacuchi.perfil.model.Fuente;
import net.tacuchi.perfil.model.Modalidad;
import net.tacuchi.perfil.model.OfertaGuardada;
import net.tacuchi.perfil.model.OfertaRecogida;
import net.tacuchi.perfil.model.Repetida;
import net.tacuchi.perfil.model.Reglas;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Ofertas en PostgreSQL. Toda consulta va con parámetros. */
@Repository
@RequiredArgsConstructor
public class OfertaRepository {

    private static final String COLUMNAS = """
            id, fuente, titulo, empresa, publicada_en, ubicacion, modalidad, url, descripcion, correo_contacto,
            idioma, encaje, motivos, tecnologias, clave_similitud, estado, postulada_en, publicada,
            vista_primera, vista_ultima""";
    private static final String FECHA = "COALESCE(publicada_en, vista_primera)";

    private final JdbcClient jdbc;

    /**
     * Inserta la oferta o, si la fuente ya la había entregado, actualiza lo recogido y su clasificación.
     * El estado que puso la persona y su fecha de postulación no se tocan.
     */
    public void registrar(OfertaRecogida oferta, Clasificacion clasificacion, String claveSimilitud, Instant vista) {
        jdbc.sql("""
                INSERT INTO oferta (fuente, id_externo, titulo, empresa, publicada_en, ubicacion, modalidad, url,
                    descripcion, correo_contacto, idioma, encaje, motivos, tecnologias, clave_similitud,
                    vista_primera, vista_ultima)
                VALUES (:fuente, :idExterno, :titulo, :empresa, :publicadaEn, :ubicacion, :modalidad, :url,
                    :descripcion, :correo, :idioma, :encaje, :motivos, :tecnologias, :clave, :vista, :vista)
                ON CONFLICT (fuente, id_externo) DO UPDATE SET
                    titulo = EXCLUDED.titulo, empresa = EXCLUDED.empresa, publicada_en = EXCLUDED.publicada_en,
                    ubicacion = EXCLUDED.ubicacion, modalidad = EXCLUDED.modalidad, url = EXCLUDED.url,
                    descripcion = EXCLUDED.descripcion, correo_contacto = EXCLUDED.correo_contacto,
                    idioma = EXCLUDED.idioma, encaje = EXCLUDED.encaje, motivos = EXCLUDED.motivos,
                    tecnologias = EXCLUDED.tecnologias, clave_similitud = EXCLUDED.clave_similitud,
                    publicada = TRUE, vista_ultima = EXCLUDED.vista_ultima""")
                .param("fuente", oferta.fuente().name())
                .param("idExterno", oferta.idExterno())
                .param("titulo", oferta.titulo())
                .param("empresa", oferta.empresa())
                .param("publicadaEn", marca(oferta.publicadaEn()))
                .param("ubicacion", oferta.ubicacion())
                .param("modalidad", oferta.modalidad().name())
                .param("url", oferta.url())
                .param("descripcion", oferta.descripcion())
                .param("correo", oferta.correoContacto())
                .param("idioma", oferta.idioma())
                .param("encaje", clasificacion.encaje().name())
                .param("motivos", clasificacion.motivos().toArray(String[]::new))
                .param("tecnologias", clasificacion.tecnologias().toArray(String[]::new))
                .param("clave", claveSimilitud)
                .param("vista", marca(vista))
                .update();
    }

    /** Tras una lectura completa: lo que la fuente ya no entregó desde el inicio de esa lectura deja de estar publicado. */
    public int marcarNoPublicadas(Fuente fuente, Instant inicioLectura) {
        return jdbc.sql("UPDATE oferta SET publicada = FALSE WHERE fuente = :fuente AND vista_ultima < :inicio")
                .param("fuente", fuente.name())
                .param("inicio", marca(inicioLectura))
                .update();
    }

    /** Elimina las nuevas y descartadas que nadie vio desde el límite. Guardadas y postuladas se conservan. */
    public int eliminarVistasAntesDe(Instant limite) {
        return jdbc.sql("DELETE FROM oferta WHERE estado IN ('NUEVA', 'DESCARTADA') AND vista_ultima < :limite")
                .param("limite", marca(limite))
                .update();
    }

    public List<OfertaGuardada> listar(FiltroOfertas filtro) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNAS + " FROM oferta WHERE TRUE");
        Map<String, Object> parametros = new HashMap<>();
        enLista(sql, parametros, "encaje", filtro.encajes());
        enLista(sql, parametros, "estado", filtro.estados());
        enLista(sql, parametros, "fuente", filtro.fuentes());
        enLista(sql, parametros, "modalidad", filtro.modalidades());
        if (filtro.tecnologia() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM unnest(tecnologias) t WHERE lower(t) = lower(:tecnologia))");
            parametros.put("tecnologia", filtro.tecnologia());
        }
        if (filtro.desde() != null) {
            sql.append(" AND " + FECHA + " >= :desde");
            parametros.put("desde", marca(filtro.desde()));
        }
        if (filtro.hastaExclusive() != null) {
            sql.append(" AND " + FECHA + " < :hasta");
            parametros.put("hasta", marca(filtro.hastaExclusive()));
        }
        sql.append(" ORDER BY " + FECHA + " DESC, id DESC LIMIT " + Reglas.TOPE_LISTA_OFERTAS);
        return jdbc.sql(sql.toString()).params(parametros).query(OfertaRepository::fila).list();
    }

    public Optional<OfertaGuardada> buscar(long id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM oferta WHERE id = :id")
                .param("id", id)
                .query(OfertaRepository::fila)
                .optional();
    }

    /** Ofertas con la misma clave de similitud que alguna de las dadas. */
    public List<Repetida> conClaves(Collection<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, fuente, clave_similitud FROM oferta WHERE clave_similitud IN (:claves)")
                .param("claves", List.copyOf(claves))
                .query((rs, n) -> new Repetida(rs.getLong("id"), Fuente.valueOf(rs.getString("fuente")),
                        rs.getString("clave_similitud")))
                .list();
    }

    public boolean cambiarEstado(long id, EstadoOferta estado, Instant postuladaEn) {
        // Volver a marcar como postulada conserva la fecha de la primera vez.
        return jdbc.sql("""
                UPDATE oferta SET estado = :estado,
                    postulada_en = CASE WHEN :estado = 'POSTULADA' THEN COALESCE(postulada_en, :postuladaEn) END
                WHERE id = :id""")
                .param("estado", estado.name())
                .param("postuladaEn", marca(postuladaEn))
                .param("id", id)
                .update() == 1;
    }

    /** Nuevas de encaje alto, aún publicadas, que no salieron en ningún correo. */
    public List<OfertaGuardada> pendientesDeAviso() {
        return jdbc.sql("SELECT " + COLUMNAS + " " + """
                FROM oferta WHERE estado = 'NUEVA' AND encaje = 'ALTO' AND publicada AND avisada_en IS NULL
                ORDER BY id""")
                .query(OfertaRepository::fila)
                .list();
    }

    public void marcarAvisadas(Collection<Long> ids, Instant cuando) {
        jdbc.sql("UPDATE oferta SET avisada_en = :cuando WHERE id IN (:ids)")
                .param("cuando", marca(cuando))
                .param("ids", List.copyOf(ids))
                .update();
    }

    private static void enLista(StringBuilder sql, Map<String, Object> parametros, String columna,
            Set<? extends Enum<?>> valores) {
        if (valores == null) {
            return;
        }
        sql.append(" AND ").append(columna).append(" IN (:").append(columna).append(')');
        parametros.put(columna, valores.stream().map(Enum::name).toList());
    }

    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp marca = rs.getTimestamp(columna);
        return marca == null ? null : marca.toInstant();
    }

    private static List<String> textos(ResultSet rs, String columna) throws SQLException {
        Array arreglo = rs.getArray(columna);
        return arreglo == null ? List.of() : List.of((String[]) arreglo.getArray());
    }

    private static OfertaGuardada fila(ResultSet rs, int numero) throws SQLException {
        return new OfertaGuardada(
                rs.getLong("id"),
                Fuente.valueOf(rs.getString("fuente")),
                rs.getString("titulo"),
                rs.getString("empresa"),
                instante(rs, "publicada_en"),
                rs.getString("ubicacion"),
                Modalidad.valueOf(rs.getString("modalidad")),
                rs.getString("url"),
                rs.getString("descripcion"),
                rs.getString("correo_contacto"),
                rs.getString("idioma"),
                Encaje.valueOf(rs.getString("encaje")),
                textos(rs, "motivos"),
                textos(rs, "tecnologias"),
                rs.getString("clave_similitud"),
                EstadoOferta.valueOf(rs.getString("estado")),
                instante(rs, "postulada_en"),
                rs.getBoolean("publicada"),
                instante(rs, "vista_primera"),
                instante(rs, "vista_ultima"));
    }
}
