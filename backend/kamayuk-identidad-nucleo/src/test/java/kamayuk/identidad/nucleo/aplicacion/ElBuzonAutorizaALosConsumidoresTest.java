package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import kamayuk.identidad.autorizacion.GuardiaDeAcceso;
import kamayuk.identidad.esquema.BaseDeDatosDePrueba;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import kamayuk.identidad.nucleo.infraestructura.RegistroDeMunicipalidadesJdbc;
import kamayuk.identidad.nucleo.infraestructura.web.EventosController;
import kamayuk.identidad.seguridad.infraestructura.ComprobadorDeAccesoJdbc;
import kamayuk.identidad.web.Api;
import kamayuk.identidad.web.ConfiguracionDeJson;
import kamayuk.identidad.web.ManejadorDeErrores;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Etapa 4: la implantacion deja a los cuatro consumidores <b>pudiendo leer el buzon</b>.
 *
 * <h2>Que se estaria midiendo sin esto, y por que hizo falta levantarlo todo para verlo</h2>
 *
 * <p>Hasta la etapa 3 el grupo «Consumidores del buzon» nacia <b>vacio</b>, con el razonamiento de
 * que afiliar las cuatro cuentas de servicio era del despliegue. Medido el 2026-09-09 con las cinco
 * aplicaciones levantadas de verdad, <b>eso no lo hace nadie</b>: el emisor crea el cliente
 * confidencial de cada satelite —{@code reconciliar-identidades.sh servicios}—, o sea que el
 * consumidor consigue su token y llega hasta aqui; y aqui su cuenta no tiene fila en {@code
 * usuario}, asi que el guardia contesta <b>403</b> y la pasada del consumidor de cada implantacion
 * muere con «identidad contesto 403 al leer el buzon». La copia local de los cuatro sistemas se
 * queda como la dejo su implantacion, y <b>nada lo dice</b>.
 *
 * <p>Esa medicion se hizo con cinco JVM, un emisor y cinco bases. Lo que esta prueba hace es dejar
 * el mismo hecho medible en una tabla y en un {@code MockMvc}: implanta y <b>pide el buzon con el
 * guardia de verdad delante</b>.
 *
 * <h2>El guardia y su comprobador son los de produccion, y esa es toda la gracia</h2>
 *
 * <p>{@link GuardiaDeAcceso} es el interceptor que corre en la aplicacion y {@link
 * ComprobadorDeAccesoJdbc} es quien resuelve el permiso contra las cinco tablas de esta base. Un
 * comprobador escrito en la prueba habria medido esa copia: la precedencia usuario-sobre-grupo, el
 * {@code a.sistema = 'identidad'} y la vigencia son suyos, y son justo lo que decide si estas
 * cuatro cuentas autorizan.
 *
 * <p>Los dos contextos se fijan a mano porque el perfil {@code batch} no tiene filtros HTTP: el
 * {@code azp} va en el token del contexto de seguridad —de ahi saca {@code EventosController} quien
 * pregunta— y la cuenta va en {@code OrigenContext} —de ahi la saca el guardia—. En produccion las
 * dos salen del <b>mismo</b> token firmado: {@code azp} y {@code preferred_username}.
 */
@DisplayName("Etapa 4 — la implantacion deja a los cuatro consumidores pudiendo leer el buzon")
class ElBuzonAutorizaALosConsumidoresTest {

    private static final String UBIGEO = "200105";
    private static final String RUTA = Api.RAIZ + "/eventos";

    private static ArnesDeAdministracion arnes;
    private static MockMvc mvc;
    private static long municipalidad;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();

        // El comprobador va envuelto en el mismo interceptor de transacciones que los casos de uso
        // del arnes, y no es decorativo: sus consultas leen tablas con RLS, asi que sin `SET LOCAL`
        // no devuelven vacio — revientan con «invalid input syntax for type bigint: ""». Lo dice su
        // propio javadoc, y es el defecto que H1 de la medicion encontro vivo en `rentas`.
        ComprobadorDeAccesoJdbc comprobador =
                arnes.conTransaccion(new ComprobadorDeAccesoJdbc(arnes.jdbc()));

        mvc =
                MockMvcBuilders.standaloneSetup(new EventosController(arnes.entrega()))
                        .addInterceptors(
                                new GuardiaDeAcceso(comprobador, ArnesDeAdministracion.RELOJ))
                        .setControllerAdvice(new ManejadorDeErrores())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(
                                        JsonMapper.builder()
                                                .addModule(
                                                        new ConfiguracionDeJson()
                                                                .moduloDeObjetosDeValor())
                                                .build()))
                        .build();

        municipalidad = implantar();
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        ArnesDeAdministracion.salir();
    }

    @Test
    @DisplayName("los cuatro tokens de servicio leen el buzon: 200, sin tocar nada a mano")
    void losCuatroLeenElBuzon() throws Exception {
        for (Consumidor consumidor : Consumidor.values()) {
            entrarComoCuentaDeServicio(consumidor);
            MvcResult respuesta = mvc.perform(get(RUTA + "/pendientes?limite=5")).andReturn();

            assertThat(respuesta.getResponse().getStatus())
                    .as(
                            "[la implantacion tiene que dejar a «%s» pudiendo leer el buzon. Si"
                                    + " esto es 403, el consumidor de ese sistema muere en cada vuelta"
                                    + " con «identidad contesto 403 al leer el buzon» y su copia local"
                                    + " se queda como la dejo su implantacion, sin un solo error que lo"
                                    + " diga: un permiso concedido aqui no llega nunca alli, y el"
                                    + " sintoma aparece semanas despues como un 403 en una pantalla"
                                    + " suya] contesto: %s",
                            consumidor.sistema(), respuesta.getResponse().getContentAsString())
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName(
            "y las cuatro cuentas son EXACTAMENTE las que el emisor nombra, sin la de identidad")
    void lasCuatroCuentasSonLasDelEmisor() throws SQLException {
        assertThat(
                        arnes.filas(
                                "SELECT u.cuenta FROM usuario u"
                                        + " JOIN miembro m ON m.usuario_id = u.id AND m.activo"
                                        + " JOIN grupo g ON g.id = m.grupo_id"
                                        + " WHERE g.nombre = '"
                                        + ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES
                                        + "' ORDER BY 1"))
                .as(
                        "[la cuenta es lo unico que une esta fila con el token que el emisor firma:"
                                + " una que no sea literalmente «service-account-<cliente>» se da de"
                                + " alta igual y no la nombra ningun token, asi que el 403 sigue"
                                + " ahi con una fila mas en la tabla. Y «identidad» NO esta: este"
                                + " sistema no se consume a si mismo —lo que el buzon publica es lo"
                                + " que esta misma base acaba de escribir—, asi que su cuenta no"
                                + " tendria nada que leer y su acuse retiraria un evento que nadie"
                                + " ha aplicado]")
                .containsExactly(
                        "service-account-kamayuk-caja-servicio-" + UBIGEO,
                        "service-account-kamayuk-catastro-servicio-" + UBIGEO,
                        "service-account-kamayuk-normativa-servicio-" + UBIGEO,
                        "service-account-kamayuk-rentas-servicio-" + UBIGEO);
    }

    /**
     * El contraste: una cuenta de servicio de una municipalidad que no es esta no entra.
     *
     * <p>Sin el, «los cuatro leen» se cumpliria igual con una implantacion que diera de alta
     * <b>cualquier</b> cuenta, o con un guardia que dijera que si a todo. El ubigeo va dentro de la
     * cuenta porque el cliente es uno por par (sistema, municipalidad) —ADR-0028 §2—, y esa es
     * justamente la propiedad que hace que el token de un vecino no sirva aqui.
     */
    @Test
    @DisplayName("y la cuenta de servicio de OTRA municipalidad no: 403 nombrandola")
    void laCuentaDeOtraMunicipalidadNo() throws Exception {
        String deOtra = Consumidor.CAJA.cuentaDeServicio("200101");
        ArnesDeAdministracion.entrarComo(municipalidad, deOtra);
        entrarConAzp("kamayuk-caja-servicio-200101");

        MvcResult respuesta = mvc.perform(get(RUTA + "/pendientes")).andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(403);
        assertThat(respuesta.getResponse().getContentAsString())
                .as(
                        "y el 403 dice CUAL es la cuenta: «no esta dada de alta» sin el nombre"
                                + " manda a mirar la configuracion entera")
                .contains(deOtra)
                .contains("no esta dada de alta en este sistema");
    }

    private static void entrarComoCuentaDeServicio(Consumidor consumidor) {
        ArnesDeAdministracion.entrarComo(municipalidad, consumidor.cuentaDeServicio(UBIGEO));
        entrarConAzp(consumidor.clienteDeServicio(UBIGEO));
    }

    /** Lo que Spring Security deja en el contexto cuando el token ya esta validado. */
    private static void entrarConAzp(String azp) {
        Jwt token =
                Jwt.withTokenValue("token-de-prueba")
                        .header("alg", "none")
                        .claim("azp", azp)
                        .claim("municipalidad_id", municipalidad)
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(token, List.of()));
    }

    private static long implantar() {
        new ImplantarMunicipalidad(
                        new RegistroDeMunicipalidadesJdbc(
                                arnes.base().url(),
                                BaseDeDatosDePrueba.OWNER,
                                arnes.base().clave(BaseDeDatosDePrueba.OWNER)),
                        arnes.sembrador(),
                        arnes.administrar(),
                        arnes.permisos(),
                        new DatosDeImplantacion(
                                UBIGEO,
                                "Municipalidad de la prueba",
                                "DISTRITAL",
                                "administrador",
                                "Administrador del Sistema",
                                false,
                                "implantacion"))
                .run(new org.springframework.boot.DefaultApplicationArguments());
        return unicaMunicipalidad();
    }

    private static long unicaMunicipalidad() {
        try {
            return Long.parseLong(
                    arnes.filas("SELECT id FROM municipalidad WHERE ubigeo = '" + UBIGEO + "'")
                            .get(0));
        } catch (SQLException fallo) {
            throw new IllegalStateException("No se pudo leer la municipalidad implantada", fallo);
        }
    }
}
