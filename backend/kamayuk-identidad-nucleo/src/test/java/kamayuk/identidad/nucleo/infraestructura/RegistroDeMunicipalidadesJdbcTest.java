package kamayuk.identidad.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import kamayuk.identidad.esquema.BaseDeDatosDePrueba;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El {@code id} de la municipalidad se ESCRIBE, no se pide a la secuencia.
 *
 * <h2>El defecto que esta clase existe para impedir</h2>
 *
 * <p>Salida 1 de <a href="https://github.com/hneyra/infrastructure/issues/73">
 * infrastructure#73</a>. El claim {@code municipalidad_id} —del que sale el {@code SET LOCAL} y con
 * el que actua el RLS— se escribia con TRES valores distintos y nada los reconciliaba:
 *
 * <ul>
 *   <li>el {@code municipalidadId} del archivo versionado, para los funcionarios;
 *   <li>el <b>ubigeo</b>, para las cuentas de servicio;
 *   <li>y el que la <b>secuencia</b> asignaba al implantar, que es el unico que el RLS entiende.
 * </ul>
 *
 * <p><b>Medido en {@code stg} el 2026-09-11</b>, con todo lo demas del ambiente arreglado: el token
 * de {@code kamayuk-rentas-servicio-200105} traia {@code municipalidad_id: 200105}, las cinco
 * fichas de {@code usuario} estaban en el inquilino {@code 1}, y los cuatro consumidores del buzon
 * recibian <b>403</b>. El sintoma es el peor posible —«la cuenta no esta dada de alta» CON LA FILA
 * DELANTE— porque manda a mirar el alta, que es lo unico que esta bien.
 *
 * <h2>Por que las afirmaciones usan un id que la secuencia NO habria dado</h2>
 *
 * <p>En una base recien creada la secuencia tambien da {@code 1}, asi que afirmar «vale 1» no
 * distinguiria un id declarado de uno asignado. De ahi el {@code 42}: si la fila sale con 42, el id
 * lo decidio quien declara la municipalidad y no PostgreSQL.
 */
@DisplayName("infrastructure#73 salida 1 — el id de la municipalidad es el DECLARADO")
class RegistroDeMunicipalidadesJdbcTest {

    /** Un ubigeo que el arnes NO siembra: aqui se ejerce el camino de CREAR la fila. */
    private static final String UBIGEO_NUEVO = "150101";

    private static ArnesDeAdministracion arnes;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    private RegistroDeMunicipalidadesJdbc registro() {
        return new RegistroDeMunicipalidadesJdbc(
                arnes.base().url(),
                BaseDeDatosDePrueba.OWNER,
                arnes.base().clave(BaseDeDatosDePrueba.OWNER));
    }

    @Test
    void laFilaSeEscribeConElIdDeclaradoYNoConElDeLaSecuencia() throws SQLException {
        long dado = registro().darDeAltaSiFalta(UBIGEO_NUEVO, 42L, "Lima", "PROVINCIAL", false);

        assertThat(dado).as("el registro devolvio un id distinto del declarado").isEqualTo(42L);
        assertThat(idEnLaBase(UBIGEO_NUEVO))
                .as(
                        "la fila se escribio con el id que la SECUENCIA asigno y no con el"
                                + " declarado. De ese id sale el claim `municipalidad_id` de todo"
                                + " token, asi que un id que el archivo versionado no conoce deja al"
                                + " RLS escondiendo las filas de esta municipalidad: 403 «la cuenta no"
                                + " esta dada de alta» CON LA FILA DELANTE (infrastructure#73)")
                .isEqualTo(42L);
    }

    @Test
    void repetirloconElMismoIdEsIdempotente() throws SQLException {
        registro().darDeAltaSiFalta(UBIGEO_NUEVO, 42L, "Lima", "PROVINCIAL", false);
        long segunda = registro().darDeAltaSiFalta(UBIGEO_NUEVO, 42L, "Lima", "PROVINCIAL", false);

        assertThat(segunda).isEqualTo(42L);
        assertThat(cuantasFilas(UBIGEO_NUEVO))
                .as("reimplantar duplico la fila: un despliegue repetido no puede crecer el estado")
                .isEqualTo(1);
    }

    @Test
    void siLaFilaExisteConOtroIdFallaNombrandoLosDos() throws SQLException {
        registro().darDeAltaSiFalta(UBIGEO_NUEVO, 42L, "Lima", "PROVINCIAL", false);

        Throwable salio =
                catchThrowable(
                        () ->
                                registro()
                                        .darDeAltaSiFalta(
                                                UBIGEO_NUEVO, 7L, "Lima", "PROVINCIAL", false));

        // `catchThrowable` y no `assertThatThrownBy`, que revienta antes de aplicar la
        // descripcion cuando no se lanza nada: es la leccion de `rentas`#40.
        assertThat(salio)
                .as(
                        "dar de alta con un id declarado distinto del que la fila tiene paso sin"
                                + " protestar. Ese id es el inquilino del que cuelga el RLS de TODAS"
                                + " las tablas, asi que seguir deja el claim apuntando a un inquilino"
                                + " sin una sola fila — y el RLS no lo delata, porque la base hace"
                                + " exactamente lo que se le pide")
                .isInstanceOf(IllegalStateException.class);
        assertThat(salio.getMessage())
                .as("el mensaje tiene que nombrar LOS DOS numeros, o no se sabe cual cambiar")
                .contains("ya esta dada de alta con el id 42")
                .contains("lo declarado es 7");
        assertThat(idEnLaBase(UBIGEO_NUEVO))
                .as("y la fila no se toco: cambiar ese id dejaria huerfana cada fila de la base")
                .isEqualTo(42L);
    }

    /**
     * Y la secuencia queda por encima del id escrito.
     *
     * <p>Insertar un id explicito NO la avanza, asi que sin el {@code setval} un {@code INSERT}
     * posterior que si la use pediria un valor ya ocupado y fallaria con una violacion de clave
     * primaria <b>mucho despues y en otro sitio</b> — con un mensaje que habla de una clave
     * duplicada y no de quien escribio el id a mano.
     */
    @Test
    void laSecuenciaQuedaPorEncimaDelIdEscrito() throws SQLException {
        registro().darDeAltaSiFalta(UBIGEO_NUEVO, 42L, "Lima", "PROVINCIAL", false);

        assertThat(siguienteDeLaSecuencia())
                .as(
                        "la secuencia se quedo por debajo del id escrito a mano: el siguiente"
                                + " `INSERT` que la use pedira un valor ocupado y fallara con una"
                                + " violacion de clave primaria que no dice de donde viene")
                .isGreaterThan(42L);
    }

    private long idEnLaBase(String ubigeo) throws SQLException {
        try (Connection admin = arnes.base().conexionAdmin();
                PreparedStatement consulta =
                        admin.prepareStatement("SELECT id FROM municipalidad WHERE ubigeo = ?")) {
            consulta.setString(1, ubigeo);
            try (ResultSet fila = consulta.executeQuery()) {
                assertThat(fila.next()).as("no hay fila para el ubigeo " + ubigeo).isTrue();
                return fila.getLong(1);
            }
        }
    }

    private int cuantasFilas(String ubigeo) throws SQLException {
        try (Connection admin = arnes.base().conexionAdmin();
                PreparedStatement consulta =
                        admin.prepareStatement(
                                "SELECT count(*) FROM municipalidad WHERE ubigeo = ?")) {
            consulta.setString(1, ubigeo);
            try (ResultSet fila = consulta.executeQuery()) {
                fila.next();
                return fila.getInt(1);
            }
        }
    }

    private long siguienteDeLaSecuencia() throws SQLException {
        try (Connection admin = arnes.base().conexionAdmin();
                Statement sentencia = admin.createStatement();
                ResultSet fila =
                        sentencia.executeQuery(
                                "SELECT nextval(pg_get_serial_sequence('municipalidad', 'id'))")) {
            fila.next();
            return fila.getLong(1);
        }
    }
}
