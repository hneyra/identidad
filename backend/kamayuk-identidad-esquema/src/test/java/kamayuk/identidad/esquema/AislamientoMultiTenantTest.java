package kamayuk.identidad.esquema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Prueba de aislamiento multi-tenant — ARQ-03, CAL-01 §3. <b>Bloqueante.</b>
 *
 * <p><b>El requisito que la hace valida:</b> todas las verificaciones de aislamiento se hacen
 * conectadas como {@code kamayuk_app}, un rol creado en el arranque de la prueba. La conexion que
 * Testcontainers entrega por omision es de superusuario, y un superusuario omite RLS incluso con
 * {@code FORCE ROW LEVEL SECURITY} (DAT-01 §0, hallazgo 1). Una prueba escrita sobre esa conexion
 * pasa en verde sin proteger nada.
 *
 * <p>Eso no se afirma: se demuestra en {@link
 * Trampa#superusuarioOmiteRlsPorEsoLaPruebaNoUsaEsaConexion()}, que verifica que la conexion de
 * superusuario efectivamente ve las dos municipalidades.
 */
@DisplayName("ARQ-03 — Aislamiento multi-tenant")
class AislamientoMultiTenantTest {

    /**
     * Tablas que legitimamente no llevan RLS. Es deliberadamente corta: agregar una entrada aqui
     * tiene que doler y verse en el diff. {@code flyway_schema_history} la crea y la usa solo
     * {@code kamayuk_owner}; la aplicacion no tiene ningun privilegio sobre ella.
     *
     * <p><b>{@code spatial_ref_sys} NO esta aqui</b>, y conviene decir por que en vez de copiarla
     * «por si acaso»: la instala PostGIS, y el {@code crear-roles.sql} de este sistema no declara
     * ninguna extension —la geometria del predio es de {@code catastro} (ADR-0021) y aqui no hay un
     * solo tipo {@code geography}—. Es la misma entrada que {@code normativa} retiro en C-13 y
     * {@code rentas} en P5E: una exencion que ya no exime nada se queda dentro para siempre y la
     * lista deja de decir lo que exime.
     */
    private static final Set<String> TABLAS_EXENTAS = Set.of("flyway_schema_history");

    /**
     * Catalogos: no llevan {@code municipalidad_id NOT NULL}, pero si RLS con politica propia
     * (ARQ-03 §5). Se enumeran para que una tabla nueva no pueda quedar sin clasificar.
     *
     * <p>Son DOS y no seis, que es lo que este sistema tiene: {@code municipalidad} —el registro de
     * tenants, que la aplicacion lee entero— y {@code respaldo}, porque una copia de seguridad es
     * del cluster entero y no de una municipalidad; la aplicacion solo la lee.
     *
     * <p>Las cuatro que faltan respecto de {@code normativa} —{@code parametro_tributario} y los
     * tres cuadros de valuacion— son suyas y no viajan: aqui no se guarda ni un valor normativo.
     */
    private static final Set<String> TABLAS_DE_CATALOGO = Set.of("municipalidad", "respaldo");

    private static BaseDeDatosDePrueba base;
    private static long municipalidadA;
    private static long municipalidadB;
    private static List<String> tablasDeTenant;
    private static List<String> particiones;

    @BeforeAll
    static void provisionar() throws Exception {
        base = BaseDeDatosDePrueba.provisionar();

        municipalidadA = DatosDePrueba.crearMunicipalidad(base, "200601", "Municipalidad A");
        municipalidadB = DatosDePrueba.crearMunicipalidad(base, "200602", "Municipalidad B");
        DatosDePrueba.sembrarTenant(base, municipalidadA, "A");
        DatosDePrueba.sembrarTenant(base, municipalidadB, "B");

        tablasDeTenant =
                consultarTextos(
                        "SELECT c.relname"
                                + "  FROM pg_class c"
                                + "  JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + "  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname ="
                                + " 'municipalidad_id'"
                                + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p')"
                                + "   AND NOT c.relispartition AND a.attnotnull AND NOT a.attisdropped"
                                + " ORDER BY 1");
        particiones =
                consultarTextos(
                        "SELECT c.relname"
                                + "  FROM pg_class c"
                                + "  JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p') AND"
                                + " c.relispartition"
                                + " ORDER BY 1");

        // Si esto queda vacio, todo lo demas pasa en verde sin verificar nada.
        assertThat(tablasDeTenant).as("tablas de tenant detectadas").isNotEmpty();
        assertThat(particiones).as("particiones detectadas").isNotEmpty();
    }

    @AfterAll
    static void liberar() {
        if (base != null) {
            base.close();
        }
    }

    // ------------------------------------------------------------------
    // a) Cobertura estructural
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("a) Cobertura estructural")
    class CoberturaEstructural {

        @Test
        @DisplayName("toda tabla esta clasificada como de tenant, de catalogo o exenta")
        void todaTablaEstaClasificada() throws SQLException {
            List<String> todas =
                    consultarTextos(
                            "SELECT c.relname FROM pg_class c"
                                    + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                                    + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p')"
                                    + "   AND NOT c.relispartition ORDER BY 1");

            Set<String> clasificadas = new LinkedHashSet<>(tablasDeTenant);
            clasificadas.addAll(TABLAS_DE_CATALOGO);
            clasificadas.addAll(TABLAS_EXENTAS);

            assertThat(todas)
                    .as(
                            "una tabla nueva sin clasificar rompe el build: o lleva municipalidad_id"
                                    + " NOT NULL, o entra a la lista de catalogos, o entra a la de"
                                    + " exentas con justificacion en el PR (ARQ-03 §7)")
                    .allSatisfy(tabla -> assertThat(clasificadas).contains(tabla));
        }

        @Test
        @DisplayName("EL REVERSO: ninguna entrada de TABLAS_EXENTAS sobra")
        void ningunaExencionSobra() throws SQLException {
            // La lista tiene dos direcciones y solo una la medía nadie. `todaTablaEstaClasificada`
            // exige que toda tabla este en alguna lista; sin esta, una entrada que ya no exime
            // nada se queda dentro para siempre y la lista deja de decir lo que exime.
            //
            // Y no es hipotetico: `spatial_ref_sys` la instala PostGIS, y hasta C-21 la base de
            // prueba la heredaba de `template1` por el camino de Testcontainers. La salida comoda
            // ante ese rojo era anadirla aqui en los tres sistemas que NO crean PostGIS —lo que
            // habria dejado a local y a CI midiendo bases distintas—. Con esta guarda, esa salida
            // ya no esta: en un sistema que no crea la extension, la tabla no existe y la entrada
            // se rechaza nombrandola.
            List<String> existentes =
                    consultarTextos(
                            "SELECT c.relname FROM pg_class c"
                                    + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                                    + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p')"
                                    + " ORDER BY 1");

            assertThat(TABLAS_EXENTAS)
                    .as(
                            "una exencion que ya no exime nada es peor que ninguna: dice que hay"
                                    + " una tabla sin RLS donde no hay ninguna tabla. Si la tabla se"
                                    + " fue, la entrada se va con ella; si nunca estuvo, la entrada"
                                    + " esta tapando un rojo que hay que arreglar en otro sitio")
                    .allSatisfy(exenta -> assertThat(existentes).contains(exenta));
        }

        @Test
        @DisplayName("toda tabla no exenta tiene RLS activa y forzada")
        void todaTablaTieneRlsActivaYForzada() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            try (Connection admin = base.conexionAdmin();
                    Statement sentencia = admin.createStatement();
                    ResultSet fila =
                            sentencia.executeQuery(
                                    "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity"
                                            + "  FROM pg_class c JOIN pg_namespace n ON n.oid ="
                                            + " c.relnamespace"
                                            + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p')"
                                            + " ORDER BY 1")) {
                while (fila.next()) {
                    String tabla = fila.getString(1);
                    if (TABLAS_EXENTAS.contains(tabla)) {
                        continue;
                    }
                    verificaciones
                            .assertThat(fila.getBoolean(2))
                            .as("%s tiene ENABLE ROW LEVEL SECURITY", tabla)
                            .isTrue();
                    verificaciones
                            .assertThat(fila.getBoolean(3))
                            .as(
                                    "%s tiene FORCE ROW LEVEL SECURITY (sin esto, el propietario"
                                            + " evade la politica)",
                                    tabla)
                            .isTrue();
                }
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("toda tabla de tenant tiene politica con USING y WITH CHECK")
        void todaTablaDeTenantTienePoliticaCompleta() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String tabla : tablasDeTenant) {
                verificaciones
                        .assertThat(politicasCompletasDe(tabla))
                        .as(
                                "%s necesita una politica con USING y con WITH CHECK; solo con"
                                        + " USING, un INSERT puede plantar filas en otro tenant (ARQ-03"
                                        + " §3.4)",
                                tabla)
                        .isNotEmpty();
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("toda tabla de catalogo tiene al menos una politica")
        void todaTablaDeCatalogoTienePolitica() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String tabla : TABLAS_DE_CATALOGO) {
                verificaciones
                        .assertThat(politicasDe(tabla))
                        .as(
                                "la regla 'toda tabla tiene RLS' es absoluta; en los catalogos"
                                        + " cambia la politica, no su existencia (ARQ-03 §5)",
                                tabla)
                        .isNotEmpty();
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("ninguna politica usa subconsulta")
        void ningunaPoliticaUsaSubconsulta() throws SQLException {
            List<String> conSubconsulta =
                    consultarTextos(
                            "SELECT tablename || '.' || policyname FROM pg_policies"
                                    + " WHERE schemaname = 'public'"
                                    + "   AND (COALESCE(qual, '') ILIKE '%SELECT%'"
                                    + "        OR COALESCE(with_check, '') ILIKE '%SELECT%')");
            assertThat(conSubconsulta)
                    .as("una politica con subconsulta se evalua por fila y cuesta en cada consulta")
                    .isEmpty();
        }

        @Test
        @DisplayName("toda particion tiene RLS explicita, no heredada del padre")
        void todaParticionTieneRlsExplicita() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            try (Connection admin = base.conexionAdmin();
                    Statement sentencia = admin.createStatement();
                    ResultSet fila =
                            sentencia.executeQuery(
                                    // relkind: un indice particionado tambien lleva relispartition.
                                    "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity"
                                            + "  FROM pg_class c JOIN pg_namespace n ON n.oid ="
                                            + " c.relnamespace"
                                            + " WHERE n.nspname = 'public' AND c.relispartition"
                                            + "   AND c.relkind IN ('r','p') ORDER BY 1")) {
                while (fila.next()) {
                    String particion = fila.getString(1);
                    verificaciones
                            .assertThat(fila.getBoolean(2))
                            .as(
                                    "%s: una particion no hereda relrowsecurity del padre"
                                            + " (DAT-01 §0, hallazgo 2)",
                                    particion)
                            .isTrue();
                    verificaciones
                            .assertThat(fila.getBoolean(3))
                            .as("%s: FORCE ROW LEVEL SECURITY explicito en la particion", particion)
                            .isTrue();
                }
            }
            for (String particion : particiones) {
                verificaciones
                        .assertThat(politicasCompletasDe(particion))
                        .as("%s necesita politica propia (mitigacion 1 de ARQ-03 §3.5)", particion)
                        .isNotEmpty();
            }
            verificaciones.assertAll();
        }
    }

    // ------------------------------------------------------------------
    // b) Aislamiento efectivo, tabla por tabla
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("b) Aislamiento efectivo, tabla por tabla")
    class AislamientoEfectivo {

        @Test
        @DisplayName("con contexto de A, ninguna lectura devuelve filas de B")
        void ningunaLecturaDevuelveFilasDeB() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                ContextoDeTenant.fijar(app, municipalidadA);
                for (String tabla : tablasDeTenant) {
                    // Que A vea lo suyo es parte de la verificacion: si la tabla estuviera
                    // vacia, "no se ve nada de B" seria cierto y no probaria nada. Si esto
                    // falla en una tabla nueva, lo que falta es sembrarla en DatosDePrueba.
                    verificaciones
                            .assertThat(contar(app, "SELECT count(*) FROM " + tabla))
                            .as("%s: la municipalidad A debe ver sus propias filas", tabla)
                            .isPositive();
                    verificaciones
                            .assertThat(
                                    contar(
                                            app,
                                            "SELECT count(*) FROM "
                                                    + tabla
                                                    + " WHERE municipalidad_id = "
                                                    + municipalidadB))
                            .as("%s: fuga de filas de la municipalidad B", tabla)
                            .isZero();
                }
                app.rollback();
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("un INSERT con municipalidad_id de B falla por WITH CHECK")
        void insertarConMunicipalidadAjenaFalla() {
            String estado =
                    estadoSqlDelFallo(
                            () -> {
                                try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                                        PreparedStatement sentencia =
                                                app.prepareStatement(
                                                        "INSERT INTO grupo"
                                                                + " (municipalidad_id, nombre,"
                                                                + " descripcion)"
                                                                + " VALUES (?, 'Intruso',"
                                                                + " 'grupo plantado desde otra"
                                                                + " municipalidad')")) {
                                    ContextoDeTenant.fijar(app, municipalidadA);
                                    sentencia.setLong(1, municipalidadB);
                                    sentencia.executeUpdate();
                                }
                            });
            assertThat(estado)
                    .as(
                            "sin WITH CHECK, un INSERT puede plantar datos en otro tenant aunque no"
                                    + " pueda leerlos; se espera InsufficientPrivilege, no exito")
                    .isEqualTo("42501");
        }

        @Test
        @DisplayName("un INSERT ajeno tambien falla en una tabla particionada")
        void insertarConMunicipalidadAjenaFallaEnTablaParticionada() throws SQLException {
            String estado =
                    estadoSqlDelFallo(
                            () -> {
                                try (Connection app = base.conexion(BaseDeDatosDePrueba.APP);
                                        PreparedStatement sentencia =
                                                app.prepareStatement(
                                                        "INSERT INTO auditoria"
                                                                + " (municipalidad_id, ejercicio,"
                                                                + " tabla, clave, operacion,"
                                                                + " usuario_id, origen_equipo,"
                                                                + " origen_ip, observacion)"
                                                                + " VALUES (?, 2026,"
                                                                + " 'permiso', '1',"
                                                                + " 'ALTA', 'intruso', 'PC',"
                                                                + " CAST('10.0.0.9' AS inet),"
                                                                + " 'fila plantada desde otra"
                                                                + " municipalidad')")) {
                                    ContextoDeTenant.fijar(app, municipalidadA);
                                    sentencia.setLong(1, municipalidadB);
                                    sentencia.executeUpdate();
                                }
                            });
            assertThat(estado).isEqualTo("42501");
        }

        @Test
        @DisplayName("un UPDATE sobre filas de B afecta cero filas")
        void actualizarFilasDeBAfectaCeroFilas() throws SQLException {
            long grupoDeB = DatosDePrueba.grupoDe(base, municipalidadB);
            try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                ContextoDeTenant.fijar(app, municipalidadA);

                int porMunicipalidad =
                        actualizar(
                                app,
                                "UPDATE grupo SET descripcion = 'alterado'"
                                        + " WHERE municipalidad_id = "
                                        + municipalidadB);
                // El caso peligroso: el desarrollador olvida el filtro y usa solo el id.
                int porId =
                        actualizar(
                                app,
                                "UPDATE grupo SET descripcion = 'alterado' WHERE id = " + grupoDeB);

                assertThat(porMunicipalidad)
                        .as("UPDATE filtrando por la municipalidad ajena")
                        .isZero();
                assertThat(porId).as("UPDATE por id, sin filtro de municipalidad").isZero();
                app.rollback();
            }
        }

        @Test
        @DisplayName("una consulta sin contexto falla, no devuelve vacio")
        void consultaSinContextoFalla() {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String tabla : tablasDeTenant) {
                // Sin ContextoDeTenant.fijar: es exactamente el caso a detectar.
                String estado =
                        estadoSqlDelFallo(
                                () -> {
                                    try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                                        contar(app, "SELECT count(*) FROM " + tabla);
                                    }
                                });
                verificaciones
                        .assertThat(estado)
                        .as(
                                "%s: sin contexto la consulta debe fallar, no devolver vacio ni"
                                        + " devolver todo. Un error ruidoso es preferible a una fuga"
                                        + " silenciosa (RNF-032). Se admiten los dos codigos posibles;"
                                        + " el motivo esta en ContextoDeTenant.ESTADOS_SIN_CONTEXTO",
                                tabla)
                        .isIn(ContextoDeTenant.ESTADOS_SIN_CONTEXTO);
            }
            verificaciones.assertAll();
        }
    }

    // ------------------------------------------------------------------
    // c) Configuracion de roles
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("c) Configuracion de roles")
    class ConfiguracionDeRoles {

        @Test
        @DisplayName("ningun rol de aplicacion es superusuario ni tiene BYPASSRLS")
        void ningunRolDeAplicacionEsSuperusuarioNiOmiteRls() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String rol :
                    List.of(
                            BaseDeDatosDePrueba.APP,
                            BaseDeDatosDePrueba.READONLY,
                            // Entra aunque esta base no le de CONNECT: el rol es del CLUSTER y
                            // sus atributos —superusuario, BYPASSRLS— tambien, asi que si alguna
                            // vez naciera con BYPASSRLS lo pagarian las bases que si lo admiten.
                            // Aqui se lee `pg_roles` con la conexion de administrador, no se abre
                            // sesion como el.
                            BaseDeDatosDePrueba.CARGA_PARAMETROS,
                            BaseDeDatosDePrueba.OWNER)) {
                try (Connection admin = base.conexionAdmin();
                        PreparedStatement sentencia =
                                admin.prepareStatement(
                                        "SELECT rolsuper, rolbypassrls, rolcreatedb, rolcreaterole"
                                                + "  FROM pg_roles WHERE rolname = ?")) {
                    sentencia.setString(1, rol);
                    try (ResultSet fila = sentencia.executeQuery()) {
                        assertThat(fila.next()).as("el rol %s existe", rol).isTrue();
                        verificaciones
                                .assertThat(fila.getBoolean(1))
                                .as(
                                        "%s NO puede ser superusuario: un superusuario omite RLS"
                                                + " incluso con FORCE ROW LEVEL SECURITY",
                                        rol)
                                .isFalse();
                        verificaciones
                                .assertThat(fila.getBoolean(2))
                                .as("%s NO puede tener BYPASSRLS", rol)
                                .isFalse();
                        verificaciones
                                .assertThat(fila.getBoolean(3))
                                .as("%s sin CREATEDB", rol)
                                .isFalse();
                        verificaciones
                                .assertThat(fila.getBoolean(4))
                                .as("%s sin CREATEROLE", rol)
                                .isFalse();
                    }
                }
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("kamayuk_app no es propietario de ninguna tabla")
        void sgtmAppNoEsPropietarioDeNingunaTabla() throws SQLException {
            List<String> propias =
                    consultarTextos(
                            "SELECT c.relname FROM pg_class c"
                                    + "  JOIN pg_roles r ON r.oid = c.relowner"
                                    + "  JOIN pg_namespace n ON n.oid = c.relnamespace"
                                    + " WHERE n.nspname = 'public' AND r.rolname = 'kamayuk_app'");
            assertThat(propias)
                    .as("la aplicacion nunca se conecta como propietario (ARQ-03 §4)")
                    .isEmpty();
        }

        @Test
        @DisplayName("kamayuk_app no tiene DELETE en ninguna tabla")
        void sgtmAppNoTieneDeleteEnNingunaTabla() throws SQLException {
            List<String> conDelete = tablasConPrivilegio(BaseDeDatosDePrueba.APP, "DELETE");
            assertThat(conDelete)
                    .as(
                            "no se borra deuda, pagos, recibos, valores, papeletas, asientos ni"
                                    + " auditoria: se anula, se da de baja o se reversa (RNF-051)")
                    .isEmpty();
        }

        @Test
        @DisplayName("lo inmutable no admite escritura desde la aplicacion")
        void loInmutableNoAdmiteEscritura() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            verificaciones
                    .assertThat(tienePrivilegio(BaseDeDatosDePrueba.APP, "auditoria", "UPDATE"))
                    .as("quien puede modificar la auditoria puede borrar su rastro (ADR-0008)")
                    .isFalse();
            // Las dos que este sistema no escribe nunca, cada una por su motivo. `municipalidad`
            // la da de alta la implantacion como kamayuk_owner, y un INSERT desde la aplicacion
            // seria el camino mas corto de una pantalla de alta a una escalada entre
            // municipalidades; `respaldo` la escribe el proceso de despliegue, y una aplicacion que
            // pudiera declarar una copia hecha podria declarar hecha una que no existe.
            //
            // Las dos guardas de #435 son independientes y bastan por separado: el privilegio y la
            // politica, que nombra solo a kamayuk_owner. Aqui se mide LA DEL PRIVILEGIO, que es la
            // unica que `has_table_privilege` puede ver — con solo el GRANT devuelto, las pruebas
            // de sintoma siguen verdes porque RLS lo para igual y da el MISMO 42501.
            for (String deLaImplantacion : List.of("municipalidad", "respaldo")) {
                verificaciones
                        .assertThat(
                                tienePrivilegio(
                                        BaseDeDatosDePrueba.APP, deLaImplantacion, "INSERT"))
                        .as("%s: la escribe kamayuk_owner, no la aplicacion", deLaImplantacion)
                        .isFalse();
                verificaciones
                        .assertThat(
                                tienePrivilegio(
                                        BaseDeDatosDePrueba.APP, deLaImplantacion, "UPDATE"))
                        .as(
                                "%s: tampoco se corrige en el sitio desde la aplicacion",
                                deLaImplantacion)
                        .isFalse();
            }
            verificaciones.assertAll();
        }
    }

    // ------------------------------------------------------------------
    // d) Particiones
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("d) Particiones")
    class Particiones {

        @Test
        @DisplayName("kamayuk_app no tiene ningun privilegio sobre ninguna particion")
        void sgtmAppNoTieneNingunPrivilegioSobreParticiones() throws SQLException {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String particion : particiones) {
                for (String privilegio :
                        List.of(
                                "SELECT",
                                "INSERT",
                                "UPDATE",
                                "DELETE",
                                "TRUNCATE",
                                "REFERENCES",
                                "TRIGGER")) {
                    verificaciones
                            .assertThat(
                                    tienePrivilegio(BaseDeDatosDePrueba.APP, particion, privilegio))
                            .as(
                                    "%s sobre %s: el acceso directo a una particion evade la"
                                            + " politica del padre; los privilegios se conceden solo"
                                            + " sobre la tabla padre (ARQ-03 §3.5)",
                                    privilegio, particion)
                            .isFalse();
                }
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("el acceso directo a una particion falla")
        void accesoDirectoAUnaParticionFalla() {
            SoftAssertions verificaciones = new SoftAssertions();
            for (String particion : particiones) {
                String estado =
                        estadoSqlDelFallo(
                                () -> {
                                    try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                                        ContextoDeTenant.fijar(app, municipalidadA);
                                        contar(app, "SELECT count(*) FROM " + particion);
                                    }
                                });
                verificaciones
                        .assertThat(estado)
                        .as(
                                "%s: el acceso directo a una particion debe dar"
                                        + " InsufficientPrivilege. Es lo que cierra el hallazgo 2"
                                        + " de DAT-01 §0",
                                particion)
                        .isEqualTo("42501");
            }
            verificaciones.assertAll();
        }

        @Test
        @DisplayName("la lectura por la tabla padre sigue funcionando y filtra")
        void laLecturaPorElPadreFuncionaYFiltra() throws SQLException {
            try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                ContextoDeTenant.fijar(app, municipalidadA);
                assertThat(contar(app, "SELECT count(*) FROM auditoria"))
                        .as("quitar privilegios sobre las particiones no debe romper el acceso")
                        .isEqualTo(1);
                app.rollback();
            }
        }
    }

    // ------------------------------------------------------------------
    // La trampa que invalida esta prueba si se descuida
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("La trampa de la conexion por omision")
    class Trampa {

        @Test
        @DisplayName("el superusuario omite RLS: por eso esta prueba no usa esa conexion")
        void superusuarioOmiteRlsPorEsoLaPruebaNoUsaEsaConexion() throws SQLException {
            long vistasPorElSuperusuario;
            try (Connection admin = base.conexionAdmin()) {
                admin.setAutoCommit(false);
                ContextoDeTenant.fijar(admin, municipalidadA);
                vistasPorElSuperusuario = contar(admin, "SELECT count(*) FROM grupo");
                admin.rollback();
            }

            long vistasPorLaAplicacion;
            try (Connection app = base.conexion(BaseDeDatosDePrueba.APP)) {
                ContextoDeTenant.fijar(app, municipalidadA);
                vistasPorLaAplicacion = contar(app, "SELECT count(*) FROM grupo");
                app.rollback();
            }

            assertThat(vistasPorElSuperusuario)
                    .as(
                            "con el mismo contexto fijado, el superusuario ve las dos"
                                    + " municipalidades. Si esto alguna vez diera 1, seria porque el rol"
                                    + " dejo de ser superusuario, no porque la trampa desaparecio")
                    .isEqualTo(2);
            assertThat(vistasPorLaAplicacion)
                    .as("kamayuk_app ve solo la suya. Esta es la unica conexion que prueba algo")
                    .isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Accion que puede fallar contra la base. */
    @FunctionalInterface
    private interface AccionSql {
        void ejecutar() throws SQLException;
    }

    /**
     * Devuelve el {@code SQLSTATE} con el que fallo la accion, o {@code null} si no fallo. Que
     * devuelva {@code null} es en si mismo el fallo interesante: significa que la operacion que
     * debia ser rechazada tuvo exito.
     */
    private static String estadoSqlDelFallo(AccionSql accion) {
        try {
            accion.ejecutar();
            return null;
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    private static long contar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery(sql)) {
            fila.next();
            return fila.getLong(1);
        }
    }

    private static int actualizar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            return sentencia.executeUpdate(sql);
        }
    }

    private static List<String> consultarTextos(String sql) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement();
                ResultSet fila = sentencia.executeQuery(sql)) {
            while (fila.next()) {
                valores.add(fila.getString(1));
            }
        }
        return valores;
    }

    private static List<String> politicasDe(String tabla) throws SQLException {
        return consultarTextos(
                "SELECT policyname FROM pg_policies"
                        + " WHERE schemaname = 'public' AND tablename = "
                        + literal(tabla));
    }

    private static List<String> politicasCompletasDe(String tabla) throws SQLException {
        return consultarTextos(
                "SELECT policyname FROM pg_policies"
                        + " WHERE schemaname = 'public' AND tablename = "
                        + literal(tabla)
                        + "   AND qual IS NOT NULL AND with_check IS NOT NULL");
    }

    private static List<String> tablasConPrivilegio(String rol, String privilegio)
            throws SQLException {
        return consultarTextos(
                "SELECT c.relname FROM pg_class c"
                        + "  JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname = 'public' AND c.relkind IN ('r','p')"
                        + "   AND has_table_privilege("
                        + literal(rol)
                        + ", c.oid, "
                        + literal(privilegio)
                        + ")");
    }

    private static boolean tienePrivilegio(String rol, String tabla, String privilegio)
            throws SQLException {
        try (Connection admin = base.conexionAdmin();
                Statement sentencia = admin.createStatement();
                ResultSet fila =
                        sentencia.executeQuery(
                                "SELECT has_table_privilege("
                                        + literal(rol)
                                        + ", "
                                        + literal(tabla)
                                        + ", "
                                        + literal(privilegio)
                                        + ")")) {
            fila.next();
            return fila.getBoolean(1);
        }
    }

    /** Los nombres vienen del catalogo de PostgreSQL, no de una entrada de usuario. */
    private static String literal(String valor) {
        return "'" + valor.replace("'", "''") + "'";
    }
}
