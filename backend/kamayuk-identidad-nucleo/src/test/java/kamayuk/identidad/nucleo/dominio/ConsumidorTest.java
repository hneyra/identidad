package kamayuk.identidad.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quien puede consumir el buzon, y como se sabe quien pregunta.
 *
 * <p>Es dominio puro: sin base de datos y sin Spring. Lo unico que sale del disco es el {@code
 * CHECK} de {@code V3}, y sale por lo que dice el epigrafe de mas abajo.
 */
@DisplayName("Consumidor — el que lee el buzon sale del azp, y son cuatro")
class ConsumidorTest {

    /** {@code consumidor IN ('rentas', 'catastro', …)} dentro de {@code V3}. */
    private static final Pattern DEL_CHECK =
            Pattern.compile("consumidor IN \\(([^)]+)\\)", Pattern.DOTALL);

    @Test
    @DisplayName("el azp de una cuenta de servicio dice de que sistema es")
    void elAzpDiceDeQueSistemaEs() {
        assertThat(Consumidor.deAzp("kamayuk-caja-servicio-200105")).isEqualTo(Consumidor.CAJA);
        assertThat(Consumidor.deAzp("kamayuk-rentas-servicio-200101"))
                .as("y el ubigeo no cambia quien es: cambia de que municipalidad")
                .isEqualTo(Consumidor.RENTAS);
        assertThat(Consumidor.deAzp("kamayuk-catastro-servicio-999999"))
                .isEqualTo(Consumidor.CATASTRO);
        assertThat(Consumidor.deAzp("kamayuk-normativa-servicio-200105"))
                .isEqualTo(Consumidor.NORMATIVA);
    }

    /**
     * Lo que NO es una cuenta de servicio, nombrado uno a uno.
     *
     * <p>Cada uno es una forma distinta de que un cliente diga quien es sin serlo, y los cuatro
     * tienen que fallar por el mismo sitio: no hay ninguno que se parezca lo bastante como para que
     * el borde se lo crea.
     */
    @Test
    @DisplayName("y lo que no lo es se rechaza, incluido el cliente del backoffice")
    void loQueNoEsUnaCuentaDeServicioSeRechaza() {
        for (String azp :
                List.of(
                        "kamayuk-backoffice",
                        "kamayuk-caja",
                        "kamayuk-caja-servicio",
                        "kamayuk-caja-servicio-20010",
                        "kamayuk-caja-servicio-abcdef",
                        "KAMAYUK-CAJA-SERVICIO-200105",
                        " ")) {
            assertThat(catchThrowable(() -> Consumidor.deAzp(azp)))
                    .as("«%s» no puede pasar por una cuenta de servicio", azp)
                    .isInstanceOf(Consumidor.NoEsUnaCuentaDeServicio.class);
        }
    }

    @Test
    @DisplayName("un token sin azp tampoco: no hay consumidor por omision")
    void sinAzpNoHayConsumidorPorOmision() {
        assertThat(catchThrowable(() -> Consumidor.deAzp(null)))
                .as(
                        "[un valor por omision aqui seria un acuse en nombre de otro producido por"
                                + " un descuido de configuracion]")
                .isInstanceOf(Consumidor.NoEsUnaCuentaDeServicio.class);
    }

    /**
     * {@code identidad} no se consume a si mismo, <b>y el mensaje lo explica</b>.
     *
     * <p>Se comprueba el texto y no solo el tipo: los otros rechazos mandan a mirar el emisor —«ese
     * cliente no tiene la forma»— y este manda a mirar el despliegue del consumidor, que es un
     * ingestor apuntado a su propio buzon. Con el mismo mensaje, quien lo reciba buscaria donde no
     * es.
     */
    @Test
    @DisplayName("y la cuenta de servicio de identidad se rechaza diciendo por que")
    void identidadNoSeConsumeASiMismo() {
        Throwable loQueSalio =
                catchThrowable(() -> Consumidor.deAzp("kamayuk-identidad-servicio-200105"));

        assertThat(loQueSalio)
                .isInstanceOf(Consumidor.NoEsUnaCuentaDeServicio.class)
                .hasMessageContaining("NO se consume a si mismo");
    }

    /**
     * Los cuatro de aqui son los cuatro del {@code CHECK} de {@code V3}.
     *
     * <p>Son dos sitios con la misma verdad y no se pueden juntar: uno es Java y el otro es el DDL
     * que corre el migrador. Lo que si se puede es que no discrepen en silencio, y esta prueba es
     * eso. El que se quedaria viejo sin ella seria justo el del motor, cuyo rojo llega en
     * produccion — y con la forma peor de todas: un {@code 23514} sin nombre de campo, dentro de la
     * transaccion de un acuse.
     */
    @Test
    @DisplayName("y son exactamente los cuatro que el CHECK de V3 admite")
    void losMismosQueElCheckDeV3() throws IOException {
        String v3 =
                Files.readString(
                        raizDelRepositorio()
                                .resolve(
                                        "backend/kamayuk-identidad-esquema/src/main/resources/db/"
                                                + "migration/V3__acuses_del_buzon.sql"),
                        StandardCharsets.UTF_8);
        Matcher clausula = DEL_CHECK.matcher(v3);
        assertThat(clausula.find())
                .as(
                        "V3 tiene que declarar «consumidor IN (…)». Sin la clausula esta"
                                + " comparacion no tiene con que comparar y se cumpliria sola")
                .isTrue();

        List<String> delCheck = new ArrayList<>();
        for (String pieza : clausula.group(1).split(",")) {
            delCheck.add(pieza.trim().replace("'", ""));
        }

        assertThat(delCheck)
                .as(
                        "el vocabulario del motor y el del borde tienen que ser el mismo conjunto."
                                + " `identidad` no esta en ninguno de los dos: no se consume a si"
                                + " mismo")
                .containsExactlyInAnyOrderElementsOf(Consumidor.sistemas());
    }

    /** Y los cuatro son sistemas del producto: ninguno inventado. */
    /**
     * Componer y volver a analizar: las dos direcciones de la misma forma.
     *
     * <p>{@code clienteDeServicio} existe desde la etapa 4 porque la implantacion tiene que dar de
     * alta la cuenta de servicio de cada consumidor, y componer esa forma en la implantacion habria
     * sido escribirla dos veces. Lo que esto mide es que no se puedan separar: lo que se compone
     * aqui es exactamente lo que {@code deAzp} admite, y la cuenta es ese cliente con el prefijo
     * que el emisor le pone. Si se separaran, la implantacion daria de alta cuatro filas de {@code
     * usuario} que ningun token nombra — y el sintoma es el 403 que la etapa 4 viene a cerrar, con
     * una fila mas en la tabla.
     */
    @Test
    @DisplayName("y la forma se compone y se vuelve a analizar: da el mismo consumidor")
    void componerYAnalizarDanLoMismo() {
        for (Consumidor consumidor : Consumidor.values()) {
            String cliente = consumidor.clienteDeServicio("200105");
            assertThat(Consumidor.deAzp(cliente)).isEqualTo(consumidor);
            assertThat(consumidor.cuentaDeServicio("200105"))
                    .as(
                            "la cuenta es «service-account-» mas el cliente, que es como la nombra el"
                                    + " emisor y lo que el guardia compara con `usuario.cuenta`")
                    .isEqualTo("service-account-" + cliente);
        }
        assertThat(Consumidor.CAJA.cuentaDeServicio("200105"))
                .as(
                        "[medido el 2026-09-09 contra el emisor de verdad: este es el literal que"
                                + " llega en el `preferred_username`, y el que salio en el 403 «La"
                                + " cuenta «service-account-kamayuk-normativa-servicio-200105» no"
                                + " esta dada de alta en este sistema»]")
                .isEqualTo("service-account-kamayuk-caja-servicio-200105");
    }

    @Test
    @DisplayName("y un ubigeo que no son seis digitos no compone una cuenta: se dice")
    void unUbigeoQueNoLoEsSeDice() {
        assertThat(catchThrowable(() -> Consumidor.RENTAS.clienteDeServicio("2001")))
                .as(
                        "dar de alta una cuenta que ningun token puede nombrar es peor que fallar:"
                                + " la implantacion saldria en verde y los cuatro consumidores"
                                + " seguirian recibiendo 403")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene la forma");
    }

    @Test
    @DisplayName("y los cuatro son sistemas del producto, y ninguno es identidad")
    void losCuatroSonSistemasDelProducto() {
        for (String sistema : Consumidor.sistemas()) {
            assertThat(SistemasDelProducto.esUnSistema(sistema)).isTrue();
        }
        assertThat(Consumidor.sistemas())
                .doesNotContain(SistemasDelProducto.IDENTIDAD)
                .hasSize(SistemasDelProducto.TODOS.size() - 1);
    }

    /**
     * La raiz del clon, subiendo hasta encontrar {@code .git}.
     *
     * <p>{@code Files.exists} y no {@code Files.isDirectory}: en un {@code git worktree} ese {@code
     * .git} es un <b>archivo</b>, y con {@code isDirectory} el recorrido sube hasta {@code /} y
     * esta prueba muere sin poder hablar de lo que vigila. Es el defecto que ya se cerro tres veces
     * en este producto.
     */
    private static Path raizDelRepositorio() {
        Path candidato = Path.of("").toAbsolutePath();
        while (candidato != null && !Files.exists(candidato.resolve(".git"))) {
            candidato = candidato.getParent();
        }
        if (candidato == null) {
            throw new IllegalStateException(
                    "No se encontro la raiz del repositorio subiendo desde "
                            + Path.of("").toAbsolutePath()
                            + ". Sin ella no se puede leer V3, y «no se pudo comprobar» no es «esta"
                            + " bien»");
        }
        return candidato;
    }
}
