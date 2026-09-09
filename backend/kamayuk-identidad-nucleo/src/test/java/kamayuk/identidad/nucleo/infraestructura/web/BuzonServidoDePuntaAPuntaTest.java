package kamayuk.identidad.nucleo.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.HechoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Miembro;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-2, la otra mitad: <b>de HTTP a PostgreSQL</b>, y quien pregunta sale del token.
 *
 * <h2>Que anade sobre {@code AcusesPorConsumidorTest}</h2>
 *
 * <p>Aquella mide la tabla; esta mide el <b>borde</b>, que es donde vive la unica decision de este
 * controlador: el consumidor se deriva del {@code azp} del token y no de nada que el cliente pueda
 * escribir. Sin esta clase, {@code pendientesPara(CAJA, …)} estaria comprobado y «como se llega a
 * {@code CAJA}» no lo estaria por nadie — que es exactamente donde se cuela un {@code
 * ?consumidor=}.
 *
 * <p>El montaje es {@code standaloneSetup}, igual que {@code AdministracionDePuntaAPuntaTest}: sin
 * cadena de seguridad, asi que <b>el token se pone a mano</b> en el {@link SecurityContextHolder}.
 * Eso es exactamente lo que hace Spring Security cuando el token ya esta validado, y es lo que el
 * controlador lee — no se esta simulando la validacion de la firma, que no es de este lado.
 */
@DisplayName("AC-2 — el buzon servido: quien pregunta sale del azp del token")
class BuzonServidoDePuntaAPuntaTest {

    private static final String RUTA = Api.RAIZ + "/eventos";

    /** El cliente confidencial de {@code caja} para esta municipalidad (ADR-0028 §2). */
    private static final String AZP_DE_CAJA = "kamayuk-caja-servicio-200105";

    private static final String AZP_DE_RENTAS = "kamayuk-rentas-servicio-200105";

    /** El cliente del backoffice: un token de PERSONA, que es el que no puede acusar. */
    private static final String AZP_DE_USUARIO = "sgtm-backoffice";

    private static ArnesDeAdministracion arnes;
    private static MockMvc mvc;
    private static int siguienteUbigeo = 320100;

    private long municipalidad;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        mvc =
                MockMvcBuilders.standaloneSetup(new EventosController(arnes.entrega()))
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

    /** Una municipalidad por prueba: ver el mismo epigrafe en {@code AcusesPorConsumidorTest}. */
    @BeforeEach
    void unaMunicipalidadNueva() throws SQLException {
        municipalidad = arnes.crearMunicipalidad(String.valueOf(++siguienteUbigeo), "Buzon HTTP");
        ArnesDeAdministracion.entrarComo(municipalidad, "publicador");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        ArnesDeAdministracion.salir();
    }

    @Test
    @DisplayName("un token de caja lee y acusa como caja, y rentas sigue viendo lo acusado")
    void unTokenDeCajaAcusaComoCaja() throws Exception {
        List<UUID> emitidos = emitir(2);

        entrarConAzp(AZP_DE_CAJA);
        assertThat(idsServidos()).containsExactlyElementsOf(emitidos);

        MvcResult acuse =
                mvc.perform(
                                post(RUTA + "/acuses")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(cuerpoDeAcuse(emitidos.get(0))))
                        .andReturn();
        assertThat(acuse.getResponse().getStatus()).isEqualTo(200);
        assertThat(acuse.getResponse().getContentAsString())
                .contains("\"recibidos\":1")
                .contains("\"escritos\":1");

        assertThat(idsServidos())
                .as("para caja, lo que caja acuso sale de su cola")
                .containsExactly(emitidos.get(1));

        entrarConAzp(AZP_DE_RENTAS);
        assertThat(idsServidos())
                .as(
                        "[el mismo defecto de AC-2, ahora por HTTP] «rentas» pide con SU token y"
                                + " tiene que seguir viendo los dos: lo que «caja» acuso es de"
                                + " «caja»")
                .containsExactlyElementsOf(emitidos);
    }

    /**
     * El token de una persona no puede leer ni acusar: <b>403</b>, y con su propio codigo.
     *
     * <p>Las dos operaciones, y no solo el acuse: si la lectura pasara, el buzon de autorizacion de
     * la municipalidad —quien puede hacer que, con su cuerpo entero— quedaria expuesto a cualquier
     * cuenta con la opcion {@code eventos}, que es una opcion del catalogo y por tanto algo que un
     * administrador puede concederse a si mismo.
     */
    @Test
    @DisplayName("un token de usuario recibe 403 en las dos, con SIN_IDENTIDAD_DE_SERVICIO")
    void unTokenDeUsuarioNoPuedeNiLeerNiAcusar() throws Exception {
        List<UUID> emitidos = emitir(1);
        entrarConAzp(AZP_DE_USUARIO);

        MvcResult lectura = mvc.perform(get(RUTA + "/pendientes")).andReturn();
        MvcResult acuse =
                mvc.perform(
                                post(RUTA + "/acuses")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(cuerpoDeAcuse(emitidos.get(0))))
                        .andReturn();

        for (MvcResult respuesta : List.of(lectura, acuse)) {
            assertThat(respuesta.getResponse().getStatus())
                    .as(
                            "[«sgtm-backoffice» es el cliente de las personas: acusar retira un"
                                    + " evento de la cola de un sistema, y lo retirado no se vuelve"
                                    + " a servir]")
                    .isEqualTo(403);
            assertThat(respuesta.getResponse().getContentAsString())
                    .as(
                            "y con codigo propio: «no tiene el privilegio» se arregla"
                                    + " concediendoselo, y esto se arregla pidiendo otro token")
                    .contains("SIN_IDENTIDAD_DE_SERVICIO")
                    .contains("sgtm-backoffice");
        }
    }

    /** Y sin ningun token tampoco hay consumidor por omision. */
    @Test
    @DisplayName("sin token, 403: no hay consumidor por omision")
    void sinTokenNoHayConsumidorPorOmision() throws Exception {
        emitir(1);
        SecurityContextHolder.clearContext();

        MvcResult lectura = mvc.perform(get(RUTA + "/pendientes")).andReturn();

        assertThat(lectura.getResponse().getStatus()).isEqualTo(403);
        assertThat(lectura.getResponse().getContentAsString())
                .as(
                        "[un valor por omision aqui —«si no dice quien es, rentas»— seria un acuse"
                                + " en nombre de otro producido por un descuido de configuracion]")
                .contains("SIN_IDENTIDAD_DE_SERVICIO");
    }

    /** {@code identidad} no se consume a si mismo, y el mensaje lo dice. */
    @Test
    @DisplayName("y la cuenta de servicio de identidad tampoco: no se consume a si mismo")
    void identidadNoSeConsumeASiMismo() throws Exception {
        emitir(1);
        entrarConAzp("kamayuk-identidad-servicio-200105");

        MvcResult lectura = mvc.perform(get(RUTA + "/pendientes")).andReturn();

        assertThat(lectura.getResponse().getStatus()).isEqualTo(403);
        assertThat(lectura.getResponse().getContentAsString())
                .contains("SIN_IDENTIDAD_DE_SERVICIO")
                .contains("NO se consume a si mismo");
    }

    @Test
    @DisplayName("el limite fuera de rango es 422, y el acuse de un evento que no consta tambien")
    void losDosBordesDeLaValidacion() throws Exception {
        emitir(1);
        entrarConAzp(AZP_DE_CAJA);

        MvcResult limite = mvc.perform(get(RUTA + "/pendientes?limite=501")).andReturn();
        assertThat(limite.getResponse().getStatus()).isEqualTo(422);
        assertThat(limite.getResponse().getContentAsString()).contains("El limite va de 1 a 500");

        UUID inventado = UUID.fromString("99999999-8888-4777-8666-555555555555");
        MvcResult acuse =
                mvc.perform(
                                post(RUTA + "/acuses")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(cuerpoDeAcuse(inventado)))
                        .andReturn();
        assertThat(acuse.getResponse().getStatus())
                .as("422 y no 404: lo que esta mal es el cuerpo, y el cliente puede corregirlo")
                .isEqualTo(422);
        assertThat(acuse.getResponse().getContentAsString()).contains(inventado.toString());
    }

    /** La forma del evento servido es la que los cuatro consumidores leen desde la etapa 4. */
    @Test
    @DisplayName("el evento servido trae los siete campos que el consumidor lee")
    void laFormaDelEventoServido() throws Exception {
        emitir(1);
        entrarConAzp(AZP_DE_RENTAS);

        String cuerpo =
                mvc.perform(get(RUTA + "/pendientes?limite=1"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(cuerpo)
                .as(
                        "[esta es la forma que cada consumidor declara en su"
                                + " docs/50-api/contratos-que-consume/identidad.json desde la"
                                + " etapa 4, y que ContratoCon<Consumidor>Test compara: un campo"
                                + " que se retire aqui deja su adaptador leyendo un nulo]")
                .contains("\"eventos\"")
                .contains("\"quedan\"")
                .contains("\"eventoId\"")
                .contains("\"secuencia\"")
                .contains("\"tipo\"")
                .contains("\"sujetoId\"")
                .contains("\"cuerpo\"")
                .contains("\"huella\"")
                .contains("\"creadoEn\"");
    }

    // ------------------------------------------------------------------

    /**
     * Pone en el contexto lo que Spring Security pone cuando el token ya esta validado.
     *
     * <p>{@code JwtAuthenticationToken} con autoridades —aunque sean ninguna— y no el constructor
     * de un solo argumento: ese deja la autenticacion en {@code isAuthenticated() == false}, y
     * entonces el controlador leeria el {@code azp} de un token que Spring considera no
     * autenticado.
     */
    private static void entrarConAzp(String azp) {
        Jwt token =
                Jwt.withTokenValue("token-de-prueba")
                        .header("alg", "none")
                        .claim("azp", azp)
                        .claim("municipalidad_id", 1)
                        .build();
        AbstractAuthenticationToken autenticacion = new JwtAuthenticationToken(token, List.of());
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    private List<UUID> emitir(int cuantos) {
        List<UUID> emitidos = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            EventoDeIdentidad evento =
                    arnes.transaccion()
                            .execute(
                                    estado ->
                                            arnes.buzon()
                                                    .emitir(
                                                            HechoDeIdentidad.deMiembro(
                                                                    new Miembro(1L, 2L, true),
                                                                    "Grupo",
                                                                    "cuenta",
                                                                    "quien")));
            emitidos.add(java.util.Objects.requireNonNull(evento).eventoId());
        }
        return emitidos;
    }

    /** Los identificadores que sirve el endpoint, en el orden en que los sirve. */
    private List<UUID> idsServidos() throws Exception {
        String cuerpo =
                mvc.perform(get(RUTA + "/pendientes"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<UUID> ids = new ArrayList<>();
        java.util.regex.Matcher hallazgos =
                java.util.regex.Pattern.compile("\"eventoId\":\"([0-9a-f-]{36})\"").matcher(cuerpo);
        while (hallazgos.find()) {
            ids.add(UUID.fromString(hallazgos.group(1)));
        }
        return ids;
    }

    private static String cuerpoDeAcuse(UUID... eventos) {
        List<String> comillados = new ArrayList<>();
        for (UUID evento : eventos) {
            comillados.add("\"" + evento + "\"");
        }
        return "{\"eventos\":[" + String.join(",", comillados) + "]}";
    }
}
