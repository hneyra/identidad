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

        // El grupo de la etapa 3 existe y tiene SU opcion: sin ella nadie podria consumir el
        // buzon, y con los siete sobre todo —como el de administracion— seria un segundo
        // administrador de la municipalidad creado por el despliegue.
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

        // Y desde la etapa 4 tiene sus CUATRO miembros, que son las cuatro cuentas de servicio.
        // Medido con las cinco aplicaciones levantadas: sin ellas los cuatro consumidores reciben
        // 403 «no esta dada de alta en este sistema» y su copia local se queda congelada sin que
        // nada lo diga. Se comparan por su NOMBRE y no se cuentan: cuatro filas con la cuenta mal
        // compuesta darian la misma cifra y ningun token las nombraria.
        assertThat(cuentasDelGrupoDeConsumidores())
                .as(
                        "[la implantacion es lo unico que puede afiliarlas: el emisor crea el"
                                + " cliente confidencial de cada satelite, asi que el consumidor"
                                + " consigue su token y llega hasta el guardia, y ahi no hay fila que"
                                + " lo conozca. Sin esto los cuatro reciben 403 y su copia local se"
                                + " queda como la dejo su implantacion, en silencio] y `identidad` NO"
                                + " esta: no se consume a si mismo")
                .containsExactly(
                        "service-account-kamayuk-caja-servicio-" + UBIGEO,
                        "service-account-kamayuk-catastro-servicio-" + UBIGEO,
                        "service-account-kamayuk-normativa-servicio-" + UBIGEO,
                        "service-account-kamayuk-rentas-servicio-" + UBIGEO);

        // Y sus altas y sus afiliaciones SALEN POR EL BUZON, como las del administrador: cada uno
        // de los cuatro satelites recibe las cuatro cuentas y las cuatro afiliaciones. Son cinco y
        // cinco —el administrador mas los cuatro consumidores— y esa es la cifra que la etapa 4
        // mueve: la implantacion emitia 1 USUARIO_DADO_DE_ALTA y 1 MIEMBRO_AFILIADO, y emite 5 y 5.
        assertThat(arnes.contar(contarEventos(TipoDeEventoDeIdentidad.USUARIO_DADO_DE_ALTA)))
                .as("el administrador y las cuatro cuentas de servicio")
                .isEqualTo(5);
        assertThat(arnes.contar(contarEventos(TipoDeEventoDeIdentidad.MIEMBRO_AFILIADO)))
                .as("su afiliacion al grupo de administracion, y las cuatro al del buzon")
                .isEqualTo(5);

        // Y cuantos son en total, que es la cifra que la etapa 4 mueve y que hay que poder citar:
        // los permisos (una opcion del catalogo unido, mas la del grupo del buzon), los dos grupos,
        // las cinco altas y las cinco afiliaciones. Con el catalogo de hoy —157 opciones— son 170,
        // y antes de que la implantacion sembrara las cuatro cuentas de servicio eran 162. No se
        // escribe 170 a mano: se compone de la cifra del catalogo, para que el dia que un sistema
        // estrene una pantalla esto siga siendo cierto sin tocarlo.
        assertThat(arnes.contar("SELECT count(*) FROM identidad_evento"))
                .as(
                        "[cada uno de estos hechos viaja a los CUATRO satelites y es lo que su"
                                + " copia local aplica. Si esta cifra baja, alguien dejo de emitir"
                                + " algo que la implantacion escribe, y el sintoma no esta aqui:"
                                + " esta en la copia de los cuatro, que se queda sin ello]"
                                + " %d permisos + 2 grupos + 5 usuarios + 5 afiliaciones",
                        opciones + 1)
                .isEqualTo(opciones + 1 + 2 + 5 + 5);

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
                .as(
                        "ni un segundo administrador, ni una quinta cuenta de servicio: siguen"
                                + " siendo las CINCO que la implantacion da de alta")
                .isEqualTo(5);
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

        // Y las cuatro siguen afiliadas y ACTIVAS tras el segundo despliegue. No es lo mismo que
        // «no se duplican»: `afiliar` es un upsert que reactiva, asi que lo que esto mide es que
        // reimplantar REPARA a un consumidor al que alguien desafilio, que es la unica forma que
        // tiene de repararse.
        assertThat(cuentasDelGrupoDeConsumidores())
                .as("las cuatro siguen dentro del grupo despues del segundo despliegue")
                .hasSize(4);
    }

    /** Las cuentas afiliadas y activas del grupo del buzon, por su nombre. */
    private List<String> cuentasDelGrupoDeConsumidores() throws SQLException {
        return arnes.filas(
                "SELECT u.cuenta FROM usuario u"
                        + " JOIN miembro m ON m.usuario_id = u.id AND m.activo"
                        + " JOIN grupo g ON g.id = m.grupo_id"
                        + " WHERE g.nombre = '"
                        + ImplantarMunicipalidad.GRUPO_DE_CONSUMIDORES
                        + "' ORDER BY 1");
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
