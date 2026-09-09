package kamayuk.identidad.nucleo.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.sql.SQLException;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.Usuario;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import kamayuk.identidad.web.Api;
import kamayuk.identidad.web.ConfiguracionDeJson;
import kamayuk.identidad.web.ManejadorDeErrores;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-1: las once escrituras y las diez lecturas, <b>de HTTP a PostgreSQL</b>, bajo {@code
 * /identidad/api/v1}.
 *
 * <h2>Que anade sobre las pruebas de caso de uso</h2>
 *
 * <p>Las de {@code aplicacion} miden lo que decide el caso de uso; esta mide lo que <b>contesta el
 * borde</b>, que es otra cosa y es donde se pierden las decisiones: el 422 de la observacion que
 * falta, el 409 del nombre repetido, el 404 del que no existe en esta municipalidad, y —lo nuevo de
 * esta etapa— que el <b>par {@code (sistema, codigo)}</b> viaja de verdad en el cuerpo y en la
 * consulta.
 *
 * <p>El pool se conecta como {@code kamayuk_app} y hay un centinela que lo comprueba: con el dueño
 * de las tablas la mitad de lo que esta prueba mide pasaria en verde sin ejercerse, porque {@code
 * FORCE ROW LEVEL SECURITY} protege del propietario pero no del superusuario (DAT-01 §0).
 */
@DisplayName("AC-1 — la administracion, de HTTP a PostgreSQL")
class AdministracionDePuntaAPuntaTest {

    private static final String RAIZ = Api.RAIZ + "/seguridad";

    private static ArnesDeAdministracion arnes;
    private static MockMvc mvc;
    private static long municipalidadA;
    private static long municipalidadB;
    private static long grupoDeB;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        municipalidadA = arnes.crearMunicipalidad("290101", "Frontera A");
        municipalidadB = arnes.crearMunicipalidad("290102", "Frontera B");

        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();
        for (long municipalidad : new long[] {municipalidadA, municipalidadB}) {
            ArnesDeAdministracion.entrarComo(municipalidad, "despliegue");
            try {
                arnes.sembrador().sembrar(catalogo, Observacion.de("Siembra de la prueba"));
            } finally {
                ArnesDeAdministracion.salir();
            }
        }

        // El administrador de guardia de A. Sin el, el PRIMER `PUT` de permisos de esta clase
        // saldria 409 —«el cambio dejaria a la municipalidad sin ningun usuario capaz de
        // administrar permisos»— y el rojo hablaria de otra cosa. Es lo mismo que hace la
        // implantacion, por el mismo camino.
        ArnesDeAdministracion.entrarComo(municipalidadA, "admin.a");
        try {
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
                            "permisos",
                            java.util.EnumSet.allOf(
                                    kamayuk.identidad.autorizacion.Privilegio.class),
                            Observacion.de("Administra los permisos de esta municipalidad"));
        } finally {
            ArnesDeAdministracion.salir();
        }

        // Un grupo de B, para poder comprobar que desde A no existe: 404 y no una respuesta vacia.
        ArnesDeAdministracion.entrarComo(municipalidadB, "admin.b");
        try {
            grupoDeB =
                    arnes.administrar()
                            .registrarGrupo(
                                    Grupo.nuevo("Solo de B", null),
                                    Observacion.de("Alta del grupo de la otra municipalidad"))
                            .id();
        } finally {
            ArnesDeAdministracion.salir();
        }

        mvc =
                MockMvcBuilders.standaloneSetup(
                                new SeguridadController(arnes.administrar()),
                                new PermisosController(arnes.permisos()),
                                new PermisosDeUsuarioController(arnes.permisos()),
                                new TitularesDelPrivilegioController(arnes.permisos()))
                        .setControllerAdvice(new ManejadorDeErrores())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(
                                        JsonMapper.builder()
                                                .addModule(
                                                        new ConfiguracionDeJson()
                                                                .moduloDeObjetosDeValor())
                                                .build()))
                        .build();
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    @BeforeEach
    void fijarContexto() {
        ArnesDeAdministracion.entrarComo(municipalidadA, "admin.a");
    }

    @AfterEach
    void limpiarContexto() {
        ArnesDeAdministracion.salir();
    }

    @Test
    @DisplayName("centinela — el pool que usa el controlador es el de kamayuk_app, no otro rol")
    void seConectaComoKamayukApp() {
        String rol =
                arnes.transaccion()
                        .execute(
                                estado ->
                                        arnes.jdbc()
                                                .sql("SELECT current_user")
                                                .query(String.class)
                                                .single());
        assertThat(rol)
                .as(
                        "con el SUPERUSUARIO del cluster la mitad de esta clase pasaria en verde sin"
                                + " ejercer ninguna politica (DAT-01 §0, hallazgo 1)")
                .isEqualTo("kamayuk_app");
    }

    @Test
    @DisplayName("el alta de un grupo escribe su fila, y el listado la ve")
    void elAltaDeGrupoEscribeSuFila() throws Exception {
        MvcResult alta =
                mvc.perform(
                                post(RAIZ + "/grupos")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"nombre":"Ventanilla",
                                                 "descripcion":"Atiende al contribuyente",
                                                 "observacion":"Alta segun memorando 2026-12"}"""))
                        .andReturn();

        assertThat(alta.getResponse().getStatus()).isEqualTo(201);
        assertThat(alta.getResponse().getContentAsString()).contains("\"nombre\":\"Ventanilla\"");

        assertThat(cuerpoDe(get(RAIZ + "/grupos?tamano=500"))).contains("Ventanilla");
    }

    @Test
    @DisplayName("y el mismo nombre otra vez es 409, no un choque de clave")
    void elNombreRepetidoEs409() throws Exception {
        String cuerpo =
                """
                {"nombre":"Repetido","observacion":"Alta que se hace dos veces"}""";
        mvc.perform(post(RAIZ + "/grupos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn();

        MvcResult segunda =
                mvc.perform(
                                post(RAIZ + "/grupos")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(cuerpo))
                        .andReturn();

        assertThat(segunda.getResponse().getStatus()).isEqualTo(409);
        assertThat(segunda.getResponse().getContentAsString()).contains("Repetido");
    }

    @Test
    @DisplayName("sin observacion no se guarda: 422 nombrando el campo (regla 10)")
    void sinObservacionNoSeGuarda() throws Exception {
        MvcResult sinObservacion =
                mvc.perform(
                                post(RAIZ + "/usuarios")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"cuenta":"sin.observacion","nombre":"Sin motivo"}"""))
                        .andReturn();

        assertThat(sinObservacion.getResponse().getStatus())
                .as(
                        "un campo que falta llega NULO desde Jackson, y sin esta lectura seria un"
                                + " 500 con identificador de incidencia donde tocaba un 422 (#30)")
                .isEqualTo(422);
        assertThat(sinObservacion.getResponse().getContentAsString()).contains("observacion");
        assertThat(arnes.contar("SELECT count(*) FROM usuario WHERE cuenta = 'sin.observacion'"))
                .as("y la fila no queda")
                .isZero();
    }

    @Test
    @DisplayName("la baja, la reactivacion y la vigencia son tres actos distintos")
    void lasTresEscriturasDeEstadoDeUnUsuario() throws Exception {
        long usuario = altaDeUsuario("con.estados", "Con estados");

        assertThat(estadoDe(post(RAIZ + "/usuarios/" + usuario + "/baja"), motivo("Cesa hoy")))
                .isEqualTo(200);
        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM usuario WHERE id = "
                                        + usuario
                                        + " AND NOT habilitado"))
                .isEqualTo(1);

        assertThat(
                        estadoDe(
                                post(RAIZ + "/usuarios/" + usuario + "/reactivacion"),
                                motivo("Se reincorpora")))
                .isEqualTo(200);
        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM usuario WHERE id = "
                                        + usuario
                                        + " AND habilitado"))
                .isEqualTo(1);

        assertThat(
                        estadoDe(
                                put(RAIZ + "/usuarios/" + usuario + "/vigencia"),
                                """
                                {"vigenciaDesde":"2026-01-01","vigenciaHasta":"2026-12-31",
                                 "observacion":"Contrato hasta fin de ejercicio"}"""))
                .isEqualTo(200);
        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM usuario WHERE id = "
                                        + usuario
                                        + " AND vigencia_hasta = DATE '2026-12-31'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("y una vigencia invertida es 422, no una fila imposible")
    void laVigenciaInvertidaEs422() throws Exception {
        long usuario = altaDeUsuario("vigencia.mala", "Vigencia invertida");
        assertThat(
                        estadoDe(
                                put(RAIZ + "/usuarios/" + usuario + "/vigencia"),
                                """
                                {"vigenciaDesde":"2026-12-31","vigenciaHasta":"2026-01-01",
                                 "observacion":"Las dos fechas al reves"}"""))
                .isEqualTo(422);
    }

    @Test
    @DisplayName("afiliar y desafiliar van por la misma ruta, y la baja no borra")
    void afiliarYDesafiliar() throws Exception {
        long grupo = altaDeGrupo("Con miembros");
        long usuario = altaDeUsuario("afiliado", "Quien se afilia");

        assertThat(
                        estadoDe(
                                post(RAIZ + "/grupos/" + grupo + "/miembros"),
                                "{\"usuarioId\":"
                                        + usuario
                                        + ",\"activo\":true,\"observacion\":\"Entra al grupo\"}"))
                .isEqualTo(200);
        assertThat(cuerpoDe(get(RAIZ + "/grupos/" + grupo + "/miembros"))).contains("afiliado");

        assertThat(
                        estadoDe(
                                post(RAIZ + "/grupos/" + grupo + "/miembros"),
                                "{\"usuarioId\":"
                                        + usuario
                                        + ",\"activo\":false,\"observacion\":\"Sale del grupo\"}"))
                .isEqualTo(200);
        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM miembro WHERE grupo_id = "
                                        + grupo
                                        + " AND usuario_id = "
                                        + usuario
                                        + " AND NOT activo"))
                .as("un DELETE aqui borraria la constancia de quien pudo hacer que (RNF-051)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("los dos listados del catalogo publican el sistema, y se pueden acotar")
    void losListadosPublicanElSistema() throws Exception {
        String todos = cuerpoDe(get(RAIZ + "/accesos?tamano=500"));
        assertThat(todos)
                .as("sin el sistema, el codigo de la respuesta no identifica ninguna opcion")
                .contains("\"sistema\":\"identidad\"")
                .contains("\"sistema\":\"rentas\"");

        String soloIdentidad = cuerpoDe(get(RAIZ + "/accesos?sistema=identidad&tamano=500"));
        assertThat(soloIdentidad).contains("\"sistema\":\"identidad\"");
        assertThat(soloIdentidad)
                .as("acotado significa acotado: nada de otro catalogo se cuela")
                .doesNotContain("\"sistema\":\"rentas\"");

        assertThat(cuerpoDe(get(RAIZ + "/modulos?tamano=500")))
                .as("y los modulos igual: «SEGURIDAD» es un modulo de tres de los cinco")
                .contains("\"codigo\":\"SEGURIDAD\"");
    }

    @Test
    @DisplayName("un sistema que no es uno de los cinco es 422 enumerandolos")
    void unSistemaDesconocidoEs422() throws Exception {
        MvcResult respuesta = mvc.perform(get(RAIZ + "/accesos?sistema=tesoreria")).andReturn();
        assertThat(respuesta.getResponse().getStatus())
                .as(
                        "devolver la pagina vacia se leeria como «ese sistema no declara ninguna"
                                + " opcion», que de `normativa` —que declara una— es falso")
                .isEqualTo(422);
        assertThat(respuesta.getResponse().getContentAsString()).contains("identidad");
    }

    @Test
    @DisplayName("el PUT de permisos de un grupo exige el par, y lo devuelve")
    void elPutDePermisosExigeElPar() throws Exception {
        long grupo = altaDeGrupo("Con permisos");

        assertThat(
                        estadoDe(
                                put(RAIZ + "/grupos/" + grupo + "/permisos"),
                                """
                                {"niveles":[{"acceso":"caja_tributaria",
                                             "privilegios":["LECTURA"]}],
                                 "observacion":"Sin decir de que sistema es"}"""))
                .as(
                        "suponer `identidad` haria que un cuerpo escrito para otro catalogo fijara"
                                + " privilegios sobre la opcion equivocada, con 200 y sin ruido")
                .isEqualTo(422);

        MvcResult conElPar =
                mvc.perform(
                                put(RAIZ + "/grupos/" + grupo + "/permisos")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"niveles":[{"sistema":"caja",
                                                             "acceso":"caja_tributaria",
                                                             "privilegios":["LECTURA"]}],
                                                 "observacion":"La ventanilla consulta la caja"}"""))
                        .andReturn();
        assertThat(conElPar.getResponse().getStatus()).isEqualTo(200);
        assertThat(conElPar.getResponse().getContentAsString())
                .contains("\"sistema\":\"caja\"")
                .contains("\"acceso\":\"caja_tributaria\"");

        assertThat(cuerpoDe(get(RAIZ + "/grupos/" + grupo + "/permisos")))
                .as("y la lectura de la matriz devuelve el mismo par")
                .contains("\"sistema\":\"caja\"");
    }

    @Test
    @DisplayName("«quien tiene el privilegio» exige el sistema, y no lo supone")
    void losTitularesExigenElSistema() throws Exception {
        assertThat(
                        mvc.perform(
                                        get(
                                                RAIZ
                                                        + "/accesos/permisos/usuarios"
                                                        + "?privilegio=REGISTRO"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .as(
                        "«permisos» es una opcion de este sistema y otra de `rentas`: sin el par, la"
                                + " pregunta «quien tiene la llave» no tiene una sola respuesta")
                .isEqualTo(422);

        assertThat(
                        mvc.perform(
                                        get(
                                                RAIZ
                                                        + "/accesos/permisos/usuarios?sistema="
                                                        + SistemasDelProducto.IDENTIDAD
                                                        + "&privilegio=REGISTRO"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("desde A, el grupo de B no existe: 404, no una pagina vacia")
    void elAislamientoSeSostieneEnLaFrontera() throws Exception {
        MvcResult desdeA = mvc.perform(get(RAIZ + "/grupos/" + grupoDeB + "/miembros")).andReturn();
        assertThat(desdeA.getResponse().getStatus())
                .as(
                        "cero filas se leeria como «este grupo no lo tiene nadie», que es lo"
                                + " contrario de lo que hay que contestar")
                .isEqualTo(404);
    }

    // ------------------------------------------------------------------

    private static String motivo(String texto) {
        return "{\"observacion\":\"" + texto + "\"}";
    }

    private long altaDeGrupo(String nombre) {
        return arnes.administrar()
                .registrarGrupo(
                        Grupo.nuevo(nombre, null), Observacion.de("Alta del grupo " + nombre))
                .id();
    }

    private long altaDeUsuario(String cuenta, String nombre) {
        return arnes.administrar()
                .registrarUsuario(
                        Usuario.nuevo(cuenta, nombre, null),
                        Observacion.de("Alta de la cuenta " + cuenta))
                .id();
    }

    private static int estadoDe(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String cuerpo)
            throws Exception {
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private static String cuerpoDe(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
            throws Exception {
        return mvc.perform(peticion).andReturn().getResponse().getContentAsString();
    }
}
