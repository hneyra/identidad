package kamayuk.identidad.seguridad.infraestructura;

import java.time.LocalDate;
import kamayuk.identidad.autorizacion.ComprobadorDeAcceso;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el permiso contra {@code acceso}, {@code grupo}, {@code miembro}, {@code permiso} y
 * {@code usuario} <b>de la base de este sistema</b> (D-N5, que contesta D-19).
 *
 * <p>Es la misma consulta que {@code rentas}, letra por letra, y eso es a proposito: son dos copias
 * del <b>mismo</b> modelo del manual sobre dos copias de las <b>mismas</b> cinco tablas (ADR-0032
 * las replica en los cinco baselines). Escribir aqui otra precedencia produciria un sistema donde
 * el mismo usuario puede una cosa en una pantalla y no en la de al lado, y el sintoma —un 403 en un
 * sitio y no en otro— no se parece a su causa.
 *
 * <h2>La precedencia, que es la decision que hay que conocer</h2>
 *
 * <p><b>La excepcion del usuario decide; si no la hay, mandan sus grupos.</b> Si existe una fila de
 * {@code permiso} para ese usuario y ese acceso, esa fila resuelve —otorgue o niegue—; si no
 * existe, se toma la union de los permisos de los grupos vigentes a los que pertenece. El precio
 * esta a la vista y se acepta: una excepcion <b>sustituye</b> al grupo entero para ese acceso.
 *
 * <p>La vigencia se comprueba en los tres sitios (RF-123) —usuario, grupo y pertenencia—, porque
 * comprobar solo una deja abierta la puerta mas comoda: dar de baja al usuario y que siga entrando
 * por un grupo vigente.
 *
 * <p>La consulta no filtra por municipalidad: lo hace la politica RLS con el contexto de la
 * transaccion (regla 2). Un usuario de otra municipalidad, sencillamente, no existe desde aqui.
 *
 * <h2>Lo que la columna {@code sistema} deja abierto, dicho aqui y no descubierto en la etapa 2
 * </h2>
 *
 * <p>El esquema de {@code identidad} llavea {@code acceso} por {@code (municipalidad_id, sistema,
 * codigo)} porque esta base guarda los catalogos de los cinco. Esta consulta empareja el acceso por
 * {@code a.codigo = :acceso} <b>y nada mas</b>, y no puede hacer otra cosa: el puerto que
 * implementa —{@link kamayuk.identidad.autorizacion.ComprobadorDeAcceso#autoriza}— recibe el codigo
 * del acceso y no de que sistema es, porque en los otros cuatro esa pregunta no existe.
 *
 * <p>Mientras esta base tenga sembrado <b>un solo</b> catalogo —la etapa 1 siembra el de {@code
 * identidad} y nada mas— hay exactamente una fila por codigo y la consulta es correcta. El dia que
 * entren los cinco, dos codigos iguales de sistemas distintos hacen que el {@code single()} de la
 * primera rama <b>reviente</b>, y no en silencio: sale como un error del guardia en cada peticion a
 * esa opcion. Quien decide de que sistema es el acceso que se comprueba —y por tanto si el puerto
 * gana un parametro o si el codigo pasa a llevar el sistema dentro— es la etapa 2. No se elige
 * ahora ni se tapa con un {@code LIMIT 1}: elegir la primera fila seria autorizar contra el permiso
 * de otro sistema, que es peor que fallar.
 */
@Component
public class ComprobadorDeAccesoJdbc extends RepositorioJdbc implements ComprobadorDeAcceso {

    public ComprobadorDeAccesoJdbc(JdbcClient jdbc) {
        super(jdbc);
    }

    /**
     * <b>{@code @Transactional} no es decorativo aqui.</b> Estas cinco tablas llevan RLS con {@code
     * FORCE}, y sus politicas leen {@code app.municipalidad_id}, que el gestor de transacciones
     * fija con {@code SET LOCAL} <b>al abrir una transaccion</b>. Sin transaccion no hay parametro,
     * y PostgreSQL no devuelve cero filas: falla con «unrecognized configuration parameter» y eso
     * llega al cliente como un 500 (DAT-01 §0, y #486 lo midio doce veces).
     *
     * <p>El guardia corre en un {@code preHandle}, antes de que ningun caso de uso abra la suya,
     * asi que este es el unico sitio del sistema que consulta tablas de tenant sin una transaccion
     * ya abierta. Las pruebas no lo verian: abren la suya.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean autoriza(String usuario, String acceso, Privilegio privilegio, LocalDate fecha) {

        String columna = privilegio.columna();

        String sql =
                "SELECT COALESCE("
                        // 1. La excepcion del usuario, si la hay: decide, otorgue o niegue.
                        + "  (SELECT p."
                        + columna
                        + "     FROM permiso p"
                        + "     JOIN acceso a ON a.id = p.acceso_id AND a.codigo = :acceso"
                        + "     JOIN usuario u ON u.id = p.usuario_id"
                        + "    WHERE u.cuenta = :usuario),"
                        // 2. Si no la hay: la union de los grupos vigentes.
                        + "  EXISTS ("
                        + "    SELECT 1 FROM usuario u"
                        + "      JOIN miembro m ON m.usuario_id = u.id AND m.activo"
                        + "      JOIN grupo g ON g.id = m.grupo_id"
                        + "                  AND g.habilitado"
                        + "                  AND (g.vigencia_desde IS NULL OR g.vigencia_desde <= :fecha)"
                        + "                  AND (g.vigencia_hasta IS NULL OR g.vigencia_hasta >= :fecha)"
                        + "      JOIN permiso p ON p.grupo_id = g.id"
                        + "      JOIN acceso a ON a.id = p.acceso_id AND a.codigo = :acceso AND a.activo"
                        + "     WHERE u.cuenta = :usuario AND p."
                        + columna
                        + "  ), false)"
                        // 3. Y por encima de todo: el usuario tiene que estar habilitado y
                        //    vigente. Va al final para que se lea como lo que es, una
                        //    condicion que anula cualquier permiso.
                        + " AND EXISTS ("
                        + "   SELECT 1 FROM usuario u"
                        + "    WHERE u.cuenta = :usuario"
                        + "      AND u.habilitado"
                        + "      AND (u.vigencia_desde IS NULL OR u.vigencia_desde <= :fecha)"
                        + "      AND (u.vigencia_hasta IS NULL OR u.vigencia_hasta >= :fecha))";

        return Boolean.TRUE.equals(
                jdbc().sql(sql)
                        .param("usuario", usuario)
                        .param("acceso", acceso)
                        .param("fecha", fecha)
                        .query(Boolean.class)
                        .single());
    }

    /**
     * {@code @Transactional} por lo mismo que {@link #autoriza}: {@code usuario} lleva RLS con
     * {@code FORCE} y su politica lee {@code app.municipalidad_id}, que se fija con {@code SET
     * LOCAL} al abrir la transaccion. Sin ella no salen cero filas: sale un 500.
     *
     * <p>Se pregunta por la EXISTENCIA de la fila y nada mas —ni {@code habilitado} ni vigencia—,
     * porque lo que distingue es «este sistema no te conoce» de «te conoce y no te deja». Un
     * usuario deshabilitado SI esta dado de alta, y su remedio es otro: lo habilita un
     * administrador.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean conoceAlUsuario(String usuario) {
        return Boolean.TRUE.equals(
                jdbc().sql("SELECT EXISTS (SELECT 1 FROM usuario u WHERE u.cuenta = :usuario)")
                        .param("usuario", usuario)
                        .query(Boolean.class)
                        .single());
    }
}
