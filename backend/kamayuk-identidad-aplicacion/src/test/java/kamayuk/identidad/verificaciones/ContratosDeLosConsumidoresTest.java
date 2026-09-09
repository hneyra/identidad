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
 * Lo que sostiene los cuatro {@code @Disabled} de las pruebas de contrato.
 *
 * <h2>Por que existe</h2>
 *
 * <p>{@link ContratoConRentasTest} y sus tres hermanas estan desactivadas porque el archivo que
 * leen —{@code <consumidor>/docs/50-api/contratos-que-consume/identidad.json}— lo publica el
 * ingestor de cada consumidor, que es de la <b>etapa 4</b>. Se midio antes de decidirlo: {@code
 * ContratoDelConsumidor.leer} <b>lanza</b> nombrando el archivo <b>antes</b> de que la base
 * consulte {@code desajustesVivos()}, asi que ese gancho no puede declarar «el consumidor todavia
 * no publica su contrato» como deuda con nombre. El rojo literal de esa medicion:
 *
 * <pre>
 * No esta «…/rentas/docs/50-api/contratos-que-consume/identidad.json», asi que no se puede
 * comprobar que este backend siga cumpliendo lo que su consumidor espera.
 * </pre>
 *
 * <p>Una prueba desactivada sin nada que la reactive es una prueba borrada con mas lineas. Esto es
 * lo que la reactiva: <b>afirma que el archivo sigue sin existir</b>, de modo que el dia que un
 * consumidor lo publique esta guarda sale roja pidiendo que se quite el {@code @Disabled} que le
 * corresponde. No es una afirmacion sobre lo que este repositorio hace: es la lista de trabajo de
 * la etapa 4, escrita donde se pone roja sola.
 *
 * <h2>Las tres cosas que hacen que muerda, y ninguna sobra</h2>
 *
 * <ol>
 *   <li><b>Los clones tienen que estar.</b> Sin ellos, «el archivo no existe» es cierto porque no
 *       existe el repositorio entero, y esta guarda se cumpliria sola para siempre. Falla nombrando
 *       lo que falta — «no se pudo comprobar» no es «esta bien».
 *   <li><b>El contraste de la ruta.</b> Los tres consumidores que ya publican contratos de OTROS
 *       proveedores tienen que seguir haciendolo: sin esto, una errata en la ruta —un {@code
 *       contrato-que-consume} en singular— haria que «no publica» fuera trivialmente cierto y nadie
 *       se enterara nunca.
 *   <li><b>Las cuatro pruebas tienen que existir y llevar su {@code @Disabled}.</b> Si alguien las
 *       borrara, quitar la anotacion dejaria de ser el remedio y este archivo estaria pidiendo algo
 *       que no se puede hacer.
 * </ol>
 */
@DisplayName("Etapa 4 — los cuatro contratos que todavia no existen, y lo que los espera")
class ContratosDeLosConsumidoresTest {

    /** Los cuatro que consumiran el buzon. {@code identidad} no se consume a si mismo. */
    private static final List<String> CONSUMIDORES =
            List.of("rentas", "catastro", "normativa", "caja");

    /** Donde cada consumidor publica lo que le pide a un proveedor (ADR-0030 §4). */
    private static final String CARPETA = "docs/50-api/contratos-que-consume";

    /**
     * Contratos que <b>si</b> existen hoy, y que fijan que la ruta de arriba es la buena.
     *
     * <p>Medido el 2026-09-09 sobre los cuatro clones: {@code rentas} publica los tres de sus
     * proveedores, {@code catastro} el de {@code normativa} y {@code caja} el de {@code rentas}.
     * Son <b>tres clones de los cuatro</b> a proposito: con uno solo, apartar ese clon dejaria el
     * contraste sin sujeto y sin decirlo. Sin esta lista, una errata en {@link #CARPETA} volveria
     * trivialmente cierta la afirmacion de esta clase.
     */
    private static final List<String> QUE_SI_PUBLICAN_HOY =
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
                        "sin el clon, «no publica su contrato» es cierto porque no existe el"
                                + " repositorio entero, y esta guarda se cumpliria sola para"
                                + " siempre. Los clones son HERMANOS: `git clone"
                                + " https://github.com/hneyra/<sistema>` al lado de este, y en CI"
                                + " los trae `backend.yml` con `sparse-checkout: "
                                + CARPETA
                                + "`")
                .isEmpty();
    }

    @Test
    @DisplayName("y la ruta de los contratos es la buena: hay cinco que si existen")
    void laRutaDeLosContratosEsLaBuena() {
        List<String> queFaltan = new ArrayList<>();
        for (String contrato : QUE_SI_PUBLICAN_HOY) {
            if (!Files.isRegularFile(raizDeLosClones().resolve(contrato))) {
                queFaltan.add(contrato);
            }
        }
        assertThat(queFaltan)
                .as(
                        "[el contraste] estos contratos existen desde C-1 y P5B/P5C. Si dejan de"
                                + " encontrarse, lo que ha cambiado no es que nadie publique nada:"
                                + " es la ruta con la que esta clase mira — y entonces «ningun"
                                + " consumidor publica su contrato con identidad» seria"
                                + " trivialmente cierto")
                .isEmpty();
    }

    @Test
    @DisplayName("ninguno publica todavia su contrato con identidad (etapa 4)")
    void ningunoPublicaTodaviaSuContratoConIdentidad() {
        List<String> yaPublican = new ArrayList<>();
        for (String consumidor : CONSUMIDORES) {
            if (Files.isRegularFile(
                    raizDeLosClones().resolve(consumidor + "/" + CARPETA + "/identidad.json"))) {
                yaPublican.add(consumidor);
            }
        }
        assertThat(yaPublican)
                .as(
                        "[esta es la lista de trabajo de la etapa 4, y este rojo es su remedio] uno"
                                + " o mas consumidores YA publican lo que le piden a este buzon,"
                                + " asi que su prueba de contrato ya se puede correr: hay que"
                                + " quitarle el @Disabled a ContratoCon<Consumidor>Test y sacar ese"
                                + " nombre de esta lista. Dejarlo aqui deja la comprobacion"
                                + " desactivada sobre un contrato que existe, que es el unico"
                                + " momento en que podria empezar a romperse sin que nadie lo vea")
                .isEmpty();
    }

    /**
     * Y las cuatro pruebas siguen ahi, con su anotacion.
     *
     * <p>Se lee el fuente y no se pregunta a JUnit: lo que hace falta afirmar es que la anotacion
     * {@code @Disabled} <b>este escrita</b>, y con el nombre del consumidor dentro, para que
     * quitarla sea el remedio que la prueba de arriba nombra. Preguntandole a JUnit por una clase
     * desactivada se sabria que no corre, que es justo lo que no distingue «desactivada a
     * proposito» de «desaparecida».
     */
    @Test
    @DisplayName("y las cuatro pruebas de contrato existen, desactivadas y nombrando su etapa")
    void lasCuatroPruebasExistenDesactivadas() throws IOException {
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
            String texto = Files.readString(fuente, StandardCharsets.UTF_8);
            if (!texto.contains("@Disabled(")) {
                hallazgos.add(clase + ": ya no lleva @Disabled, y su contrato sigue sin existir");
            }
            if (!texto.contains("etapa 4: " + consumidor)) {
                hallazgos.add(clase + ": su @Disabled no dice que espera de la etapa 4");
            }
        }
        assertThat(hallazgos)
                .as(
                        "las cuatro tienen que seguir escritas: si se borraran, «quitar el"
                                + " @Disabled» dejaria de ser un remedio que nadie puede aplicar y"
                                + " la guarda de arriba estaria pidiendo algo imposible")
                .isEmpty();
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
