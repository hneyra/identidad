package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import kamayuk.identidad.compartido.Pagina;
import kamayuk.identidad.compartido.Paginacion;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.dominio.Vigencia;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.Usuario;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RF-120 y RF-123 contra PostgreSQL real: modulos, accesos, grupos, usuarios y miembros.
 *
 * <p>Viene de {@code rentas} con las once escrituras (etapa 2 de {@code infrastructure#52}), y se
 * prueba con <b>dos municipalidades</b> sembradas por el mismo motivo que alli: el criterio que mas
 * importa no es funcional sino de aislamiento — esta es la base de la que depende quien puede hacer
 * que en los cinco sistemas, y una fuga aqui es una fuga en todos.
 *
 * <p><b>Lo que cambia respecto de la version de {@code rentas}</b>: el catalogo sembrado son las
 * <b>157</b> opciones de los cinco sistemas —eran 160 al copiarse, 161 con {@code eventos} en la
 * etapa 3, y 157 desde que la etapa 4 retiro de {@code rentas} las cuatro que ya no sirve— y no las
 * de uno, y los dos listados aceptan acotarse por sistema. Eso no es un detalle de cifras: es la
 * diferencia entre una base que guarda su menu y una que guarda a quien se le concede cada opcion
 * de todos (ADR-0039).
 */
@DisplayName("RF-120 — Administracion de la seguridad")
class AdministrarSeguridadTest {

    private static final Paginacion TODO = Paginacion.de(0, 500, "id");

    private static ArnesDeAdministracion arnes;
    private static long municipalidadA;
    private static long municipalidadB;
    private static CatalogoUnido catalogo;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        municipalidadA = arnes.crearMunicipalidad("270101", "Seguridad A");
        municipalidadB = arnes.crearMunicipalidad("270102", "Seguridad B");
        catalogo = CatalogoUnidoDelJar.leer();

        // Cada municipalidad recibe su propia siembra: los accesos son datos de tenant, no un
        // catalogo global. Es lo que hace que la comparacion de mas abajo signifique algo.
        sembrarEn(municipalidadA);
        sembrarEn(municipalidadB);
    }

    private static void sembrarEn(long municipalidad) {
        ArnesDeAdministracion.entrarComo(municipalidad, "despliegue");
        try {
            arnes.sembrador()
                    .sembrar(catalogo, Observacion.de("Siembra inicial del catalogo de los cinco"));
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
        ArnesDeAdministracion.entrarComo(municipalidadA, "admin.a");
    }

    @AfterEach
    void limpiarContexto() {
        ArnesDeAdministracion.salir();
    }

    @Nested
    @DisplayName("Aislamiento: de esta base depende quien puede hacer que en los cinco")
    class Aislamiento {

        @Test
        @DisplayName("un usuario de A no aparece en ninguna consulta con contexto de B")
        void unUsuarioDeANoApareceEnB() {
            arnes.administrar()
                    .registrarUsuario(
                            Usuario.nuevo("solo.de.a", "Empleado de A", null),
                            Observacion.de("Alta de empleado de la municipalidad A"));

            ArnesDeAdministracion.entrarComo(municipalidadB, "admin.b");
            Pagina<Usuario> desdeB = arnes.administrar().usuarios(TODO);

            assertThat(desdeB.contenido())
                    .as("no es que se filtre: desde B esa fila no existe")
                    .noneSatisfy(u -> assertThat(u.cuenta()).isEqualTo("solo.de.a"));
        }

        @Test
        @DisplayName("cada municipalidad tiene sus propios accesos, con los mismos codigos")
        void cadaMunicipalidadTieneSusPropiosAccesos() {
            long opciones = catalogo.opciones().size();
            assertThat(arnes.administrar().accesos(null, TODO).totalElementos())
                    .isEqualTo(opciones);

            ArnesDeAdministracion.entrarComo(municipalidadB, "admin.b");
            assertThat(arnes.administrar().accesos(null, TODO).totalElementos())
                    .as("los accesos son datos de tenant: cada una configura los suyos")
                    .isEqualTo(opciones);
        }
    }

    @Nested
    @DisplayName("La siembra es el catalogo de los CINCO, y se puede acotar a uno")
    class Siembra {

        @Test
        @DisplayName("los codigos sembrados son los del catalogo unido, ni uno mas ni uno menos")
        void losCodigosCoincidenConElCatalogo() {
            List<String> sembrados =
                    arnes.administrar().accesos(null, TODO).contenido().stream()
                            .map(a -> a.sistema() + ":" + a.codigo())
                            .sorted()
                            .toList();
            List<String> delCatalogo =
                    catalogo.opciones().stream()
                            .map(o -> o.sistema() + ":" + o.codigo())
                            .sorted()
                            .toList();

            // Si divergieran, habria opciones sin acceso configurable —una pantalla a la que nadie
            // puede dar permiso— o accesos huerfanos, que se configuran y no gobiernan nada.
            assertThat(sembrados).isEqualTo(delCatalogo);
        }

        @Test
        @DisplayName("el filtro por sistema acota, y omitirlo son los cinco")
        void elFiltroPorSistemaAcota() {
            assertThat(
                            arnes.administrar()
                                    .accesos(SistemasDelProducto.IDENTIDAD, TODO)
                                    .totalElementos())
                    .as("las seis de este sistema")
                    .isEqualTo(catalogo.cuantasDe(SistemasDelProducto.IDENTIDAD));
            assertThat(arnes.administrar().accesos("caja", TODO).contenido())
                    .as("y solo las de ese sistema, sin colarse ninguna de otro")
                    .allSatisfy(a -> assertThat(a.sistema()).isEqualTo("caja"));
            assertThat(arnes.administrar().accesos(null, TODO).totalElementos())
                    .as("sin filtro, los cinco catalogos")
                    .isEqualTo(catalogo.opciones().size());
        }

        @Test
        @DisplayName("el mismo codigo de modulo de dos sistemas son DOS modulos")
        void elMismoCodigoDeDosSistemasSonDosModulos() {
            List<String> seguridad =
                    arnes.administrar().modulos(null, TODO).contenido().stream()
                            .filter(m -> "SEGURIDAD".equals(m.codigo()))
                            .map(m -> m.sistema())
                            .sorted()
                            .toList();

            // Es la desviacion del baseline ejercida: sin la columna `sistema`, el segundo INSERT
            // choca con el UNIQUE del primero y el `ON CONFLICT DO NOTHING` lo descarta EN
            // SILENCIO.
            assertThat(seguridad)
                    .as(
                            "«SEGURIDAD» es un modulo de `identidad`, otro de `normativa` y otro de `rentas`")
                    .containsExactly("identidad", "normativa", "rentas");
        }
    }

    @Nested
    @DisplayName("Alta, baja y vigencia, con auditoria")
    class AltaBajaYVigencia {

        @Test
        @DisplayName("toda alta, baja y modificacion deja auditoria con su observacion")
        void todaEscrituraDejaAuditoria() throws SQLException {
            Grupo grupo =
                    arnes.administrar()
                            .registrarGrupo(
                                    Grupo.nuevo("Auditables", "Grupo para verificar la pista"),
                                    Observacion.de("Alta del grupo segun memorando 2026-77"));

            arnes.administrar()
                    .inhabilitarGrupo(
                            grupo.id(), Observacion.de("Suspension temporal por reorganizacion"));
            arnes.administrar()
                    .habilitarGrupo(
                            grupo.id(), Observacion.de("Se reactiva terminada la reorganizacion"));

            assertThat(
                            arnes.filas(
                                    "SELECT operacion FROM auditoria WHERE tabla = 'grupo'"
                                            + " AND clave = '"
                                            + grupo.id()
                                            + "' ORDER BY id"))
                    .containsExactly("ALTA", "BAJA", "MODIFICACION");
            assertThat(
                            arnes.filas(
                                    "SELECT observacion FROM auditoria WHERE tabla = 'grupo'"
                                            + " AND clave = '"
                                            + grupo.id()
                                            + "' ORDER BY id"))
                    .allSatisfy(o -> assertThat(o).isNotBlank());
        }

        @Test
        @DisplayName("desafiliar da de baja la fila, no la borra")
        void desafiliarDaDeBajaNoBorra() throws SQLException {
            Grupo grupo =
                    arnes.administrar()
                            .registrarGrupo(
                                    Grupo.nuevo("Del que se sale", null),
                                    Observacion.de("Alta de grupo para la prueba de baja"));
            Usuario usuario =
                    arnes.administrar()
                            .registrarUsuario(
                                    Usuario.nuevo("se.va", "Quien se va", null),
                                    Observacion.de("Alta de usuario que despues sale del grupo"));

            arnes.administrar().afiliar(grupo.id(), usuario.id(), Observacion.de("Entra al grupo"));
            arnes.administrar()
                    .desafiliar(
                            grupo.id(),
                            usuario.id(),
                            Observacion.de("Sale del grupo por cambio de area"));

            assertThat(
                            arnes.contar(
                                    "SELECT count(*) FROM miembro WHERE grupo_id = "
                                            + grupo.id()
                                            + " AND usuario_id = "
                                            + usuario.id()))
                    .as("la fila dice que entre tal dia y tal otro esa persona pudo hacer aquello")
                    .isEqualTo(1);
            assertThat(
                            arnes.contar(
                                    "SELECT count(*) FROM miembro WHERE grupo_id = "
                                            + grupo.id()
                                            + " AND usuario_id = "
                                            + usuario.id()
                                            + " AND NOT activo AND fecha_baja IS NOT NULL"))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("RF-123: la vigencia se fija, y la fila lo dice")
        void laVigenciaSeFija() {
            Usuario usuario =
                    arnes.administrar()
                            .registrarUsuario(
                                    Usuario.nuevo("por.contrato", "Personal por contrato", null),
                                    Observacion.de("Alta de personal por contrato"));

            Usuario conVigencia =
                    arnes.administrar()
                            .fijarVigenciaDeUsuario(
                                    usuario.id(),
                                    new Vigencia(
                                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
                                    Observacion.de("Fin de contrato segun resolucion 2026-90"));

            assertThat(conVigencia.vigencia().hasta()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(conVigencia.autorizaEn(LocalDate.of(2026, 9, 9)))
                    .as("caduca sola el dia que termina el contrato, sin retirar el permiso")
                    .isFalse();
        }

        @Test
        @DisplayName("un grupo inexistente da 404, no un error del motor")
        void unGrupoInexistenteDa404() {
            assertThatThrownBy(
                            () ->
                                    arnes.administrar()
                                            .inhabilitarGrupo(
                                                    999_999L,
                                                    Observacion.de("No deberia encontrarlo")))
                    .isInstanceOf(kamayuk.identidad.web.ProblemaDeNegocio.class)
                    .hasMessageContaining("999999");
        }

        @Test
        @DisplayName("la cuenta repetida en la misma municipalidad se nombra")
        void laCuentaRepetidaSeNombra() {
            arnes.administrar()
                    .registrarUsuario(
                            Usuario.nuevo("repetida", "Primera", null),
                            Observacion.de("Alta de la primera cuenta"));
            assertThatThrownBy(
                            () ->
                                    arnes.administrar()
                                            .registrarUsuario(
                                                    Usuario.nuevo("repetida", "Segunda", null),
                                                    Observacion.de("Alta que deberia chocar")))
                    .isInstanceOf(AdministrarSeguridad.CuentaRepetida.class)
                    .hasMessageContaining("repetida");
        }
    }
}
