package kamayuk.identidad.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que sostiene que las cuatro pruebas de contrato <b>corran</b>, y no solo que existan.
 *
 * <h2>De donde viene</h2>
 *
 * <p>En la etapa 3 esta clase afirmaba lo contrario: que <b>ningun</b> consumidor publicaba todavia
 * {@code <consumidor>/docs/50-api/contratos-que-consume/identidad.json} y que {@link
 * ContratoConRentasTest} y sus tres hermanas llevaban su {@code @Disabled}. Era la lista de trabajo
 * de la etapa 4 escrita donde se pone roja sola, y se puso: el 2026-09-09, con los cuatro clones
 * hermanos en su rama de la etapa 4, salio con «Expecting empty but was: ["rentas", "catastro",
 * "normativa", "caja"]» — los cuatro a la vez, porque los cuatro consumidores se construyeron el
 * mismo dia. Ese rojo es el que esta clase existia para producir, y su remedio es esta version.
 *
 * <h2>Lo que afirma desde la etapa 4, y por que sigue haciendo falta</h2>
 *
 * <p>Que los cuatro archivos <b>estan</b>, y que las cuatro pruebas que los leen <b>no llevan
 * {@code @Disabled}</b>. Parece redundante con correr las cuatro, y no lo es: una prueba
 * desactivada no falla, <b>se salta</b>, y JUnit lo cuenta como {@code SKIPPED} en un build que
 * sigue en verde. Si alguien devolviera la anotacion a una de ellas —«mientras arreglo el
 * contrato»— el buzon podria dejar de publicar un campo que ese consumidor lee sin que nada de este
 * repositorio lo dijera. Y si un consumidor retirara su archivo, {@code ContratoDelConsumidor.leer}
 * lanzaria nombrandolo desde su propia prueba; aqui se dice ademas <b>de quien</b> es y <b>que</b>
 * hace falta, porque ese rojo cae en un repositorio que no es el que retiro el archivo.
 *
 * <h2>Las tres cosas que hacen que muerda, y ninguna sobra</h2>
 *
 * <ol>
 *   <li><b>Los clones tienen que estar.</b> Sin ellos, «el archivo no existe» seria cierto porque
 *       no existe el repositorio entero, y el rojo mandaria a mirar al consumidor cuando lo que
 *       falta es un {@code git clone}. Falla nombrando lo que falta — «no se pudo comprobar» no es
 *       «esta bien».
 *   <li><b>El contraste de la ruta.</b> Los tres consumidores que publican contratos de OTROS
 *       proveedores tienen que seguir haciendolo: sin esto, una errata en la ruta —un {@code
 *       contrato-que-consume} en singular— haria que los cuatro «no publican» a la vez, y el rojo
 *       acusaria a cuatro repositorios de algo que paso en este.
 *   <li><b>Las cuatro pruebas tienen que existir, activas y apuntando a su consumidor.</b> Si
 *       alguien las borrara o las desactivara, el archivo del consumidor seguiria existiendo y
 *       nadie lo compararia con lo que {@code EventosController} publica.
 * </ol>
 */
@DisplayName("Etapa 4 — los cuatro contratos existen, y sus cuatro pruebas corren")
class ContratosDeLosConsumidoresTest {

    /** Los cuatro que consumen el buzon. {@code identidad} no se consume a si mismo. */
    private static final List<String> CONSUMIDORES =
            List.of("rentas", "catastro", "normativa", "caja");

    /** Donde cada consumidor publica lo que le pide a un proveedor (ADR-0030 §4). */
    private static final String CARPETA = "docs/50-api/contratos-que-consume";

    /**
     * Contratos con OTROS proveedores que existen desde antes, y que fijan que la ruta de arriba es
     * la buena.
     *
     * <p>Medido el 2026-09-09 sobre los cuatro clones: {@code rentas} publica los tres de sus
     * proveedores, {@code catastro} el de {@code normativa} y {@code caja} el de {@code rentas}.
     * Son <b>tres clones de los cuatro</b> a proposito: con uno solo, apartar ese clon dejaria el
     * contraste sin sujeto y sin decirlo. Sin esta lista, una errata en {@link #CARPETA} pondria
     * rojos los cuatro «publica su contrato» a la vez, acusando a los consumidores de algo que paso
     * aqui.
     */
    private static final List<String> QUE_PUBLICAN_PARA_OTROS =
            List.of(
                    "rentas/" + CARPETA + "/catastro.json",
                    "rentas/" + CARPETA + "/normativa.json",
                    "rentas/" + CARPETA + "/caja.json",
                    "catastro/" + CARPETA + "/normativa.json",
                    "caja/" + CARPETA + "/rentas.json");

    @Test
    @DisplayName("los cuatro clones hermanos estan: sin ellos esto no mide nada")
    void losCuatroClonesEstan() {
        List<String> queFaltan = new ArrayList<>();
        for (String consumidor : CONSUMIDORES) {
            if (!Files.isDirectory(raizDeLosClones().resolve(consumidor))) {
                queFaltan.add(consumidor);
            }
        }
        assertThat(queFaltan)
                .as(
                        "sin el clon, «no publica su contrato» seria cierto porque no existe el"
                                + " repositorio entero, y el rojo mandaria a mirar al consumidor"
                                + " cuando lo que falta es un clon. Los clones son HERMANOS: `git"
                                + " clone https://github.com/hneyra/<sistema>` al lado de este, y"
                                + " en CI los trae `backend.yml` con `sparse-checkout: "
                                + CARPETA
                                + "`")
                .isEmpty();
    }

    @Test
    @DisplayName("y la ruta de los contratos es la buena: hay cinco de otros proveedores")
    void laRutaDeLosContratosEsLaBuena() {
        List<String> queFaltan = new ArrayList<>();
        for (String contrato : QUE_PUBLICAN_PARA_OTROS) {
            if (!Files.isRegularFile(raizDeLosClones().resolve(contrato))) {
                queFaltan.add(contrato);
            }
        }
        assertThat(queFaltan)
                .as(
                        "[el contraste] estos contratos existen desde C-1 y P5B/P5C. Si dejan de"
                                + " encontrarse, lo que ha cambiado no es que nadie publique nada:"
                                + " es la ruta con la que esta clase mira — y entonces los cuatro"
                                + " «no publica su contrato con identidad» saldrian a la vez por"
                                + " un defecto de aqui")
                .isEmpty();
    }

    @Test
    @DisplayName("los cuatro publican su contrato con identidad (etapa 4)")
    void losCuatroPublicanSuContratoConIdentidad() {
        List<String> noPublican = new ArrayList<>();
        for (String consumidor : CONSUMIDORES) {
            if (!Files.isRegularFile(
                    raizDeLosClones().resolve(consumidor + "/" + CARPETA + "/identidad.json"))) {
                noPublican.add(consumidor);
            }
        }
        assertThat(noPublican)
                .as(
                        "[este rojo cae AQUI y no en el consumidor, y por eso dice de quien es]"
                                + " estos consumidores tienen su ingestor desde la etapa 4 y ya no"
                                + " publican lo que le piden a este buzon en "
                                + CARPETA
                                + "/identidad.json. Sin ese archivo, su ContratoCon<Consumidor>Test"
                                + " no puede comparar nada y un campo que este controlador retire"
                                + " deja a ese ingestor leyendo un nulo. Remedio: en el clon del"
                                + " consumidor, regenerar el contrato con su generador"
                                + " (ContratoQueConsumeDeIdentidad) — y si el clon esta atrasado,"
                                + " traerlo a `main`")
                .isEmpty();
    }

    /**
     * Y las cuatro pruebas siguen ahi, <b>activas</b> y apuntando a su consumidor.
     *
     * <p>Se lee el fuente y no se pregunta a JUnit, por lo mismo que en la etapa 3 pero al reves:
     * preguntandole a JUnit por una clase desactivada solo se sabria que no corrio, y eso es justo
     * lo que no distingue «desactivada a proposito» de «desaparecida». Aqui lo que se exige es que
     * la anotacion <b>no este escrita</b> —ni {@code @Disabled} ni su {@code import}, para que
     * devolverla no sea una linea— y que {@code consumidor()} devuelva el nombre del clon, que es
     * lo que ata la clase con el archivo que la prueba de arriba exige.
     *
     * <p><b>Y se lee sin comentarios</b>, porque el javadoc de cada una de las cuatro nombra la
     * anotacion para explicar por que ya no la lleva: una guarda que se dispara con la prosa que la
     * justifica es la que alguien acaba apagando borrando el comentario (#42, #16).
     */
    @Test
    @DisplayName("y las cuatro pruebas de contrato existen, activas y apuntando a su consumidor")
    void lasCuatroPruebasExistenActivas() throws IOException {
        List<String> hallazgos = new ArrayList<>();
        for (String consumidor : CONSUMIDORES) {
            String clase =
                    "ContratoCon"
                            + Character.toUpperCase(consumidor.charAt(0))
                            + consumidor.substring(1)
                            + "Test.java";
            Path fuente =
                    RaizDelRepositorio.ruta()
                            .resolve(
                                    "backend/kamayuk-identidad-aplicacion/src/test/java/kamayuk/"
                                            + "identidad/verificaciones/"
                                            + clase);
            if (!Files.isRegularFile(fuente)) {
                hallazgos.add(clase + ": no existe");
                continue;
            }
            String texto = sinComentarios(Files.readString(fuente, StandardCharsets.UTF_8));
            if (texto.contains("@Disabled") || texto.contains("org.junit.jupiter.api.Disabled")) {
                hallazgos.add(
                        clase
                                + ": lleva @Disabled, y el contrato de "
                                + consumidor
                                + " existe: una prueba desactivada no falla, se salta, y el build"
                                + " sigue en verde mientras este buzon deja de publicar lo que ese"
                                + " ingestor lee");
            }
            if (!texto.contains("return \"" + consumidor + "\";")) {
                hallazgos.add(
                        clase
                                + ": su consumidor() no devuelve «"
                                + consumidor
                                + "», asi que no lee el archivo que la prueba de arriba exige");
            }
        }
        assertThat(hallazgos)
                .as(
                        "las cuatro tienen que seguir escritas y activas: el archivo del consumidor"
                                + " existe, y si nadie lo compara con lo que EventosController"
                                + " publica, retirar un campo pasa en verde aqui y revienta alli")
                .isEmpty();
    }

    /** Los comentarios de bloque y de linea en blanco, para que la prosa no cuente como codigo. */
    private static String sinComentarios(String fuente) {
        return fuente.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** El directorio que contiene los clones, que son hermanos de este. */
    private static Path raizDeLosClones() {
        Path padre = RaizDelRepositorio.ruta().getParent();
        if (padre == null) {
            throw new IllegalStateException(
                    "El clon de este repositorio esta en la raiz del sistema de archivos, asi que"
                            + " no tiene hermanos donde buscar a los consumidores");
        }
        return padre;
    }
}
