package kamayuk.identidad.nucleo.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Consumidor;
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

    /** Las mismas, calificadas: {@link #pendientesPara} las lee con un {@code JOIN} delante. */
    private static final String COLUMNAS_DEL_EVENTO =
            "e.id, e.evento_id, e.tipo, e.sujeto_id, e.cuerpo, e.huella, e.creado_en";

    /**
     * «Lo que este consumidor no ha acusado», escrito UNA vez.
     *
     * <p>Lo usan la pagina y la cuenta, y tienen que decir exactamente lo mismo: si divergieran,
     * {@code quedan} contaria una cola distinta de la que se sirve y el consumidor daria vueltas
     * sobre un retraso que no baja.
     */
    private static final String SIN_ACUSE_DE =
            " LEFT JOIN identidad_evento_acuse a"
                    + " ON a.municipalidad_id = e.municipalidad_id"
                    + " AND a.evento_id = e.evento_id"
                    + " AND a.consumidor = :consumidor"
                    + " WHERE a.evento_id IS NULL";

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

    /**
     * Lo que a ese consumidor le falta: {@code LEFT JOIN} contra su acuse y {@code IS NULL}.
     *
     * <p>La consulta no nombra la municipalidad y las dos tablas la llevan en su politica, asi que
     * el {@code JOIN} <b>si</b> la nombra: sin esa igualdad, la forma de la consulta admitiria
     * casar un acuse de una municipalidad con un evento de otra y lo unico que lo impediria seria
     * RLS. Una consulta cuya correccion depende de que la politica este puesta es la que se rompe
     * el dia que alguien la ejecute como dueno.
     *
     * <p>Se hacen <b>dos</b> consultas —la pagina y la cuenta— y no una con {@code count(*) OVER
     * ()}: con la ventana, el {@code quedan} de una pagina llena seria el total y el de la ultima
     * seria el de esa pagina, o sea la misma columna significando dos cosas. Y el {@code LIMIT}
     * deja de poder recortar la lectura, que es para lo que esta.
     */
    @Override
    public Lote pendientesPara(Consumidor consumidor, int limite) {
        List<EventoDeIdentidad> eventos =
                jdbc().sql(
                                "SELECT "
                                        + COLUMNAS_DEL_EVENTO
                                        + " FROM identidad_evento e"
                                        + SIN_ACUSE_DE
                                        + " ORDER BY e.id LIMIT :limite")
                        .param("consumidor", consumidor.sistema())
                        .param("limite", limite)
                        .query(BuzonDeIdentidadJdbc::mapear)
                        .list();
        Long quedan =
                jdbc().sql("SELECT count(*) FROM identidad_evento e" + SIN_ACUSE_DE)
                        .param("consumidor", consumidor.sistema())
                        .query(Long.class)
                        .single();
        return new Lote(eventos, quedan == null ? 0L : quedan);
    }

    /**
     * El acuse: se comprueba, se escribe y se dice cuantas filas quedaron.
     *
     * <p><b>La comprobacion va antes y no se sustituye por la foranea</b>, aunque la foranea
     * rechace lo mismo: lo que el motor devuelve es un {@code 23503} con el nombre de la
     * restriccion, y quien manda doscientos identificadores no puede saber cual de ellos estaba
     * mal. Aqui se nombran los que faltan.
     *
     * <p>Y se lee de {@code identidad_evento} con el contexto puesto, asi que «de otra
     * municipalidad» y «no existe» son la misma respuesta — que es lo correcto: desde esta peticion
     * ese evento no existe.
     *
     * <p>{@code ON CONFLICT DO NOTHING} y no un {@code SELECT} previo: dos vueltas simultaneas del
     * mismo consumidor leerian las dos «no esta» y las dos insertarian. La idempotencia la da el
     * motor, por la clave primaria, que es donde no se puede perder una carrera.
     */
    @Override
    public int acusar(Consumidor consumidor, List<UUID> eventoIds) {
        if (eventoIds.isEmpty()) {
            return 0;
        }
        Set<UUID> pedidos = new LinkedHashSet<>(eventoIds);
        Set<UUID> queEstan =
                new LinkedHashSet<>(
                        jdbc().sql(
                                        "SELECT evento_id FROM identidad_evento"
                                                + " WHERE evento_id IN (:eventos)")
                                .param("eventos", pedidos)
                                .query(UUID.class)
                                .list());
        List<UUID> queNoEstan = new ArrayList<>();
        for (UUID pedido : pedidos) {
            if (!queEstan.contains(pedido)) {
                queNoEstan.add(pedido);
            }
        }
        if (!queNoEstan.isEmpty()) {
            throw new EventoQueNoConsta(
                    "Este buzon no sirvio "
                            + queNoEstan.size()
                            + " de los "
                            + pedidos.size()
                            + " eventos acusados, y acusar lo que no se sirvio no es un descuido"
                            + " tolerable: lo acusado no se vuelve a servir. Son: "
                            + queNoEstan
                            + ". Un evento de otra municipalidad, desde esta peticion, no existe.");
        }
        Instant acusadoEn = Instant.now(reloj);
        int escritas = 0;
        for (UUID evento : pedidos) {
            escritas +=
                    jdbc().sql(
                                    "INSERT INTO identidad_evento_acuse (municipalidad_id,"
                                            + " consumidor, evento_id, acusado_en) VALUES ("
                                            + MUNICIPALIDAD_ACTUAL
                                            + ", :consumidor, :evento, :acusadoEn)"
                                            + " ON CONFLICT DO NOTHING")
                            .param("consumidor", consumidor.sistema())
                            .param("evento", evento)
                            .param("acusadoEn", java.sql.Timestamp.from(acusadoEn))
                            .update();
        }
        return escritas;
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
