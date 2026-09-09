package kamayuk.identidad.esquema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Siembra una fila en <b>cada</b> tabla de tenant de {@code identidad}, para las dos
 * municipalidades de la prueba.
 *
 * <p>La cobertura completa no es adorno: la verificacion "con contexto de A no se ve ninguna fila
 * de B" es vacia si en la tabla no hay filas de B. Una tabla sin datos sembrados pasaria en verde
 * sin probar nada, que es justamente el modo de fallo contra el que existe esta prueba. Por eso la
 * prueba exige ademas que cada tabla de tenant tenga al menos una fila propia.
 *
 * <p><b>Al agregar una tabla de tenant hay que sembrarla aqui.</b> Si no, el build se pone rojo con
 * el mensaje de que la municipalidad A no ve filas suyas en esa tabla.
 *
 * <p><b>Es la version recortada de la de {@code normativa}</b>, no una copia: alli siembra 19
 * tablas y aqui hay 13. Se recorto y no se copio entera porque una siembra que nombra tablas que
 * este esquema no tiene no llega a correr contra el motor, y una que las nombrara «por si acaso»
 * diria que este sistema guarda valores normativos y no guarda ninguno.
 *
 * <p><b>Y no hay ningun catalogo nacional que sembrar</b>: las tres tablas de valuacion y {@code
 * parametro_tributario} son de {@code normativa}, asi que aqui no se abre ni una conexion como
 * {@code rol_carga_parametros} — que ademas no podria abrirse, porque este esquema no le concede
 * {@code CONNECT} (ver {@code crear-roles.sql}).
 *
 * <p><b>{@code modulo_sistema} y {@code acceso} se siembran con su columna {@code sistema}</b>, que
 * es la unica desviacion de este baseline. Se siembran DOS sistemas distintos con el MISMO codigo a
 * proposito: es lo que la columna existe para admitir, y sin dos filas asi el UNIQUE nuevo pasaria
 * en verde sin haberse ejercido.
 */
public final class DatosDePrueba {

    private static final LocalDate VIGENCIA = LocalDate.of(2026, 1, 1);
    private static final short EJERCICIO = 2026;

    /**
     * El modelo minimo que {@code documento_emitido.datos} admite: un {@code ModeloDeDocumento}.
     */
    private static final String MODELO_DE_DOCUMENTO =
            "{\"titulo\":\"Documento de prueba\",\"subtitulo\":null,\"aLaFecha\":\"2026-01-01\","
                    + "\"cabecera\":[],\"tablas\":[],\"pie\":[],\"duplicado\":null}";

    private DatosDePrueba() {}

    /** El alta de una municipalidad es una operacion de implantacion: la hace el owner. */
    public static long crearMunicipalidad(BaseDeDatosDePrueba base, String ubigeo, String nombre)
            throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER)) {
            long id =
                    insertar(
                            owner,
                            "INSERT INTO municipalidad (ubigeo, nombre, tipo)"
                                    + " VALUES (?, ?, 'DISTRITAL') RETURNING id",
                            ubigeo,
                            nombre);
            owner.commit();
            return id;
        }
    }

    /**
     * Siembra todas las tablas de tenant como {@code kamayuk_app} y con el contexto de la
     * municipalidad fijado. Sembrar con el rol de la aplicacion, y no con el owner, verifica de
     * paso que la clausula {@code WITH CHECK} deja pasar lo que debe dejar pasar.
     */
    public static void sembrarTenant(BaseDeDatosDePrueba base, long muni, String sufijo)
            throws SQLException {
        try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
            ContextoDeTenant.fijar(app, muni);

            sembrarSeguridad(app, muni, sufijo);
            sembrarDocumento(app, muni, sufijo);

            app.commit();
        }
    }

    private static void sembrarSeguridad(Connection app, long muni, String sufijo)
            throws SQLException {
        long moduloId = sembrarModulo(app, muni, "identidad", "MOD-" + sufijo);
        long accesoId = sembrarAcceso(app, muni, moduloId, "identidad", "opcion-" + sufijo);

        // El MISMO codigo de modulo y de opcion, de OTRO sistema. Es lo que la columna `sistema`
        // existe para admitir —esta base guarda los catalogos de los cinco y dos sistemas pueden
        // nombrar igual dos opciones distintas—, y sin estas dos filas el UNIQUE de tres columnas
        // pasaria en verde sin haberse ejercido nunca: con el de dos, el segundo INSERT choca.
        long moduloAjeno = sembrarModulo(app, muni, "rentas", "MOD-" + sufijo);
        sembrarAcceso(app, muni, moduloAjeno, "rentas", "opcion-" + sufijo);
        long grupoId =
                insertar(
                        app,
                        "INSERT INTO grupo (municipalidad_id, nombre, descripcion)"
                                + " VALUES (?, ?, 'Grupo de prueba') RETURNING id",
                        muni,
                        "Identidad " + sufijo);
        long usuarioId =
                insertar(
                        app,
                        "INSERT INTO usuario (municipalidad_id, cuenta, nombre)"
                                + " VALUES (?, ?, 'Usuario de prueba') RETURNING id",
                        muni,
                        "usuario-" + sufijo);
        ejecutar(
                app,
                "INSERT INTO miembro (municipalidad_id, grupo_id, usuario_id, usuario_alta)"
                        + " VALUES (?, ?, ?, 'prueba')",
                muni,
                grupoId,
                usuarioId);
        ejecutar(
                app,
                "INSERT INTO permiso (municipalidad_id, acceso_id, grupo_id, lectura, registro,"
                        + " usuario_registro) VALUES (?, ?, ?, true, true, 'prueba')",
                muni,
                accesoId,
                grupoId);
        ejecutar(
                app,
                "INSERT INTO sesion (municipalidad_id, usuario_id, origen_equipo, origen_ip,"
                        + " ejercicio_trabajo)"
                        + " VALUES (?, ?, 'PC-PRUEBA', CAST(? AS inet), ?)",
                muni,
                usuarioId,
                "10.0.0.1",
                EJERCICIO);
        ejecutar(
                app,
                "INSERT INTO auditoria (municipalidad_id, ejercicio, tabla, clave, operacion,"
                        + " usuario_id, origen_equipo, origen_ip, observacion)"
                        + " VALUES (?, ?, 'permiso', '1', 'ALTA', 'prueba', 'PC-PRUEBA',"
                        + "         CAST(? AS inet), 'alta inicial de la prueba de aislamiento')",
                muni,
                EJERCICIO,
                "10.0.0.1");
    }

    private static long sembrarModulo(Connection app, long muni, String sistema, String codigo)
            throws SQLException {
        return insertar(
                app,
                "INSERT INTO modulo_sistema (municipalidad_id, sistema, codigo, nombre)"
                        + " VALUES (?, ?, ?, 'Seguridad') RETURNING id",
                muni,
                sistema,
                codigo);
    }

    private static long sembrarAcceso(
            Connection app, long muni, long moduloId, String sistema, String codigo)
            throws SQLException {
        return insertar(
                app,
                "INSERT INTO acceso (municipalidad_id, modulo_id, sistema, tipo, codigo, nombre)"
                        + " VALUES (?, ?, ?, 'OPCION_MENU', ?, 'Opcion de prueba') RETURNING id",
                muni,
                moduloId,
                sistema,
                codigo);
    }

    private static void sembrarDocumento(Connection app, long muni, String sufijo)
            throws SQLException {
        ejecutar(
                app,
                "INSERT INTO documento_emitido (municipalidad_id, tipo, numero, ejercicio,"
                        + " referencia, datos, formato, resumen, fecha_emision, usuario_emision,"
                        + " observacion)"
                        + " VALUES (?, 'CONSTANCIA_DE_ACCESOS', ?, 2026, 'usuario#1',"
                        + "         CAST(? AS jsonb), 'PDF', repeat('a', 64), ?, 'siembra',"
                        + "         'documento de prueba')",
                muni,
                "CONSTANCIA_DE_ACCESOS-2026-00000" + (sufijo.equals("A") ? "1" : "2"),
                MODELO_DE_DOCUMENTO,
                VIGENCIA);
    }

    /** Identificador del grupo sembrado en una municipalidad. */
    public static long grupoDe(BaseDeDatosDePrueba base, long municipalidadId) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia =
                        admin.prepareStatement(
                                "SELECT id FROM grupo WHERE municipalidad_id = ?"
                                        + " ORDER BY id LIMIT 1")) {
            sentencia.setLong(1, municipalidadId);
            return unicoLong(sentencia);
        }
    }

    private static long insertar(Connection conexion, String sql, Object... valores)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            fijar(sentencia, valores);
            return unicoLong(sentencia);
        }
    }

    private static void ejecutar(Connection conexion, String sql, Object... valores)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            fijar(sentencia, valores);
            sentencia.executeUpdate();
        }
    }

    private static void fijar(PreparedStatement sentencia, Object... valores) throws SQLException {
        for (int i = 0; i < valores.length; i++) {
            sentencia.setObject(i + 1, valores[i]);
        }
    }

    private static long unicoLong(PreparedStatement sentencia) throws SQLException {
        try (ResultSet resultado = sentencia.executeQuery()) {
            if (!resultado.next()) {
                throw new IllegalStateException("La sentencia no devolvio ninguna fila");
            }
            return resultado.getLong(1);
        }
    }
}
