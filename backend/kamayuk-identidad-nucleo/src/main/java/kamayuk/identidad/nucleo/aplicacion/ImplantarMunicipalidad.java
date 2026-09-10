package kamayuk.identidad.nucleo.aplicacion;

import java.util.EnumSet;
import java.util.Set;
import kamayuk.identidad.auditoria.Origen;
import kamayuk.identidad.auditoria.OrigenContext;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.compartido.TenantContext;
import kamayuk.identidad.dominio.MunicipalidadId;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.Usuario;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import kamayuk.identidad.nucleo.infraestructura.RegistroDeMunicipalidadesJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Pone la municipalidad dentro de la base de {@code identidad}: sin esto no hay nada que
 * administrar.
 *
 * <h2>Lo que la etapa 2 cambia, y por que no es un refactor</h2>
 *
 * <p>Hasta la etapa 1 esta implantacion escribia el grupo de administracion, el administrador, su
 * afiliacion y sus permisos <b>con SQL directo</b>, desde el sembrador. Ahora los escribe llamando
 * a {@link AdministrarSeguridad} y {@link AdministrarPermisos}, que es el mismo camino por el que
 * pasa cualquier alta hecha desde una pantalla. Eso compra dos cosas que el SQL directo no puede
 * dar:
 *
 * <ol>
 *   <li><b>Emite sus eventos.</b> Los cuatro actos escriben en {@code identidad_evento} dentro de
 *       su propia transaccion, asi que el administrador y sus permisos llegan a los otros cuatro
 *       sistemas por el buzon. Es lo que hace posible el <b>arranque en frio</b> de la etapa 5: sin
 *       ellos, la unica cuenta que puede entrar el primer dia existiria solo aqui y ningun otro
 *       sistema la conoceria — el guardia de {@code caja} le negaria todo a quien acaba de
 *       implantar la municipalidad, y el sintoma seria un 403 sin causa visible.
 *   <li><b>Pasa por las mismas reglas.</b> La cuenta repetida, el nombre de grupo repetido, la
 *       observacion obligatoria (regla 10) y —la que importa— la guarda del <b>ultimo
 *       administrador</b>. Con SQL directo, la implantacion era el unico camino de escritura que
 *       las esquivaba todas.
 * </ol>
 *
 * <h2>El orden de los permisos no es libre: {@code permisos} va primero</h2>
 *
 * <p>{@code AdministrarPermisos} comprueba <b>despues</b> de cada escritura que quede algun usuario
 * capaz de administrar permisos, y en una municipalidad recien creada no hay ninguno. Si el primer
 * permiso que se fija fuera otro, esa comprobacion contaria cero y la implantacion entera se
 * desharia con un 409. Se fija primero {@code (identidad, permisos)} —con el administrador ya
 * afiliado al grupo— y a partir de ahi la cuenta es uno. Es el mismo orden que {@code rentas} usa
 * en su implantacion, y aqui ademas hay que decir <b>de que sistema</b> es esa opcion: {@code
 * permisos} tambien es una opcion de {@code rentas} y contar por el codigo solo daria por
 * administrador de este sistema a quien administra los permisos de otro.
 *
 * <h2>El hueco que cierra (C-6, hueco 3)</h2>
 *
 * <p>Cada sistema tiene <b>su propia base</b> (ADR-0032) y en cada una hay una tabla {@code
 * municipalidad} con su {@code es_demostracion}. {@code SoloEnDemostracion} la consulta <b>en la
 * base de su propio sistema</b>, y las politicas RLS resuelven {@code app.municipalidad_id} contra
 * ella. Hasta C-7, el unico {@code INSERT INTO municipalidad} del arbol de este repositorio estaba
 * en fixtures de prueba: una instalacion real no tenia como escribir esa fila.
 *
 * <h2>Por que un proceso y no un endpoint</h2>
 *
 * <p>Porque {@code municipalidad} solo la escribe {@code kamayuk_owner}. Un endpoint que lo hiciera
 * le exigiria a {@code kamayuk_app} un privilegio que se le quito a proposito, y seria el camino
 * mas corto de una pantalla de alta a una escalada entre municipalidades.
 *
 * <p>Corre en el perfil {@code batch}: sin servidor web, sin puerto expuesto y con vida corta. Las
 * credenciales de {@code kamayuk_owner} entran <b>solo</b> en el paso 1, para <b>un</b> {@code
 * INSERT}, en una conexion que se abre y se cierra. Todo lo demas va por el camino normal de la
 * aplicacion, como {@code kamayuk_app} y con su auditoria.
 *
 * <h2>Dos grupos, y el segundo nace con las cuatro cuentas de servicio dentro</h2>
 *
 * <p>{@code rentas} crea dos —administracion y {@code Seguridad}—; aqui el de administracion y,
 * desde la <b>etapa 3</b>, «Consumidores del buzon». El de {@code rentas} que no se copia es la
 * plantilla de quien administra el acceso de los usuarios <b>sin</b> poder administrar el resto, y
 * esa delegacion es una decision de la municipalidad: crearla vacia desde el despliegue seria
 * decidir por ella.
 *
 * <p>El grupo de administracion recibe los siete privilegios sobre las <b>157</b> opciones de los
 * cinco catalogos —la cifra que {@code CatalogoUnidoTest} mide—, que es lo que hace que el primer
 * dia haya alguien que pueda configurar todo lo demas.
 *
 * <p>«Consumidores del buzon» recibe <b>una sola opcion</b>, {@code (identidad, eventos)}, y solo
 * {@code LECTURA} y {@code REGISTRO} — que son exactamente los dos privilegios que {@code
 * EventosController} exige. No recibe {@code ELIMINACION} ni {@code ESPECIAL} porque no hay nada
 * que borrar: el buzon es inmutable y su acuse tambien.
 *
 * <p><b>Y desde la etapa 4 nace CON sus cuatro miembros</b>, que es lo contrario de lo que hacia la
 * etapa 3 y hay que decir por que cambio. Aquella lo dejaba vacio razonando que «a quien se afilia
 * lo decide quien despliegue»; medido el 2026-09-09 con las cinco aplicaciones levantadas, <b>no lo
 * decide nadie</b>: {@code reconciliar-identidades.sh servicios} crea el cliente confidencial de
 * cada satelite en el emisor —o sea que el consumidor <b>consigue su token</b>—, pero la cuenta
 * {@code service-account-kamayuk-<sistema>-servicio-<ubigeo>} no tiene fila en {@code usuario} de
 * esta base, asi que los cuatro reciben <b>403</b> «La cuenta «…» no esta dada de alta en este
 * sistema» y la pasada del consumidor de cada implantacion muere con {@code IdentidadNoContesta:
 * identidad contesto 403 al leer el buzon}. Para poder medir AC-6 hubo que darlas de alta <b>a
 * mano</b> con el token del administrador. Un grupo vacio no es «pendiente de que alguien decida»:
 * es la copia local de los cuatro sistemas congelada como la dejo su implantacion, <b>sin un solo
 * error que lo diga</b> — el defecto exacto que la etapa 4 existe para cerrar.
 *
 * <p>Lo que se afilia no es una eleccion de esta clase: son <b>los cuatro de {@link
 * Consumidor}</b>, que es el mismo enumerado del que sale quien puede leer el buzon y el que {@code
 * ConsumidorTest} ata al {@code CHECK} de {@code V3}. Derivarlo de {@code
 * SistemasDelProducto.TODOS} menos {@code identidad} habria sido una segunda lista con la misma
 * verdad, y la que se quedaria vieja seria justo la que da de alta las cuentas. <b>{@code
 * identidad} no esta</b>, y no por omision: este sistema no se consume a si mismo —lo que el buzon
 * publica es lo que esta misma base acaba de escribir—, asi que su cuenta de servicio no tendria
 * nada que leer y su acuse retiraria un evento que nadie ha aplicado.
 *
 * <p><b>Se dan de alta habilitadas y con {@link kamayuk.identidad.dominio.Vigencia#SIEMPRE}</b>, y
 * las dos cosas se deciden aqui. Habilitadas, porque una cuenta de servicio que naciera
 * deshabilitada dejaria la replica parada desde el primer minuto con el mismo sintoma mudo que esto
 * viene a cerrar. Y sin fecha de fin, porque una vigencia puesta por el despliegue caduca un dia
 * que nadie recuerda y lo que se para entonces son <b>los cuatro sistemas a la vez</b>, sin que
 * nadie haya decidido nada: retirarle el acceso a un consumidor es un acto —desafiliarlo del grupo,
 * o retirarle la credencial en el emisor—, con su observacion y su fila de auditoria, no un
 * temporizador. Es la misma razon por la que el administrador tampoco nace con vigencia.
 *
 * <h2>Idempotente, entera</h2>
 *
 * <p>Se ejecuta en cada despliegue. Lo que ya existe se queda como esta —con los permisos que
 * alguien haya configurado despues—, y lo que falta se crea. Nunca borra.
 *
 * <p><b>Las cuatro cuentas de servicio no son una excepcion</b>: la cuenta repetida la rechaza
 * {@code registrarUsuario} con {@code CuentaRepetida} y aqui eso no es un error sino el segundo
 * despliegue —se atrapa y se lee la que hay, igual que con el administrador—, y {@code afiliar} es
 * un {@code ON CONFLICT … DO UPDATE} que <b>reactiva</b> la fila. Reafiliar a proposito y no
 * saltarselo si ya esta: es lo que hace que un despliegue repare a un consumidor al que alguien
 * desafilio, que es la unica forma que tiene de repararse. Lo que cuesta esta dicho y es lo mismo
 * que ya costaba la afiliacion del administrador: <b>el estado no crece y los eventos si</b>
 * —repetir la afiliacion es otro acto, con su observacion—, y aplicar dos veces un {@code
 * MIEMBRO_AFILIADO} en la copia de un satelite es idempotente porque alli tambien es un {@code
 * upsert}.
 */
@Component
@Profile("batch")
@ConditionalOnProperty("kamayuk.implantacion.ubigeo")
@EnableConfigurationProperties(DatosDeImplantacion.class)
public class ImplantarMunicipalidad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImplantarMunicipalidad.class);

    /** El grupo del que cuelgan los permisos del primer administrador. */
    public static final String GRUPO_DE_ADMINISTRACION = "Administracion del sistema";

    /**
     * El grupo del que cuelga el permiso de las cuatro cuentas de servicio (etapa 3).
     *
     * <p>Desde la etapa 4 nace <b>con sus cuatro miembros</b>: ver el epigrafe de la cabecera.
     */
    public static final String GRUPO_DE_CONSUMIDORES = "Consumidores del buzon";

    /** La opcion con la que se lee y se acusa el buzon, y de que sistema es. */
    public static final String ACCESO_DEL_BUZON = "eventos";

    /**
     * La opcion que gobierna la propia administracion de permisos, y de que sistema es.
     *
     * <p>Se fija <b>la primera</b>: ver el epigrafe del orden en la cabecera de esta clase.
     */
    public static final String ACCESO_DE_ADMINISTRACION = "permisos";

    /** Los siete, que es lo que un administrador tiene sobre todo. */
    private static final Set<Privilegio> LOS_SIETE = EnumSet.allOf(Privilegio.class);

    /**
     * Los dos que {@code EventosController} exige, y ninguno mas.
     *
     * <p>{@code LECTURA} para servir la cola y {@code REGISTRO} para acusar. No hay {@code
     * ELIMINACION} porque no hay nada que borrar —el buzon es inmutable y su acuse tambien— ni
     * {@code ESPECIAL}, que es lo que en este producto abre las operaciones fuera de lo corriente.
     */
    private static final Set<Privilegio> LEER_Y_ACUSAR =
            EnumSet.of(Privilegio.LECTURA, Privilegio.REGISTRO);

    private final RegistroDeMunicipalidadesJdbc registro;
    private final SembradorDelCatalogo sembrador;
    private final AdministrarSeguridad administrar;
    private final AdministrarPermisos permisos;
    private final DatosDeImplantacion datos;

    public ImplantarMunicipalidad(
            RegistroDeMunicipalidadesJdbc registro,
            SembradorDelCatalogo sembrador,
            AdministrarSeguridad administrar,
            AdministrarPermisos permisos,
            DatosDeImplantacion datos) {
        this.registro = registro;
        this.sembrador = sembrador;
        this.administrar = administrar;
        this.permisos = permisos;
        this.datos = datos;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        long municipalidadId =
                registro.darDeAltaSiFalta(
                        datos.ubigeo(), datos.nombre(), datos.tipo(), datos.esDemostracion());

        // El perfil batch no tiene filtros HTTP, asi que los dos contextos que en una peticion
        // salen del token se fijan aqui a mano. `Origen.deProceso` existe para esto: una escritura
        // sin peticion detras, que aun asi tiene que decir quien.
        TenantContext.fijar(new MunicipalidadId(municipalidadId));
        OrigenContext.fijar(Origen.deProceso(datos.usuarioDelProceso()));
        try {
            CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();
            Observacion porQue =
                    Observacion.de(
                            "Implantacion de la municipalidad "
                                    + datos.ubigeo()
                                    + " en identidad (despliegue)");

            int nuevos = sembrador.sembrar(catalogo, porQue);
            int otorgados = darDeAltaAlAdministrador(catalogo, porQue);
            int consumidores = grupoDeConsumidoresDelBuzon(porQue);

            // El regimen se registra aunque sea una sola palabra: es lo unico del resultado que no
            // se puede comprobar mirando pantallas. Una instalacion que se creia de demostracion y
            // salio real emite papeles sin marca, y quien lo descubre es quien recibe uno (#122).
            log.info(
                    "Municipalidad {} lista en identidad ({}): id {}, {} accesos nuevos de los {}"
                            + " del catalogo unido, {} permisos otorgados al grupo '{}',"
                            + " administrador '{}', {} cuentas de servicio afiliadas a '{}'",
                    datos.ubigeo(),
                    datos.esDemostracion() ? "DEMOSTRACION" : "instalacion real",
                    municipalidadId,
                    nuevos,
                    catalogo.opciones().size(),
                    otorgados,
                    GRUPO_DE_ADMINISTRACION,
                    datos.administrador(),
                    consumidores,
                    GRUPO_DE_CONSUMIDORES);
        } finally {
            OrigenContext.limpiar();
            TenantContext.limpiar();
        }
    }

    /**
     * El grupo, el administrador, su afiliacion y sus permisos, <b>por el camino de siempre</b>.
     *
     * <p>Cada uno de los cuatro actos es una transaccion propia y emite su evento. No van en una
     * sola a proposito: {@code AdministrarPermisos} comprueba el ultimo administrador <b>dentro</b>
     * de la suya, asi que envolverlo todo en una transaccion mayor haria que esa comprobacion
     * mirara un estado que todavia no esta confirmado — y sobre todo, dejaria 160 permisos y sus
     * 160 eventos colgando de un solo {@code COMMIT} que en una municipalidad grande no termina.
     *
     * <p>Lo que cuesta esta dicho: una implantacion interrumpida a mitad deja la municipalidad con
     * parte de los permisos. Se acepta porque es <b>idempotente</b> —volver a correrla completa lo
     * que falta y no repite lo que hay— y porque la alternativa, una transaccion unica, cambia un
     * estado parcial reparable por uno irreparable a base de tiempos de espera.
     *
     * @return cuantos permisos quedaron otorgados
     */
    private int darDeAltaAlAdministrador(CatalogoUnido catalogo, Observacion porQue) {
        Grupo grupo = grupoDeAdministracion(porQue);
        long grupoId = exigirIdentificador(grupo.id(), "grupo");

        Usuario administrador = administrador(porQue);
        long usuarioId = exigirIdentificador(administrador.id(), "usuario");

        administrar.afiliar(grupoId, usuarioId, porQue);

        // PRIMERO la opcion que gobierna esta misma pantalla, y de ESTE sistema: mientras no este
        // otorgada no hay ningun administrador, y la guarda de `AdministrarPermisos` rechazaria
        // cualquier otro permiso con un 409 que hablaria de lo contrario de lo que pasa.
        permisos.fijarParaGrupo(
                grupoId,
                SistemasDelProducto.IDENTIDAD,
                ACCESO_DE_ADMINISTRACION,
                LOS_SIETE,
                porQue);
        int otorgados = 1;

        for (CatalogoUnido.Opcion opcion : catalogo.opciones()) {
            if (esLaDeAdministracion(opcion)) {
                continue;
            }
            permisos.fijarParaGrupo(grupoId, opcion.sistema(), opcion.codigo(), LOS_SIETE, porQue);
            otorgados++;
        }
        return otorgados;
    }

    private static boolean esLaDeAdministracion(CatalogoUnido.Opcion opcion) {
        return SistemasDelProducto.IDENTIDAD.equals(opcion.sistema())
                && ACCESO_DE_ADMINISTRACION.equals(opcion.codigo());
    }

    /**
     * El grupo desde el que los cuatro sistemas leen el buzon, con su unica opcion y sus cuatro
     * cuentas de servicio.
     *
     * <p>Va <b>despues</b> del administrador y no antes, y el orden no es libre: {@code
     * AdministrarPermisos} comprueba tras cada escritura que quede alguien capaz de administrar
     * permisos, y en una municipalidad recien creada eso solo es cierto una vez que el grupo de
     * administracion tiene su {@code (identidad, permisos)}. Puesto antes, este {@code
     * fijarParaGrupo} seria el primero de la municipalidad y se rechazaria con un 409 que hablaria
     * de lo contrario de lo que pasa.
     *
     * <p>Y el permiso va antes que los miembros por lo mismo que el grupo va antes que el permiso:
     * afiliar a un grupo que todavia no puede leer el buzon dejaria una ventana —corta, dentro de
     * la misma implantacion— en la que las cuatro cuentas existen y no autorizan. No cuesta nada
     * ponerlo en este orden y ahorra tener que razonar sobre esa ventana.
     *
     * @return cuantas cuentas de servicio quedaron afiliadas
     */
    private int grupoDeConsumidoresDelBuzon(Observacion porQue) {
        Grupo grupo;
        try {
            grupo =
                    administrar.registrarGrupo(
                            Grupo.nuevo(
                                    GRUPO_DE_CONSUMIDORES,
                                    "Creado por la implantacion: desde aqui los cuatro sistemas"
                                            + " leen y acusan el buzon de identidad. Sus miembros"
                                            + " son cuentas de servicio, no personas"),
                            porQue);
        } catch (AdministrarSeguridad.GrupoRepetido yaEstaba) {
            grupo = administrar.grupoPorNombre(GRUPO_DE_CONSUMIDORES).orElseThrow(() -> yaEstaba);
        }
        long grupoId = exigirIdentificador(grupo.id(), "grupo");
        permisos.fijarParaGrupo(
                grupoId, SistemasDelProducto.IDENTIDAD, ACCESO_DEL_BUZON, LEER_Y_ACUSAR, porQue);

        int afiliadas = 0;
        for (Consumidor consumidor : Consumidor.values()) {
            Usuario cuenta = cuentaDeServicio(consumidor, porQue);
            administrar.afiliar(grupoId, exigirIdentificador(cuenta.id(), "usuario"), porQue);
            afiliadas++;
        }
        return afiliadas;
    }

    /**
     * La fila de {@code usuario} de una cuenta de servicio, o la que ya estaba.
     *
     * <p>Es un alta como cualquier otra —pasa por {@link AdministrarSeguridad}, exige su
     * observacion, asienta auditoria y <b>emite su evento</b>— y eso ultimo es deliberado: los
     * cuatro satelites reciben por el buzon el alta y la afiliacion de las cuatro cuentas, igual
     * que reciben las del administrador. No sobra: cada uno autoriza contra <b>su</b> copia local,
     * asi que el dia que una operacion de un satelite la pida un proceso suyo con esta cuenta, su
     * guardia tiene que conocerla — y sobre todo, una copia que no las tiene y una que si son
     * distinguibles, que es lo que hace comprobable el arranque en frio.
     *
     * <p><b>Sin correo y sin {@code sujetoOidc}</b>: no hay persona detras a la que escribir, y el
     * enlace con el emisor lo hace la cuenta, que es justamente lo que se compone aqui.
     */
    private Usuario cuentaDeServicio(Consumidor consumidor, Observacion porQue) {
        String cuenta = consumidor.cuentaDeServicio(datos.ubigeo());
        try {
            return administrar.registrarUsuario(
                    Usuario.nuevo(
                            cuenta,
                            "Cuenta de servicio de " + consumidor.sistema() + " (buzon)",
                            null),
                    porQue);
        } catch (AdministrarSeguridad.CuentaRepetida yaEstaba) {
            return administrar.usuarioPorCuenta(cuenta).orElseThrow(() -> yaEstaba);
        }
    }

    /**
     * El grupo, o el que ya estaba.
     *
     * <p>{@code registrarGrupo} rechaza el nombre repetido con {@code GrupoRepetido} —es lo que
     * hace que el alta por pantalla nombre el choque en vez de dejar salir un error de clave—, y
     * aqui eso no es un error: es el segundo despliegue. Se atrapa y se lee el que hay.
     */
    private Grupo grupoDeAdministracion(Observacion porQue) {
        try {
            return administrar.registrarGrupo(
                    Grupo.nuevo(
                            GRUPO_DE_ADMINISTRACION,
                            "Creado por la implantacion: administra este sistema entero"),
                    porQue);
        } catch (AdministrarSeguridad.GrupoRepetido yaEstaba) {
            return administrar.grupoPorNombre(GRUPO_DE_ADMINISTRACION).orElseThrow(() -> yaEstaba);
        }
    }

    /**
     * El primer administrador, como fila.
     *
     * <p>{@code cuenta} tiene que coincidir con el {@code preferred_username} del token: es lo
     * unico que une esta fila con la identidad de Keycloak, y si no coinciden el usuario entra y no
     * es nadie. <b>No se crea ninguna clave</b>: el sistema no guarda contrasenas ni las transporta
     * (ADR-0005).
     */
    private Usuario administrador(Observacion porQue) {
        try {
            return administrar.registrarUsuario(
                    Usuario.nuevo(datos.administrador(), datos.nombreDelAdministrador(), null),
                    porQue);
        } catch (AdministrarSeguridad.CuentaRepetida yaEstaba) {
            return administrar.usuarioPorCuenta(datos.administrador()).orElseThrow(() -> yaEstaba);
        }
    }

    private static long exigirIdentificador(
            @org.jspecify.annotations.Nullable Long id, String que) {
        if (id == null) {
            throw new IllegalStateException(
                    "El " + que + " se guardo y volvio sin identificador, que no puede pasar");
        }
        return id;
    }
}
