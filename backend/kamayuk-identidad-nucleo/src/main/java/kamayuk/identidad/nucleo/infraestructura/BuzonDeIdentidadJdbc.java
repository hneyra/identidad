package kamayuk.identidad.nucleo.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.HechoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.TipoDeEventoDeIdentidad;
import kamayuk.identidad.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escribe y lee {@code identidad_evento}.
 *
 * <p><b>No abre ninguna transaccion, y eso es el diseno entero.</b> {@link #emitir} se llama desde
 * dentro del metodo {@code @Transactional} del caso de uso, asi que participa en la misma: la fila
 * del evento y la fila que lo produjo se confirman o se deshacen juntas. Un {@code @Transactional}
 * aqui —aunque fuera {@code REQUIRED}— seria inofensivo hoy y una invitacion a que manana alguien
 * le ponga {@code REQUIRES_NEW} «para que el evento no se pierda», que es exactamente lo que lo
 * romperia: el evento sobreviviria a la escritura que se deshizo.
 *
 * <p>La consulta no nombra la municipalidad: la pone la politica RLS con el contexto de la
 * transaccion (regla 2). Un evento de otra municipalidad, desde aqui, no existe.
 */
@Repository
public class BuzonDeIdentidadJdbc extends RepositorioJdbc implements BuzonDeIdentidad {

    private static final String COLUMNAS =
            "id, evento_id, tipo, sujeto_id, cuerpo, huella, creado_en";

    private final Clock reloj;

    public BuzonDeIdentidadJdbc(JdbcClient jdbc, Clock reloj) {
        super(jdbc);
        this.reloj = reloj;
    }

    /**
     * {@code creado_en} lo pone el {@link Clock} de la aplicacion y no {@code now()} de la base.
     *
     * <p>No es una preferencia: con {@code now()} el instante depende del reloj del motor, que es
     * otro proceso y —en el compose y en el cluster— otra maquina; y sobre todo no se puede fijar
     * en una prueba, de modo que cualquier archivo de referencia que llevara la fecha dentro se
     * reescribiria en cada corrida y taparia el cambio que ese archivo existe para ensenar. Es lo
     * que C-12 midio en el buzon de {@code catastro}.
     */
    @Override
    public EventoDeIdentidad emitir(HechoDeIdentidad hecho) {
        Instant creadoEn = Instant.now(reloj);
        Long secuencia =
                jdbc().sql(
                                "INSERT INTO identidad_evento (municipalidad_id, evento_id, tipo,"
                                        + " sujeto_id, cuerpo, huella, creado_en) VALUES ("
                                        + MUNICIPALIDAD_ACTUAL
                                        + ", :evento, :tipo, :sujeto, CAST(:cuerpo AS jsonb),"
                                        + " :huella, :creadoEn) RETURNING id")
                        .param("evento", hecho.eventoId())
                        .param("tipo", hecho.tipo().name())
                        .param("sujeto", hecho.sujetoId())
                        .param("cuerpo", hecho.cuerpo())
                        .param("huella", hecho.huella())
                        .param("creadoEn", java.sql.Timestamp.from(creadoEn))
                        .query(Long.class)
                        .single();
        return new EventoDeIdentidad(
                secuencia,
                hecho.eventoId(),
                hecho.tipo(),
                hecho.sujetoId(),
                hecho.cuerpo(),
                hecho.huella(),
                creadoEn);
    }

    @Override
    public List<EventoDeIdentidad> pendientesDesde(long secuencia, int limite) {
        return jdbc().sql(
                        "SELECT "
                                + COLUMNAS
                                + " FROM identidad_evento WHERE id > :desde"
                                + " ORDER BY id LIMIT :limite")
                .param("desde", secuencia)
                .param("limite", limite)
                .query(BuzonDeIdentidadJdbc::mapear)
                .list();
    }

    private static EventoDeIdentidad mapear(ResultSet fila, int numero) throws SQLException {
        return new EventoDeIdentidad(
                fila.getLong("id"),
                fila.getObject("evento_id", java.util.UUID.class),
                TipoDeEventoDeIdentidad.valueOf(fila.getString("tipo")),
                fila.getLong("sujeto_id"),
                fila.getString("cuerpo"),
                fila.getString("huella"),
                fila.getTimestamp("creado_en").toInstant());
    }
}
