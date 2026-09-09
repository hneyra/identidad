package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.PermisoEfectivo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.Usuario;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import kamayuk.identidad.web.ProblemaDeNegocio;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RF-121 contra PostgreSQL real: otorgar, retirar, la precedencia y el ultimo administrador.
 *
 * <p>Viene de {@code rentas} con las once escrituras. <b>Lo que cambia es que un acceso se resuelve
 * por el par {@code (sistema, codigo)}</b>, y eso no es una firma mas larga: {@code permisos} es
 * una opcion de este sistema <b>y otra</b> de {@code rentas}, asi que resolver por el codigo solo
 * autorizaria contra el catalogo de otro — y en la guarda del ultimo administrador eso significa
 * dar por administrador de {@code identidad} a quien administra los permisos de otro sistema.
 */
@DisplayName("RF-121 — Permisos y niveles de accesibilidad")
class AdministrarPermisosTest {

    private static final Set<Privilegio> LOS_SIETE = EnumSet.allOf(Privilegio.class);
    private static final Set<Privilegio> NINGUNO = EnumSet.noneOf(Privilegio.class);
    private static final String OPCION_DE_ADMINISTRACION = "permisos";

    private static ArnesDeAdministracion arnes;
    private static long municipalidad;
    private static CatalogoUnido catalogo;

    /** Quien sostiene la guarda del ultimo administrador mientras las pruebas mueven permisos. */
    private static long grupoAdministrador;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        municipalidad = arnes.crearMunicipalidad("280101", "Permisos");
        catalogo = CatalogoUnidoDelJar.leer();

        ArnesDeAdministracion.entrarComo(municipalidad, "despliegue");
        try {
            arnes.sembrador()
                    .sembrar(catalogo, Observacion.de("Siembra del catalogo unido de la prueba"));

            // El administrador de guardia. Sin el, la PRIMERA fijacion de permisos de cualquier
            // prueba se rechazaria con 409 —«el cambio dejaria a la municipalidad sin ningun
            // usuario capaz de administrar permisos»— y el rojo hablaria de otra cosa.
            Grupo administradores =
                    arnes.administrar()
                            .registrarGrupo(
                                    Grupo.nuevo("Administradores", null),
                                    Observacion.de("El grupo que sostiene la guarda"));
            Usuario titular =
                    arnes.administrar()
                            .registrarUsuario(
                                    Usuario.nuevo(
                                            "admin.guardia", "Administrador de guardia", null),
                                    Observacion.de("La cuenta que administra los permisos"));
            arnes.administrar()
                    .afiliar(
                            administradores.id(),
                            titular.id(),
                            Observacion.de("Entra al grupo de administradores"));
            arnes.permisos()
                    .fijarParaGrupo(
                            administradores.id(),
                            SistemasDelProducto.IDENTIDAD,
                            OPCION_DE_ADMINISTRACION,
                            LOS_SIETE,
                            Observacion.de("Administra los permisos de esta municipalidad"));
            grupoAdministrador = administradores.id();
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    @BeforeEach
    void fijarContexto() {
        ArnesDeAdministracion.entrarComo(municipalidad, "admin.guardia");
    }

    @AfterEach
    void limpiarContexto() {
        ArnesDeAdministracion.salir();
    }

    @Nested
    @DisplayName("Otorgar y retirar deja la matriz que se pidio")
    class OtorgarYRetirar {

        @Test
        @DisplayName("los siete privilegios, uno a uno: se otorgan y se retiran")
        void losSietePrivilegiosUnoAUno() {
            Grupo grupo = grupo("Siete privilegios");

            for (Privilegio privilegio : Privilegio.values()) {
                arnes.permisos()
                        .fijarParaGrupo(
                                grupo.id(),
                                "caja",
                                "caja_tributaria",
                                EnumSet.of(privilegio),
                                Observacion.de("Se otorga " + privilegio + " sobre la caja"));

                assertThat(
                                arnes.permisos().deGrupo(grupo.id()).stream()
                                        .filter(p -> "caja_tributaria".equals(p.codigoDeAcceso()))
                                        .flatMap(p -> p.privilegios().stream())
                                        .toList())
                        .as(
                                "un solo metodo para otorgar y para retirar, con el conjunto"
                                        + " COMPLETO: lo que no esta en el conjunto se retira")
                        .containsExactly(privilegio);
            }
        }

        @Test
        @DisplayName("y la matriz leida dice de que sistema es cada opcion")
        void laMatrizDiceDeQueSistema() {
            Grupo grupo = grupo("Con dos sistemas");
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "caja",
                            "caja_tributaria",
                            EnumSet.of(Privilegio.LECTURA),
                            Observacion.de("Lectura de la caja tributaria"));
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "rentas",
                            "contribuyentes",
                            EnumSet.of(Privilegio.LECTURA),
                            Observacion.de("Lectura del padron de contribuyentes"));

            assertThat(
                            arnes.permisos().deGrupo(grupo.id()).stream()
                                    .map(p -> p.sistema() + ":" + p.codigoDeAcceso())
                                    .sorted()
                                    .toList())
                    .as(
                            "sin el sistema, «caja_tributaria» y «contribuyentes» serian dos codigos"
                                    + " sueltos y quien administra no sabria de que pantalla habla")
                    .containsExactly("caja:caja_tributaria", "rentas:contribuyentes");
        }
    }

    @Nested
    @DisplayName("La precedencia entre el grupo y la excepcion del usuario")
    class Precedencia {

        @Test
        @DisplayName("la excepcion SUSTITUYE al grupo entero para ese acceso, no se suma")
        void laExcepcionSustituye() {
            Grupo grupo = grupo("Con excepcion");
            Usuario usuario = usuario("con.excepcion", "Con excepcion");
            arnes.administrar().afiliar(grupo.id(), usuario.id(), Observacion.de("Entra al grupo"));

            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "rentas",
                            "contribuyentes",
                            EnumSet.of(Privilegio.LECTURA, Privilegio.REGISTRO),
                            Observacion.de("El grupo lee y registra en el padron"));
            arnes.permisos()
                    .fijarParaUsuario(
                            usuario.id(),
                            "rentas",
                            "contribuyentes",
                            EnumSet.of(Privilegio.LECTURA),
                            Observacion.de("A esta persona se le restringe a solo lectura"));

            PermisoEfectivo fila = soloDe(usuario.id(), "rentas", "contribuyentes");
            assertThat(fila.privilegios())
                    .as("la excepcion RESTRINGE: no se suma a lo del grupo, lo sustituye")
                    .containsExactly(Privilegio.LECTURA);
            assertThat(fila.origen()).isEqualTo(PermisoEfectivo.OrigenDelPermiso.EXCEPCION);
        }

        @Test
        @DisplayName("una excepcion que NIEGA todo sigue produciendo su fila, vacia")
        void laExcepcionQueNiegaProduceFila() {
            Grupo grupo = grupo("Con negacion");
            Usuario usuario = usuario("negado", "Negado expresamente");
            arnes.administrar().afiliar(grupo.id(), usuario.id(), Observacion.de("Entra al grupo"));
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "rentas",
                            "contribuyentes",
                            EnumSet.of(Privilegio.LECTURA),
                            Observacion.de("El grupo lee el padron"));
            arnes.permisos()
                    .fijarParaUsuario(
                            usuario.id(),
                            "rentas",
                            "contribuyentes",
                            NINGUNO,
                            Observacion.de("Se le niega expresamente mientras dure el proceso"));

            PermisoEfectivo fila = soloDe(usuario.id(), "rentas", "contribuyentes");
            assertThat(fila.privilegios())
                    .as(
                            "es la unica forma de distinguir «se le nego expresamente» de «nunca lo"
                                    + " tuvo»: sin la fila vacia las dos son la misma respuesta")
                    .isEmpty();
            assertThat(fila.origen()).isEqualTo(PermisoEfectivo.OrigenDelPermiso.EXCEPCION);
        }
    }

    @Nested
    @DisplayName("Nadie puede dejar el sistema sin administrador")
    class UltimoAdministrador {

        @Test
        @DisplayName("retirar el ultimo permiso de administracion se rechaza, y no deja rastro")
        void retirarElUltimoSeRechaza() throws SQLException {
            long antes = arnes.contar("SELECT count(*) FROM identidad_evento");

            assertThatThrownBy(
                            () ->
                                    arnes.permisos()
                                            .fijarParaGrupo(
                                                    grupoAdministrador,
                                                    SistemasDelProducto.IDENTIDAD,
                                                    OPCION_DE_ADMINISTRACION,
                                                    NINGUNO,
                                                    Observacion.de(
                                                            "Retiro del ultimo administrador")))
                    .isInstanceOf(ProblemaDeNegocio.class)
                    .hasMessageContaining("sin ningun usuario capaz de administrar");

            // El evento se emite ANTES de la guarda y dentro de la misma transaccion, asi que el
            // rechazo se lo lleva por delante. Sin esto, los otros cuatro sistemas recibirian la
            // retirada de un permiso que aqui sigue puesto, y esa discrepancia no la corrige nadie.
            assertThat(arnes.contar("SELECT count(*) FROM identidad_evento"))
                    .as("una escritura que se deshace se lleva su evento con ella (AC-2)")
                    .isEqualTo(antes);
            assertThat(
                            arnes.contar(
                                    "SELECT count(*) FROM permiso p"
                                            + " JOIN acceso a ON a.id = p.acceso_id"
                                            + " WHERE a.sistema = 'identidad'"
                                            + "   AND a.codigo = 'permisos' AND p.registro"))
                    .as("y el permiso sigue puesto")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("y la guarda cuenta por el PAR: administrar los permisos de `rentas` no vale")
        void laGuardaCuentaPorElPar() {
            Grupo otros = grupo("Administra los de rentas");
            Usuario usuario = usuario("admin.rentas", "Administra rentas");
            arnes.administrar().afiliar(otros.id(), usuario.id(), Observacion.de("Entra al grupo"));

            // Le damos los siete sobre `rentas:permisos`, que es OTRA opcion con el mismo codigo.
            arnes.permisos()
                    .fijarParaGrupo(
                            otros.id(),
                            "rentas",
                            OPCION_DE_ADMINISTRACION,
                            LOS_SIETE,
                            Observacion.de("Administra los permisos de rentas y nada mas"));

            // Y aun asi, retirarle el suyo al administrador de verdad sigue siendo un 409: si la
            // guarda contara por el codigo solo, esta cuenta la habria dado por administradora de
            // `identidad` y el cambio habria pasado — dejando el sistema sin quien administre y con
            // la tranquilidad de haberlo comprobado.
            assertThatThrownBy(
                            () ->
                                    arnes.permisos()
                                            .fijarParaGrupo(
                                                    grupoAdministrador,
                                                    SistemasDelProducto.IDENTIDAD,
                                                    OPCION_DE_ADMINISTRACION,
                                                    NINGUNO,
                                                    Observacion.de("Retiro del ultimo")))
                    .isInstanceOf(ProblemaDeNegocio.class)
                    .hasMessageContaining("sin ningun usuario capaz de administrar");
        }
    }

    @Nested
    @DisplayName("RF-122 — Una opcion del catalogo de CUALQUIERA de los cinco se puede configurar")
    class OpcionesDeLosCinco {

        @Test
        @DisplayName("se puede otorgar sobre una opcion de cada uno de los cinco")
        void unaDeCadaUno() {
            Grupo grupo = grupo("De los cinco");
            for (String sistema : SistemasDelProducto.TODOS) {
                CatalogoUnido.Opcion opcion =
                        catalogo.opciones().stream()
                                .filter(o -> o.sistema().equals(sistema))
                                .findFirst()
                                .orElseThrow();
                arnes.permisos()
                        .fijarParaGrupo(
                                grupo.id(),
                                opcion.sistema(),
                                opcion.codigo(),
                                EnumSet.of(Privilegio.LECTURA),
                                Observacion.de("Lectura de una opcion de " + sistema));
            }

            assertThat(
                            arnes.permisos().deGrupo(grupo.id()).stream()
                                    .map(p -> p.sistema())
                                    .distinct()
                                    .sorted()
                                    .toList())
                    .as(
                            "lo que esta base guarda es a quien se le concede cada opcion de los"
                                    + " CINCO (ADR-0039), no el menu de este sistema")
                    .containsExactlyElementsOf(
                            SistemasDelProducto.TODOS.stream().sorted().toList());
        }

        @Test
        @DisplayName("y un acceso que no esta en ningun catalogo se rechaza nombrando el par")
        void unAccesoInexistenteSeRechaza() {
            Grupo grupo = grupo("Con acceso inventado");
            assertThatThrownBy(
                            () ->
                                    arnes.permisos()
                                            .fijarParaGrupo(
                                                    grupo.id(),
                                                    "caja",
                                                    "pantalla_que_no_existe",
                                                    LOS_SIETE,
                                                    Observacion.de("No deberia encontrarlo")))
                    .isInstanceOf(ProblemaDeNegocio.class)
                    .hasMessageContaining("pantalla_que_no_existe")
                    .hasMessageContaining("caja");
        }

        @Test
        @DisplayName("y el mismo codigo del sistema equivocado es OTRA opcion, no la misma")
        void elMismoCodigoDeOtroSistemaEsOtraOpcion() {
            Grupo grupo = grupo("Dos permisos");
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            SistemasDelProducto.IDENTIDAD,
                            OPCION_DE_ADMINISTRACION,
                            EnumSet.of(Privilegio.LECTURA),
                            Observacion.de("Lectura de los permisos de este sistema"));
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "rentas",
                            OPCION_DE_ADMINISTRACION,
                            EnumSet.of(Privilegio.IMPRESION),
                            Observacion.de("Impresion de los permisos de rentas"));

            assertThat(
                            arnes.permisos().deGrupo(grupo.id()).stream()
                                    .filter(
                                            p ->
                                                    OPCION_DE_ADMINISTRACION.equals(
                                                            p.codigoDeAcceso()))
                                    .map(p -> p.sistema() + "=" + p.privilegios())
                                    .sorted()
                                    .toList())
                    .as(
                            "son DOS filas de permiso sobre DOS accesos. Con la clave de dos"
                                    + " columnas del baseline habria sido una sola, y la segunda"
                                    + " fijacion habria pisado la primera")
                    .containsExactly("identidad=[LECTURA]", "rentas=[IMPRESION]");
        }
    }

    @Nested
    @DisplayName("Auditoria de la configuracion (ADR-0008 §5)")
    class AuditoriaDeLaConfiguracion {

        @Test
        @DisplayName(
                "todo cambio de permisos deja su fila, con el sistema, el acceso y lo otorgado")
        void todoCambioDejaSuFila() throws SQLException {
            Grupo grupo = grupo("Auditado");
            arnes.permisos()
                    .fijarParaGrupo(
                            grupo.id(),
                            "normativa",
                            "parametros",
                            EnumSet.of(Privilegio.LECTURA, Privilegio.IMPRESION),
                            Observacion.de("Consulta e impresion de los parametros del sistema"));

            List<String> datos =
                    arnes.filas(
                            "SELECT datos_nuevos::text FROM auditoria"
                                    + " WHERE operacion = 'PERMISO' ORDER BY id DESC LIMIT 1");
            assertThat(datos).hasSize(1);
            assertThat(datos.get(0))
                    .as("sin el sistema, la pista no dice sobre que pantalla fue el cambio")
                    .contains("\"sistema\": \"normativa\"")
                    .contains("\"acceso\": \"parametros\"")
                    .contains("LECTURA")
                    .contains("IMPRESION");
        }
    }

    // ------------------------------------------------------------------

    private static Grupo grupo(String nombre) {
        return arnes.administrar()
                .registrarGrupo(
                        Grupo.nuevo(nombre, null), Observacion.de("Alta del grupo " + nombre));
    }

    private static Usuario usuario(String cuenta, String nombre) {
        return arnes.administrar()
                .registrarUsuario(
                        Usuario.nuevo(cuenta, nombre, null),
                        Observacion.de("Alta de la cuenta " + cuenta));
    }

    private static PermisoEfectivo soloDe(long usuarioId, String sistema, String codigo) {
        return arnes.permisos().efectivosDeUsuario(usuarioId).stream()
                .filter(p -> p.sistema().equals(sistema) && p.codigoDeAcceso().equals(codigo))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "no salio ninguna fila para "
                                                + sistema
                                                + ":"
                                                + codigo
                                                + ", y una matriz sin fila no dice lo mismo que una"
                                                + " con la fila vacia"));
    }
}
