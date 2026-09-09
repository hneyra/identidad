package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.dominio.Vigencia;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.TipoDeEventoDeIdentidad;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-3: los siete tipos son suficientes, y se mide <b>reconstruyendo</b>.
 *
 * <h2>Como esta montada, y por que asi</h2>
 *
 * <p>Dos bases de verdad en el mismo motor, cada una con el <b>mismo DDL</b> aplicado por el mismo
 * migrador. En la primera se ejercen las <b>once escrituras</b> con datos variados; de la segunda
 * no se toca nada salvo por el {@link AplicadorDeReferencia}, que solo ve los eventos. Despues se
 * comparan las cuatro tablas <b>columna de negocio a columna de negocio</b>.
 *
 * <p><b>Dos bases y no dos municipalidades de la misma</b>, aunque habria sido mas barato: con dos
 * inquilinos, un aplicador que se olvidara de fijar el contexto escribiria en el del emisor y la
 * comparacion cuadraria igual. Con dos bases eso no puede pasar — y ademas es lo que hay en
 * produccion, donde cada sistema tiene la suya (ADR-0032).
 *
 * <p><b>Sin los identificadores internos</b>, y es una afirmacion y no una comodidad: cada base
 * tiene sus propias secuencias, asi que el grupo que aqui es el 3 alli es otro. Lo que se compara
 * es la clave natural y el estado, que es lo unico que significa lo mismo en las dos.
 *
 * <h2>Lo que esta prueba NO cubre, dicho aqui</h2>
 *
 * <p>La copia es una base con el <b>esquema de {@code identidad}</b>, o sea con las 160 opciones de
 * los cinco catalogos sembradas. En la etapa 4 el consumidor es {@code caja}, que en su base solo
 * tiene sus tres: un {@code PERMISO_FIJADO} sobre una opcion de {@code rentas} no tendra fila de
 * {@code acceso} donde colgarse. Que hace cada consumidor con los permisos que no son suyos es una
 * decision de esa etapa, y aqui se declara en vez de darse por resuelta.
 */
@DisplayName("AC-3 — los siete tipos reconstruyen las cuatro tablas")
class ReconstruccionDesdeElBuzonTest {

    private static final Set<Privilegio> LOS_SIETE = EnumSet.allOf(Privilegio.class);
    private static final Set<Privilegio> SOLO_LECTURA = EnumSet.of(Privilegio.LECTURA);
    private static final Set<Privilegio> NINGUNO = EnumSet.noneOf(Privilegio.class);

    private static ArnesDeAdministracion emisor;
    private static ArnesDeAdministracion copia;
    private static long municipalidadDelEmisor;
    private static long municipalidadDeLaCopia;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        emisor = ArnesDeAdministracion.provisionar();
        copia = ArnesDeAdministracion.provisionar();
        municipalidadDelEmisor = emisor.crearMunicipalidad("260101", "Emisora");
        municipalidadDeLaCopia = copia.crearMunicipalidad("260101", "Copia");

        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();
        sembrarCatalogo(emisor, municipalidadDelEmisor, catalogo);
        sembrarCatalogo(copia, municipalidadDeLaCopia, catalogo);
    }

    @AfterAll
    static void cerrar() {
        if (emisor != null) {
            emisor.close();
        }
        if (copia != null) {
            copia.close();
        }
    }

    private static void sembrarCatalogo(
            ArnesDeAdministracion arnes, long municipalidad, CatalogoUnido catalogo) {
        ArnesDeAdministracion.entrarComo(municipalidad, "despliegue");
        try {
            arnes.sembrador()
                    .sembrar(catalogo, Observacion.de("Siembra del catalogo unido de la prueba"));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    @Test
    @DisplayName("las once escrituras se reconstruyen enteras, tabla a tabla")
    void lasOnceEscriturasSeReconstruyen() throws SQLException {
        ejercerLasOnceEscrituras();

        List<EventoDeIdentidad> eventos = loQueSalioDelBuzon();

        // El sujeto. Sin esta afirmacion, las cuatro comparaciones de abajo se cumplirian sobre
        // dos bases igual de vacias: la copia no tendria nada porque no llego ningun evento, y el
        // emisor tampoco porque no se escribio nada. Es el mismo centinela que C-15/C-16.
        assertThat(eventos)
                .as("las once escrituras tienen que haber dejado eventos que aplicar")
                .isNotEmpty();
        aplicarEnLaCopia(eventos);

        assertThat(estadoDe(copia, USUARIOS))
                .as(
                        "tabla `usuario`: la copia no quedo como el emisor. Si falta una fila, un"
                                + " tipo de alta dejo de emitirse; si difiere una columna, el cuerpo"
                                + " del evento no lleva la fila ENTERA tal como quedo")
                .isEqualTo(estadoDe(emisor, USUARIOS));

        assertThat(estadoDe(copia, GRUPOS))
                .as("tabla `grupo`: la copia no quedo como el emisor")
                .isEqualTo(estadoDe(emisor, GRUPOS));

        assertThat(estadoDe(copia, MIEMBROS))
                .as(
                        "tabla `miembro`: la copia no quedo como el emisor. Aqui la baja es lo que"
                                + " mas cuesta: una desafiliacion que no se emita deja al consumidor"
                                + " con la pertenencia ACTIVA, o sea con un permiso que ya se retiro")
                .isEqualTo(estadoDe(emisor, MIEMBROS));

        assertThat(estadoDe(copia, PERMISOS))
                .as(
                        "tabla `permiso`: la copia no quedo como el emisor. Si sobra un privilegio,"
                                + " el cuerpo no lleva los siete y la retirada no viaja")
                .isEqualTo(estadoDe(emisor, PERMISOS));

        // Y el centinela va DETRAS de las cuatro comparaciones, a proposito. Va detras porque
        // existe para que un verde no sea vacio, no para explicar un rojo: mientras alguna
        // comparacion falle, lo que hay que leer es en que fila difieren las dos bases. Medido:
        // con la emision de `registrarUsuario` quitada y el centinela DELANTE, el unico rojo era
        // «could not find USUARIO_DADO_DE_ALTA» y las cuatro comparaciones no llegaban a correr,
        // o sea que el entregable del AC-3 —que la copia queda como el emisor— no se medía.
        assertThat(eventos.stream().map(EventoDeIdentidad::tipo).distinct().toList())
                .as(
                        "los SIETE tipos tienen que haberse ejercido. Si uno no aparece, lo que la"
                                + " comparacion de arriba demuestra es que los OTROS SEIS bastan — y"
                                + " esa no es la pregunta del AC-3")
                .containsExactlyInAnyOrder(TipoDeEventoDeIdentidad.values());
    }

    /**
     * Las once, con datos variados.
     *
     * <p>Variados a proposito: dos usuarios y dos grupos —para que una comparacion por una sola
     * fila no pase por casualidad—, un correo nulo y otro no, una descripcion nula, vigencias
     * abiertas y cerradas, una afiliacion que se queda y otra que se deshace, y tres matrices de
     * permisos: los siete a un grupo, uno solo a otro, y una excepcion de usuario que <b>retira
     * todo</b>. La ultima es la que mas importa: es la unica forma de distinguir «se le nego
     * expresamente» de «nunca lo tuvo».
     */
    private void ejercerLasOnceEscrituras() {
        ArnesDeAdministracion.entrarComo(municipalidadDelEmisor, "admin.emisor");
        try {
            AdministrarSeguridad administrar = emisor.administrar();
            AdministrarPermisos permisos = emisor.permisos();

            // 1. alta de grupo (x2)
            Grupo mesa =
                    administrar.registrarGrupo(
                            Grupo.nuevo("Mesa de Partes", "Recibe y deriva expedientes"),
                            Observacion.de("Alta del grupo de Mesa de Partes"));
            Grupo caja =
                    administrar.registrarGrupo(
                            Grupo.nuevo("Caja", null),
                            Observacion.de("Alta del grupo de la ventanilla de caja"));

            // 2. alta de usuario (x2)
            var jperez =
                    administrar.registrarUsuario(
                            kamayuk.identidad.nucleo.dominio.Usuario.nuevo(
                                    "jperez", "Juan Perez", "jperez@muni.gob.pe"),
                            Observacion.de("Alta de Juan Perez segun memorando 2026-11"));
            var mlopez =
                    administrar.registrarUsuario(
                            kamayuk.identidad.nucleo.dominio.Usuario.nuevo(
                                    "mlopez", "Maria Lopez", null),
                            Observacion.de("Alta de Maria Lopez sin correo declarado"));

            // 3. afiliar (x3) y 4. desafiliar (x1)
            administrar.afiliar(
                    mesa.id(), jperez.id(), Observacion.de("Se incorpora a Mesa de Partes"));
            administrar.afiliar(caja.id(), jperez.id(), Observacion.de("Refuerza la ventanilla"));
            administrar.afiliar(
                    mesa.id(), mlopez.id(), Observacion.de("Se incorpora a Mesa de Partes"));
            administrar.desafiliar(
                    caja.id(), jperez.id(), Observacion.de("Sale de caja por rotacion de areas"));

            // 5. baja de grupo, 6. reactivacion de grupo, 7. vigencia de grupo
            administrar.inhabilitarGrupo(
                    caja.id(), Observacion.de("Se suspende el grupo por arqueo extraordinario"));
            administrar.habilitarGrupo(
                    caja.id(), Observacion.de("Terminado el arqueo, el grupo vuelve"));
            administrar.fijarVigenciaDeGrupo(
                    caja.id(),
                    new Vigencia(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                    Observacion.de("El grupo de caja caduca con el ejercicio"));

            // 8. baja de usuario, 9. reactivacion de usuario, 10. vigencia de usuario
            administrar.inhabilitarUsuario(
                    mlopez.id(), Observacion.de("Licencia sin goce de haber por tres meses"));
            administrar.habilitarUsuario(
                    mlopez.id(), Observacion.de("Se reincorpora terminada la licencia"));
            administrar.fijarVigenciaDeUsuario(
                    jperez.id(),
                    new Vigencia(null, LocalDate.of(2026, 6, 30)),
                    Observacion.de("Fin de contrato segun resolucion 2026-90"));

            // 11. las dos matrices de permisos
            permisos.fijarParaGrupo(
                    mesa.id(),
                    SistemasDelProducto.IDENTIDAD,
                    "permisos",
                    LOS_SIETE,
                    Observacion.de("Mesa de Partes administra los permisos de la municipalidad"));
            permisos.fijarParaGrupo(
                    caja.id(),
                    "caja",
                    "caja_tributaria",
                    SOLO_LECTURA,
                    Observacion.de("La ventanilla solo consulta la caja tributaria"));
            permisos.fijarParaUsuario(
                    mlopez.id(),
                    "rentas",
                    "contribuyentes",
                    NINGUNO,
                    Observacion.de(
                            "Se le niega expresamente el padron mientras dure la investigacion"));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private List<EventoDeIdentidad> loQueSalioDelBuzon() {
        ArnesDeAdministracion.entrarComo(municipalidadDelEmisor, "publicador");
        try {
            return emisor.transaccion().execute(estado -> emisor.buzon().pendientesDesde(0, 1_000));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private void aplicarEnLaCopia(List<EventoDeIdentidad> eventos) {
        ArnesDeAdministracion.entrarComo(municipalidadDeLaCopia, "consumidor");
        try {
            copia.transaccion()
                    .execute(
                            estado -> {
                                new AplicadorDeReferencia(copia.jdbc()).aplicar(eventos);
                                return null;
                            });
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    // ------------------------------------------------------------------ el estado comparable

    private static final String USUARIOS =
            "SELECT cuenta || '|' || nombre || '|' || coalesce(correo, '') || '|' || habilitado"
                    + " || '|' || coalesce(vigencia_desde::text, '') || '|'"
                    + " || coalesce(vigencia_hasta::text, '')"
                    + " FROM usuario ORDER BY cuenta";

    private static final String GRUPOS =
            "SELECT nombre || '|' || coalesce(descripcion, '') || '|' || habilitado"
                    + " || '|' || coalesce(vigencia_desde::text, '') || '|'"
                    + " || coalesce(vigencia_hasta::text, '')"
                    + " FROM grupo ORDER BY nombre";

    // `fecha_baja` se compara como «esta o no esta» y no por su valor: lo pone `now()` en cada
    // base, asi que exigir el mismo instante mediria dos relojes y no dos estados.
    private static final String MIEMBROS =
            "SELECT g.nombre || '|' || u.cuenta || '|' || m.activo || '|' || m.usuario_alta"
                    + " || '|' || coalesce(m.usuario_baja, '')"
                    + " || '|' || (m.fecha_baja IS NOT NULL)"
                    + " FROM miembro m JOIN grupo g ON g.id = m.grupo_id"
                    + " JOIN usuario u ON u.id = m.usuario_id ORDER BY 1";

    private static final String PERMISOS =
            "SELECT coalesce(g.nombre, u.cuenta) || '|' || (p.grupo_id IS NOT NULL)"
                    + " || '|' || a.sistema || '|' || a.codigo || '|' || p.usuario_registro"
                    + " || '|' || p.ejecucion || p.lectura || p.registro || p.modificacion"
                    + " || p.eliminacion || p.impresion || p.especial"
                    + " FROM permiso p JOIN acceso a ON a.id = p.acceso_id"
                    + " LEFT JOIN grupo g ON g.id = p.grupo_id"
                    + " LEFT JOIN usuario u ON u.id = p.usuario_id ORDER BY 1";

    /**
     * Lo que hay escrito, leido con la conexion de <b>superusuario</b>.
     *
     * <p>Superusuario a proposito: lo que se compara es lo que quedo en la tabla, y leerlo con la
     * conexion de la aplicacion lo dejaria sujeto a la misma politica RLS que estas dos bases
     * ejercen — una fuga se veria como una ausencia y la comparacion cuadraria.
     *
     * <p>Sin filtro por municipalidad, y se puede: cada una de las dos bases tiene <b>una sola</b>
     * (se crea en {@code provisionar}). Anadir el filtro no protegeria de nada y esconderia una
     * segunda municipalidad que apareciera por error.
     */
    private static List<String> estadoDe(ArnesDeAdministracion arnes, String sql)
            throws SQLException {
        return arnes.filas(sql);
    }
}
