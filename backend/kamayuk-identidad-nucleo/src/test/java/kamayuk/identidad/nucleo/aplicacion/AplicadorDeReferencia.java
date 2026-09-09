package kamayuk.identidad.nucleo.aplicacion;

import java.time.LocalDate;
import java.util.List;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que hara el consumidor de la etapa 4, escrito aqui para poder medir la etapa 2.
 *
 * <h2>Que demuestra, y por que no se puede demostrar de otra forma</h2>
 *
 * <p>El AC-3 no pregunta si los siete tipos «parecen suficientes»: pregunta si un consumidor que
 * <b>solo</b> vea estos eventos puede dejar sus cuatro tablas exactamente como quedaron las de
 * aqui. Eso no se razona leyendo el enumerado — se ejecuta. Este aplicador es la mitad que faltaba
 * para poder ejecutarlo: recibe los eventos en orden y escribe, sin mirar la base del emisor ni una
 * vez.
 *
 * <p>Es una clase de <b>prueba</b> y no de produccion a proposito. El consumidor de verdad vive en
 * los otros cuatro repositorios (etapa 4) y tendra ademas lo que aqui no hace falta: acuse,
 * reintentos y su propia idempotencia. Lo que este comparte con el es lo unico que la etapa 2 tiene
 * que sostener: <b>que con estos siete cuerpos se puede</b>.
 *
 * <h2>Empareja por CLAVE NATURAL, y eso es una afirmacion sobre los eventos</h2>
 *
 * <p>Ningun {@code INSERT} de aqui usa un identificador del emisor como clave: los usuarios se
 * emparejan por {@code cuenta}, los grupos por {@code nombre}, las pertenencias por el par de los
 * dos y los permisos por {@code (sujeto, sistema, codigo)}. Es deliberado y es lo que hace que la
 * prueba mida algo: cada base tiene sus propias secuencias (ADR-0032), asi que el grupo que aqui es
 * el 3 alli puede ser el 8. Un aplicador que copiara los identificadores funcionaria en la prueba
 * —las dos bases nacen a la vez— y no en produccion.
 *
 * <p>Y por eso los cuerpos de {@code MIEMBRO_*} y {@code PERMISO_FIJADO} llevan la clave natural
 * ademas del identificador: sin ella este aplicador tendria que mantener un mapa de
 * correspondencias poblado de los eventos de alta, o sea depender de no haber perdido ninguno —que
 * es exactamente lo que publicar la fila entera existe para no exigir—.
 */
public final class AplicadorDeReferencia {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public AplicadorDeReferencia(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Aplica los eventos en el orden en que se emitieron. */
    public void aplicar(List<EventoDeIdentidad> eventos) {
        for (EventoDeIdentidad evento : eventos) {
            JsonNode cuerpo = JSON.readTree(evento.cuerpo());
            switch (evento.tipo()) {
                case USUARIO_DADO_DE_ALTA, USUARIO_MODIFICADO -> usuario(cuerpo);
                case GRUPO_DADO_DE_ALTA, GRUPO_MODIFICADO -> grupo(cuerpo);
                case MIEMBRO_AFILIADO, MIEMBRO_DESAFILIADO -> miembro(cuerpo);
                case PERMISO_FIJADO -> permiso(cuerpo);
                // Los siete estan cubiertos, asi que esta rama no se alcanza hoy. Se escribe
                // igual: el dia que el emisor publique un octavo tipo, un consumidor que lo
                // ignorara en silencio dejaria su copia desatrasada sin que nada lo dijera — que
                // es exactamente el defecto que `rentas`#54 midio con la ingestion de `catastro`.
                default ->
                        throw new IllegalStateException(
                                "El buzon publico el tipo «"
                                        + evento.tipo()
                                        + "», que este consumidor no sabe aplicar");
            }
        }
    }

    private void usuario(JsonNode cuerpo) {
        jdbc.sql(
                        "INSERT INTO usuario (municipalidad_id, cuenta, nombre, correo, habilitado,"
                                + " vigencia_desde, vigencia_hasta) VALUES ("
                                + " current_setting('app.municipalidad_id')::bigint,"
                                + " :cuenta, :nombre, :correo, :habilitado, :desde, :hasta)"
                                + " ON CONFLICT (municipalidad_id, cuenta) DO UPDATE SET"
                                + "   nombre = EXCLUDED.nombre, correo = EXCLUDED.correo,"
                                + "   habilitado = EXCLUDED.habilitado,"
                                + "   vigencia_desde = EXCLUDED.vigencia_desde,"
                                + "   vigencia_hasta = EXCLUDED.vigencia_hasta")
                .param("cuenta", texto(cuerpo, "cuenta"))
                .param("nombre", texto(cuerpo, "nombre"))
                .param("correo", texto(cuerpo, "correo"))
                .param("habilitado", cuerpo.path("habilitado").asBoolean())
                .param("desde", fecha(cuerpo, "vigenciaDesde"))
                .param("hasta", fecha(cuerpo, "vigenciaHasta"))
                .update();
    }

    private void grupo(JsonNode cuerpo) {
        jdbc.sql(
                        "INSERT INTO grupo (municipalidad_id, nombre, descripcion, habilitado,"
                                + " vigencia_desde, vigencia_hasta) VALUES ("
                                + " current_setting('app.municipalidad_id')::bigint,"
                                + " :nombre, :descripcion, :habilitado, :desde, :hasta)"
                                + " ON CONFLICT (municipalidad_id, nombre) DO UPDATE SET"
                                + "   descripcion = EXCLUDED.descripcion,"
                                + "   habilitado = EXCLUDED.habilitado,"
                                + "   vigencia_desde = EXCLUDED.vigencia_desde,"
                                + "   vigencia_hasta = EXCLUDED.vigencia_hasta")
                .param("nombre", texto(cuerpo, "nombre"))
                .param("descripcion", texto(cuerpo, "descripcion"))
                .param("habilitado", cuerpo.path("habilitado").asBoolean())
                .param("desde", fecha(cuerpo, "vigenciaDesde"))
                .param("hasta", fecha(cuerpo, "vigenciaHasta"))
                .update();
    }

    private void miembro(JsonNode cuerpo) {
        boolean activo = cuerpo.path("activo").asBoolean();
        int escritas =
                jdbc.sql(
                                "INSERT INTO miembro (municipalidad_id, grupo_id, usuario_id, usuario_alta,"
                                        + " activo, fecha_baja, usuario_baja)"
                                        + " SELECT current_setting('app.municipalidad_id')::bigint,"
                                        + "        g.id, u.id, :alta, :activo,"
                                        + "        CASE WHEN :activo THEN NULL ELSE now() END, :baja"
                                        + "   FROM grupo g, usuario u"
                                        + "  WHERE g.nombre = :grupo AND u.cuenta = :cuenta"
                                        + " ON CONFLICT (municipalidad_id, grupo_id, usuario_id)"
                                        + " DO UPDATE SET activo = EXCLUDED.activo,"
                                        + "               fecha_baja = EXCLUDED.fecha_baja,"
                                        + "               usuario_baja = EXCLUDED.usuario_baja")
                        .param("grupo", texto(cuerpo, "grupoNombre"))
                        .param("cuenta", texto(cuerpo, "usuarioCuenta"))
                        .param("alta", activo ? texto(cuerpo, "usuarioAlta") : "—")
                        .param("activo", activo)
                        .param("baja", texto(cuerpo, "usuarioBaja"))
                        .update();
        exigirQueEscribiera(
                escritas,
                "miembro",
                "el grupo «"
                        + texto(cuerpo, "grupoNombre")
                        + "» y la cuenta «"
                        + texto(cuerpo, "usuarioCuenta")
                        + "»");
    }

    /**
     * El permiso, con las siete columnas SIEMPRE.
     *
     * <p>Lo no otorgado se escribe en falso y no se deja como estaba, por lo mismo que en el
     * emisor: un {@code UPDATE} que solo tocara los privilegios presentes dejaria activos los que
     * alguien acaba de quitar, y ese es el defecto que no se nota hasta que alguien entra donde no
     * debia.
     */
    private void permiso(JsonNode cuerpo) {
        boolean deGrupo = "GRUPO".equals(texto(cuerpo, "sujeto"));
        JsonNode privilegios = cuerpo.path("privilegios");
        StringBuilder columnas = new StringBuilder();
        StringBuilder valores = new StringBuilder();
        StringBuilder actualizacion = new StringBuilder();
        for (Privilegio privilegio : Privilegio.values()) {
            columnas.append(", ").append(privilegio.columna());
            valores.append(", :").append(privilegio.columna());
            actualizacion
                    .append(", ")
                    .append(privilegio.columna())
                    .append(" = EXCLUDED.")
                    .append(privilegio.columna());
        }
        String sujeto = deGrupo ? "grupo_id" : "usuario_id";
        String tabla = deGrupo ? "grupo g" : "usuario g";
        String clave = deGrupo ? "g.nombre" : "g.cuenta";

        var sentencia =
                jdbc.sql(
                                "INSERT INTO permiso (municipalidad_id, acceso_id, "
                                        + sujeto
                                        + ", usuario_registro"
                                        + columnas
                                        + ") SELECT"
                                        + " current_setting('app.municipalidad_id')::bigint,"
                                        + " a.id, g.id, :quien"
                                        + valores
                                        + "   FROM acceso a, "
                                        + tabla
                                        + "  WHERE a.sistema = :sistema AND a.codigo = :codigo"
                                        + "    AND "
                                        + clave
                                        + " = :sujeto"
                                        + " ON CONFLICT (municipalidad_id, acceso_id, "
                                        + sujeto
                                        + ") WHERE "
                                        + sujeto
                                        + " IS NOT NULL DO UPDATE SET usuario_registro ="
                                        + " EXCLUDED.usuario_registro"
                                        + actualizacion)
                        .param("sistema", texto(cuerpo, "sistema"))
                        .param("codigo", texto(cuerpo, "codigo"))
                        .param("sujeto", texto(cuerpo, "sujetoNombre"))
                        .param("quien", texto(cuerpo, "usuarioRegistro"));
        for (Privilegio privilegio : Privilegio.values()) {
            sentencia =
                    sentencia.param(
                            privilegio.columna(),
                            privilegios.path(privilegio.columna()).asBoolean());
        }
        exigirQueEscribiera(
                sentencia.update(),
                "permiso",
                "la opcion («"
                        + texto(cuerpo, "sistema")
                        + "», «"
                        + texto(cuerpo, "codigo")
                        + "») y el sujeto «"
                        + texto(cuerpo, "sujetoNombre")
                        + "»");
    }

    /**
     * Un evento que se aplica y <b>no escribe ninguna fila</b> es un evento perdido, y hay que
     * decirlo.
     *
     * <p>Las dos sentencias de arriba son {@code INSERT ... SELECT ... WHERE}, o sea que cuando lo
     * que nombran no esta en esta copia no fallan: escriben <b>cero filas</b>. Medido con la
     * emision de {@code registrarUsuario} quitada, el consumidor se tragaba las tres afiliaciones
     * sin una linea y el rojo salia mucho despues, al comparar la tabla `miembro` vacia — un
     * diagnostico que apunta a la tabla y no al evento. Es la forma de {@code rentas}#54 dentro del
     * propio instrumento: quien aplica no puede descartar en silencio lo que no sabe colocar.
     */
    private static void exigirQueEscribiera(int escritas, String tabla, String loQueNombra) {
        if (escritas != 1) {
            throw new IllegalStateException(
                    "El evento de `"
                            + tabla
                            + "` nombra "
                            + loQueNombra
                            + ", y esta copia no lo conoce: la sentencia escribio "
                            + escritas
                            + " filas. Un evento que llega antes que aquel del que depende no se"
                            + " puede aplicar, y descartarlo en silencio deja la copia desatrasada"
                            + " sin que nada lo diga");
        }
    }

    private static @Nullable String texto(JsonNode cuerpo, String campo) {
        JsonNode valor = cuerpo.path(campo);
        return valor.isNull() || valor.isMissingNode() ? null : valor.asString();
    }

    private static @Nullable LocalDate fecha(JsonNode cuerpo, String campo) {
        String valor = texto(cuerpo, campo);
        return valor == null ? null : LocalDate.parse(valor);
    }
}
