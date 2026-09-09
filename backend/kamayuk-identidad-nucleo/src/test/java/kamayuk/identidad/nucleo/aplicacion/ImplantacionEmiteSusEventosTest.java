package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import kamayuk.identidad.esquema.BaseDeDatosDePrueba;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.TipoDeEventoDeIdentidad;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import kamayuk.identidad.nucleo.infraestructura.RegistroDeMunicipalidadesJdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-6: la implantacion escribe al administrador por el mismo camino que una pantalla, asi que
 * <b>emite sus eventos</b>.
 *
 * <h2>Que se estaria midiendo sin esto</h2>
 *
 * <p>Hasta la etapa 1 el administrador lo escribia {@code SembradorDeLaCopiaLocal} con cuatro
 * {@code INSERT} directos. Con el buzon puesto, eso habria seguido funcionando —la municipalidad
 * quedaba implantada, el administrador podia entrar aqui— y <b>no habria dejado ni un evento</b>:
 * los otros cuatro sistemas no conocerian la unica cuenta que puede entrar el primer dia, y su
 * guardia le negaria todo. El sintoma es un 403 en {@code caja} para quien acaba de implantar la
 * municipalidad, y no se parece a su causa.
 *
 * <p>Por eso lo que esta prueba cuenta son <b>los eventos</b> y no las filas: las filas estaban
 * antes tambien.
 */
@DisplayName("AC-6 — la implantacion pasa por los casos de uso y emite sus eventos")
class ImplantacionEmiteSusEventosTest {

    private static final String UBIGEO = "260201";
    private static final String ADMINISTRADOR = "administrador";

    private static ArnesDeAdministracion arnes;
    private static CatalogoUnido catalogo;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        catalogo = CatalogoUnidoDelJar.leer();
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    @Test
    @DisplayName("el administrador, su grupo, su afiliacion y sus permisos salen por el buzon")
    void laImplantacionEmiteSusEventos() throws SQLException {
        implantar();

        assertThat(tipos())
                .as(
                        "la implantacion tiene que dejar los cuatro hechos del arranque en frio."
                                + " Escribirlos con SQL directo —como hacia la etapa 1— deja la"
                                + " municipalidad implantada aqui y a los otros cuatro sistemas sin"
                                + " conocer a nadie, en silencio")
                .contains(
                        TipoDeEventoDeIdentidad.GRUPO_DADO_DE_ALTA.name(),
                        TipoDeEventoDeIdentidad.USUARIO_DADO_DE_ALTA.name(),
                        TipoDeEventoDeIdentidad.MIEMBRO_AFILIADO.name(),
                        TipoDeEventoDeIdentidad.PERMISO_FIJADO.name());

        long opciones = catalogo.opciones().size();
        assertThat(arnes.contar(contarEventos(TipoDeEventoDeIdentidad.PERMISO_FIJADO)))
                .as(
                        "un PERMISO_FIJADO por cada opcion de los CINCO catalogos —son %d, y no las"
                                + " siete de este sistema: lo que esta base guarda es a quien se le"
                                + " concede cada opcion de todos (ADR-0039)— MAS UNO, el de"
                                + " «%s» sobre (identidad, eventos). Ese uno es de la etapa 3 y"
                                + " tiene que emitirse igual: el dia que ese grupo tenga miembros,"
                                + " los otros cuatro sistemas necesitan conocerlo para autorizar a"
                                + " las cuentas de servicio contra su propia copia",
                        opciones, ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES)
                .isEqualTo(opciones + 1);

        assertThat(arnes.contar("SELECT count(*) FROM acceso"))
                .as("y una fila de `acceso` por cada una, con su sistema")
                .isEqualTo(opciones);

        assertThat(arnes.filas("SELECT DISTINCT sistema FROM acceso ORDER BY 1"))
                .as("los cinco catalogos sembrados, no solo el de aqui")
                .containsExactly("caja", "catastro", "identidad", "normativa", "rentas");

        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM permiso p"
                                        + " JOIN acceso a ON a.id = p.acceso_id"
                                        + " WHERE a.sistema = 'identidad' AND a.codigo = 'permisos'"
                                        + "   AND p.registro"))
                .as(
                        "y el primero que se fija es el que gobierna esta misma pantalla: sin el,"
                                + " la guarda del ultimo administrador rechazaria el segundo permiso"
                                + " con un 409 y la implantacion entera se desharia")
                .isEqualTo(1);

        // El grupo de la etapa 3 existe, con SU opcion y sin miembros. Las tres cosas: sin la
        // primera nadie podria consumir el buzon; sin la segunda —si recibiera los siete sobre
        // todo, como el de administracion— seria un segundo administrador de la municipalidad
        // creado por el despliegue; y la tercera es lo que dice que afiliar las cuatro cuentas de
        // servicio es de la etapa 4 y no un olvido de esta.
        assertThat(
                        arnes.filas(
                                "SELECT a.sistema || ':' || a.codigo FROM permiso p"
                                        + " JOIN acceso a ON a.id = p.acceso_id"
                                        + " JOIN grupo g ON g.id = p.grupo_id"
                                        + " WHERE g.nombre = '"
                                        + ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES
                                        + "' ORDER BY 1"))
                .as(
                        "«%s» recibe UNA opcion, la del buzon",
                        ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES)
                .containsExactly("identidad:eventos");

        assertThat(
                        arnes.contar(
                                "SELECT count(*) FROM miembro m JOIN grupo g ON g.id = m.grupo_id"
                                        + " WHERE g.nombre = '"
                                        + ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES
                                        + "'"))
                .as(
                        "y nace SIN MIEMBROS: afiliar las cuatro cuentas de servicio es de la etapa"
                                + " 4 y del despliegue, porque hoy ninguna tiene fila en `usuario`."
                                + " Un miembro aqui hoy seria una cuenta que no existe")
                .isZero();

        segundoDespliegue();
    }

    /**
     * Y correrla dos veces no duplica nada ni falla.
     *
     * <p>Va dentro de la misma prueba y no en otra a proposito: las dos comparten una base que la
     * primera deja sembrada, y como metodos separados el orden en que JUnit los ejecute cambiaria
     * lo que la otra mide — que es la forma de que una prueba pase por el orden y no por lo que
     * afirma.
     */
    private void segundoDespliegue() throws SQLException {
        long eventosTrasLaPrimera = arnes.contar("SELECT count(*) FROM identidad_evento");
        long permisosTrasLaPrimera = arnes.contar("SELECT count(*) FROM permiso");

        implantar();

        assertThat(arnes.contar("SELECT count(*) FROM permiso"))
                .as("un segundo despliegue no crea un segundo permiso sobre la misma opcion")
                .isEqualTo(permisosTrasLaPrimera);
        assertThat(arnes.contar("SELECT count(*) FROM usuario"))
                .as("ni un segundo administrador")
                .isEqualTo(1);
        assertThat(arnes.contar("SELECT count(*) FROM grupo"))
                .as(
                        "ni un segundo grupo: siguen siendo los DOS que la implantacion crea, «%s»"
                                + " y «%s»",
                        ImplantarMunicipalidad.GRUPO_DE_ADMINISTRACION,
                        ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES)
                .isEqualTo(2);

        // Los eventos SI crecen, y es correcto que crezcan: fijar la misma matriz otra vez es OTRO
        // acto, con su observacion y su fila de auditoria (por eso el `evento_id` es aleatorio y no
        // derivado del contenido, al reves que en `catastro`). Lo que no puede crecer es el estado.
        assertThat(arnes.contar("SELECT count(*) FROM identidad_evento"))
                .as("el buzon guarda actos, no estados: repetir la implantacion son mas actos")
                .isGreaterThan(eventosTrasLaPrimera);
    }

    private void implantar() {
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
                                ADMINISTRADOR,
                                "Administrador del Sistema",
                                false,
                                "implantacion"))
                .run(new org.springframework.boot.DefaultApplicationArguments());
    }

    private static String contarEventos(TipoDeEventoDeIdentidad tipo) {
        return "SELECT count(*) FROM identidad_evento WHERE tipo = '" + tipo.name() + "'";
    }

    private List<String> tipos() throws SQLException {
        return arnes.filas("SELECT DISTINCT tipo FROM identidad_evento ORDER BY 1");
    }
}
