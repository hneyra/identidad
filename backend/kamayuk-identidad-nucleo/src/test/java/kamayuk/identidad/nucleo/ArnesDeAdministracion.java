package kamayuk.identidad.nucleo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import kamayuk.identidad.auditoria.AuditoriaJdbc;
import kamayuk.identidad.auditoria.Origen;
import kamayuk.identidad.auditoria.OrigenContext;
import kamayuk.identidad.compartido.TenantContext;
import kamayuk.identidad.dominio.MunicipalidadId;
import kamayuk.identidad.esquema.BaseDeDatosDePrueba;
import kamayuk.identidad.nucleo.aplicacion.AdministrarPermisos;
import kamayuk.identidad.nucleo.aplicacion.AdministrarSeguridad;
import kamayuk.identidad.nucleo.aplicacion.EntregaDeEventos;
import kamayuk.identidad.nucleo.aplicacion.SembradorDelCatalogo;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.infraestructura.AdministracionRepositoryJdbc;
import kamayuk.identidad.nucleo.infraestructura.BuzonDeIdentidadJdbc;
import kamayuk.identidad.nucleo.infraestructura.PermisoRepositoryJdbc;
import kamayuk.identidad.plataforma.tenant.TenantTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El montaje que comparten las pruebas de este contexto: una base provisionada de verdad y los
 * casos de uso envueltos en su interceptor de transacciones.
 *
 * <h2>Por que existe, si en {@code rentas} cada prueba monta el suyo</h2>
 *
 * <p>Porque aqui son mas y montan lo mismo, y sobre todo porque el montaje tiene una parte que
 * <b>no puede variar entre pruebas</b>: el pool se conecta como {@code kamayuk_app} y los casos de
 * uso van detras de un {@link TransactionInterceptor} de verdad. Las dos cosas son lo que hace que
 * estas pruebas midan algo: sin la primera, el dueño de las tablas evade la politica RLS y el
 * aislamiento pasa en verde sin haberse ejercido (DAT-01 §0, hallazgo 1); sin la segunda no hay
 * {@code SET LOCAL}, y las consultas no devuelven vacio — <b>revientan</b>.
 *
 * <p>Se conecta como {@code kamayuk_app} y no como {@code kamayuk_owner} tambien por lo segundo: el
 * rol de la aplicacion no tiene {@code DELETE} en ninguna tabla ni {@code UPDATE} en {@code
 * identidad_evento}, asi que una escritura que no deberia poder hacerse falla aqui como fallaria en
 * produccion.
 */
public final class ArnesDeAdministracion implements AutoCloseable {

    /** El reloj de todas las pruebas: fijo, para que una fecha no dependa del dia que se corren. */
    public static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneId.of("America/Lima"));

    private final BaseDeDatosDePrueba base;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;
    private final AdministrarSeguridad administrar;
    private final AdministrarPermisos permisos;
    private final SembradorDelCatalogo sembrador;
    private final BuzonDeIdentidad buzon;
    private final EntregaDeEventos entrega;

    private ArnesDeAdministracion(BaseDeDatosDePrueba base) {
        this.base = base;

        DriverManagerDataSource pool = new DriverManagerDataSource();
        pool.setUrl(base.url());
        pool.setUsername(BaseDeDatosDePrueba.APP);
        pool.setPassword(base.clave(BaseDeDatosDePrueba.APP));

        this.jdbc = JdbcClient.create(pool);
        TenantTransactionManager gestor = new TenantTransactionManager(pool);
        this.transaccion = new TransactionTemplate(gestor);

        AuditoriaJdbc auditoria = new AuditoriaJdbc(jdbc, RELOJ);
        // El buzon se envuelve IGUAL que los casos de uso, y hoy eso no hace nada: no declara
        // ninguna transaccion, asi que `AnnotationTransactionAttributeSource` no encuentra nada y
        // el interceptor deja pasar la llamada. Se envuelve por lo que pasaria si manana la
        // declarara — que es justo lo que su javadoc dice que no hay que hacer.
        //
        // Medido, y por eso esta escrito: con el buzon SIN envolver, ponerle
        // `@Transactional(propagation = REQUIRES_NEW)` a `emitir` dejaba las diez pruebas de
        // `AdministrarPermisosTest` en VERDE —«retirar el ultimo permiso … no deja rastro»
        // incluida—, porque un `@Transactional` sobre un objeto que nadie proxifica es un
        // comentario. La propiedad que sostiene el AC-2 no la podia medir nadie.
        this.buzon = envolver(new BuzonDeIdentidadJdbc(jdbc, RELOJ), gestor);
        AdministracionRepositoryJdbc administracion = new AdministracionRepositoryJdbc(jdbc);
        this.administrar =
                envolver(new AdministrarSeguridad(administracion, auditoria, buzon, RELOJ), gestor);
        this.permisos =
                envolver(
                        new AdministrarPermisos(
                                new PermisoRepositoryJdbc(jdbc),
                                administracion,
                                auditoria,
                                buzon,
                                RELOJ),
                        gestor);
        this.sembrador = envolver(new SembradorDelCatalogo(jdbc, auditoria, RELOJ), gestor);

        // La etapa 3 sirve el buzon, y su caso de uso SI declara transaccion: sin envolverlo, la
        // consulta correria sin `SET LOCAL` y la politica RLS no devolveria vacio — reventaria con
        // «invalid input syntax for type bigint: ""». Es lo que su propio javadoc explica.
        this.entrega = envolver(new EntregaDeEventos(buzon), gestor);
    }

    public static ArnesDeAdministracion provisionar() throws SQLException, IOException {
        return new ArnesDeAdministracion(BaseDeDatosDePrueba.provisionar());
    }

    @SuppressWarnings("unchecked")
    private static <T> T envolver(T objetivo, TenantTransactionManager gestor) {
        ProxyFactory fabrica = new ProxyFactory(objetivo);
        fabrica.setProxyTargetClass(true);
        fabrica.addAdvice(
                new TransactionInterceptor(gestor, new AnnotationTransactionAttributeSource()));
        return (T) fabrica.getProxy();
    }

    public AdministrarSeguridad administrar() {
        return administrar;
    }

    public AdministrarPermisos permisos() {
        return permisos;
    }

    public SembradorDelCatalogo sembrador() {
        return sembrador;
    }

    public BuzonDeIdentidad buzon() {
        return buzon;
    }

    /** El caso de uso que sirve el buzon (etapa 3), con su interceptor de transacciones. */
    public EntregaDeEventos entrega() {
        return entrega;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    public TransactionTemplate transaccion() {
        return transaccion;
    }

    public BaseDeDatosDePrueba base() {
        return base;
    }

    /** El alta de una municipalidad la hace el owner: la aplicacion no tiene ese privilegio. */
    public long crearMunicipalidad(String ubigeo, String nombre) throws SQLException {
        try (Connection owner = base.conexion(BaseDeDatosDePrueba.OWNER);
                PreparedStatement sentencia =
                        owner.prepareStatement(
                                "INSERT INTO municipalidad (ubigeo, nombre, tipo)"
                                        + " VALUES (?, ?, 'DISTRITAL') RETURNING id")) {
            sentencia.setString(1, ubigeo);
            sentencia.setString(2, nombre);
            try (ResultSet resultado = sentencia.executeQuery()) {
                resultado.next();
                long id = resultado.getLong(1);
                owner.commit();
                return id;
            }
        }
    }

    /** Fija los dos contextos que en una peticion salen del token y del borde HTTP. */
    public static void entrarComo(long municipalidad, String cuenta) {
        TenantContext.fijar(new MunicipalidadId(municipalidad));
        OrigenContext.fijar(new Origen(cuenta, "PC-PRUEBA", "10.0.0.1"));
    }

    public static void salir() {
        OrigenContext.limpiar();
        TenantContext.limpiar();
    }

    /**
     * Consulta de una sola columna con la conexion de superusuario.
     *
     * <p>Superusuario a proposito: lo que estas consultas comprueban es lo que <b>quedo
     * escrito</b>, y hacerlo con la conexion de la aplicacion las dejaria sujetas a la misma
     * politica que se esta comprobando — una fuga se veria como una ausencia.
     */
    public List<String> filas(String sql) throws SQLException {
        try (Connection admin = base.conexionAdmin();
                PreparedStatement sentencia = admin.prepareStatement(sql);
                ResultSet resultado = sentencia.executeQuery()) {
            List<String> valores = new ArrayList<>();
            while (resultado.next()) {
                valores.add(resultado.getString(1));
            }
            return valores;
        }
    }

    public long contar(String sql) throws SQLException {
        return Long.parseLong(filas(sql).get(0));
    }

    @Override
    public void close() {
        base.close();
    }
}
