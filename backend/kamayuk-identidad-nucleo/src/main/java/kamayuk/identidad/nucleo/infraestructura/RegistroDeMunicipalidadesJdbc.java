package kamayuk.identidad.nucleo.infraestructura;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * La unica clase de {@code identidad} que se conecta como {@code kamayuk_owner}, y por eso conviene
 * mirarla con atencion.
 *
 * <h2>Por que no usa el pool de la aplicacion</h2>
 *
 * <p>Porque no puede: el pool es {@code kamayuk_app}, y {@code municipalidad} solo la escribe
 * {@code kamayuk_owner} —el baseline le da una politica {@code FOR ALL TO kamayuk_owner} y lo
 * explica: dar de alta una municipalidad es una operacion de implantacion—. La conexion se abre
 * para una sentencia y se cierra; no queda en ningun pool ni la puede tomar nadie mas.
 *
 * <h2>Las tres condiciones que la mantienen encerrada</h2>
 *
 * <ul>
 *   <li>{@code @Profile("batch")}: no existe en el proceso que atiende HTTP.
 *   <li>{@code @ConditionalOnProperty}: tampoco en una corrida batch normal. Hay que pedir la
 *       implantacion explicitamente.
 *   <li>Sus credenciales llegan por propiedades propias, distintas de las de la aplicacion, asi que
 *       un despliegue que no las ponga no obtiene un componente a medias: no lo obtiene.
 * </ul>
 *
 * <p>Es la gemela de la de {@code rentas}, y son dos porque son <b>dos bases</b>: cada sistema
 * tiene la suya (ADR-0032) y la fila de {@code municipalidad} de una no existe en la otra. Ese es
 * el hueco 3 que C-6 midio: sin esta clase, este sistema no tendria nada que escribiera esa fila —
 * y sin ella {@code SoloEnDemostracion} y toda politica RLS se quedan sin municipalidad que
 * resolver.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.implantacion.ubigeo")
public class RegistroDeMunicipalidadesJdbc {

    private final String url;
    private final String usuario;
    private final String clave;

    public RegistroDeMunicipalidadesJdbc(
            @Value("${kamayuk.implantacion.url}") String url,
            @Value("${kamayuk.implantacion.owner-usuario:kamayuk_owner}") String usuario,
            @Value("${kamayuk.implantacion.owner-clave}") String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /**
     * Deja la fila con el {@code id} DECLARADO si falta, y devuelve el que hay. Idempotente.
     *
     * <p><b>El id se escribe, no se pide a la secuencia</b>, y es la salida 1 de <a
     * href="https://github.com/hneyra/infrastructure/issues/73">infrastructure#73</a>. Con la
     * secuencia, el archivo versionado declaraba {@code municipalidadId: 9} y la base asignaba
     * {@code 1}; el claim de los funcionarios salia con el 9, el de las cuentas de servicio con el
     * ubigeo, y solo el 1 lo entendia el RLS. Medido en {@code stg} el 2026-09-11: las cinco fichas
     * en el inquilino 1 y un token diciendo 200105, con el resultado de un 403 «la cuenta no esta
     * dada de alta» CON LA FILA DELANTE.
     *
     * <p><b>Y si la fila ya existe con OTRO id, esto FALLA en vez de seguir.</b> Es la decision de
     * diseño de este metodo y conviene que se lea: ese id es la clave de la que cuelga el RLS de
     * todas las tablas de esta base, asi que cambiarlo dejaria huerfana cada fila de {@code
     * usuario}, {@code grupo}, {@code miembro}, {@code permiso} y del buzon — y no daria ningun
     * error, porque la base haria exactamente lo que se le pide. Un ambiente que ya existe con otro
     * id se arregla decidiendolo (declarar el que tiene, o migrar los datos), no de pasada en un
     * despliegue.
     */
    public long darDeAltaSiFalta(
            String ubigeo,
            long municipalidadId,
            String nombre,
            String tipo,
            boolean esDemostracion) {
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
            insertarSiFalta(conexion, ubigeo, municipalidadId, nombre, tipo, esDemostracion);
            long enLaBase = identificador(conexion, ubigeo);
            if (enLaBase != municipalidadId) {
                throw new IllegalStateException(
                        "La municipalidad "
                                + ubigeo
                                + " ya esta dada de alta con el id "
                                + enLaBase
                                + " y lo declarado es "
                                + municipalidadId
                                + ". NO se cambia: ese id es el inquilino del que cuelga el RLS de"
                                + " todas las tablas de esta base, asi que cambiarlo dejaria"
                                + " huerfana cada fila de usuario, grupo, miembro, permiso y del"
                                + " buzon, y sin un solo error — la base haria lo que se le pide."
                                + " Se arregla decidiendolo: o se declara el id que la base tiene"
                                + " (kamayuk.implantacion.municipalidad-id), o se migran los datos"
                                + " al declarado (infrastructure#73)");
            }
            avanzarLaSecuencia(conexion);
            return enLaBase;
        } catch (SQLException noSePudo) {
            // Sin el ubigeo, el mensaje de PostgreSQL no dice de que municipalidad habla.
            throw new IllegalStateException(
                    "No se pudo dar de alta la municipalidad " + ubigeo, noSePudo);
        }
    }

    /**
     * {@code ON CONFLICT (ubigeo) DO NOTHING} y despues la consulta.
     *
     * <p>Es lo que hace el paso idempotente sin leer primero: leer y luego insertar deja una
     * ventana entre las dos cosas, y dos despliegues a la vez acabarian uno de ellos con un error
     * de clave duplicada. Asi los dos acaban con la misma fila.
     */
    private static void insertarSiFalta(
            Connection conexion,
            String ubigeo,
            long municipalidadId,
            String nombre,
            String tipo,
            boolean esDemostracion)
            throws SQLException {
        try (PreparedStatement alta =
                conexion.prepareStatement(
                        "INSERT INTO municipalidad (id, ubigeo, nombre, tipo, es_demostracion)"
                                + " OVERRIDING SYSTEM VALUE"
                                + " VALUES (?, ?, ?, ?, ?)"
                                + " ON CONFLICT (ubigeo) DO NOTHING")) {
            alta.setLong(1, municipalidadId);
            alta.setString(2, ubigeo);
            alta.setString(3, nombre);
            alta.setString(4, tipo);
            alta.setBoolean(5, esDemostracion);
            alta.executeUpdate();
        }
    }

    /**
     * Deja la secuencia por encima del id mas alto que hay.
     *
     * <p>Hace falta porque insertar un id explicito NO la avanza: sin esto, un {@code INSERT}
     * posterior que si la use —una prueba, o una segunda municipalidad dada de alta por otro
     * camino— pediria un valor que ya esta ocupado y fallaria con una violacion de clave primaria
     * mucho despues y en otro sitio. Es el efecto colateral conocido de {@code OVERRIDING SYSTEM
     * VALUE}, y se paga aqui una vez en cada implantacion.
     */
    private static void avanzarLaSecuencia(Connection conexion) throws SQLException {
        try (PreparedStatement ajuste =
                conexion.prepareStatement(
                        "SELECT setval(pg_get_serial_sequence('municipalidad', 'id'),"
                                + " GREATEST((SELECT max(id) FROM municipalidad), 1))")) {
            ajuste.executeQuery().close();
        }
    }

    private static long identificador(Connection conexion, String ubigeo) throws SQLException {
        try (PreparedStatement consulta =
                conexion.prepareStatement("SELECT id FROM municipalidad WHERE ubigeo = ?")) {
            consulta.setString(1, ubigeo);
            try (ResultSet fila = consulta.executeQuery()) {
                if (!fila.next()) {
                    throw new IllegalStateException(
                            "La municipalidad " + ubigeo + " no quedo dada de alta");
                }
                return fila.getLong("id");
            }
        }
    }
}
