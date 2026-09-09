package kamayuk.identidad.seguridad.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El catalogo de este sistema es <b>exactamente</b> lo que sus endpoints exigen.
 *
 * <h2>La etapa 1 la escribio al reves, y decia por que</h2>
 *
 * <p>Sin capa web, la comparacion de los otros cuatro se cumpliria sobre el conjunto vacio, asi que
 * lo que esta prueba afirmaba era el <b>estado de partida</b>: que los endpoints eran cero. Su
 * mensaje decia que el primer controlador la pondria roja y que entonces tocaba traer la
 * comparacion de los dos sentidos. Es lo que la etapa 2 hace: los cuatro controladores de {@code
 * kamayuk.identidad.nucleo} declaran sus {@code @RequiereAcceso} y esta prueba vuelve a ser la de
 * los otros cuatro sistemas, letra por letra.
 *
 * <p>{@link CatalogoDelSistema} es una lista escrita, y una lista escrita se desincroniza. Lo que
 * la ata a la realidad es esto: recorre {@code src/main} de todo el repositorio, junta los valores
 * de {@code @RequiereAcceso(acceso = "...")} y exige que sean los mismos codigos.
 *
 * <p>Los dos sentidos importan, y por motivos distintos:
 *
 * <ul>
 *   <li><b>Un acceso que el catalogo no tiene</b> es una pantalla a la que nadie puede dar permiso
 *       —el guardia niega lo que no encuentra en {@code acceso}—, que es el defecto que RF-122
 *       existe para impedir.
 *   <li><b>Un acceso que sobra</b> es una fila que la implantacion siembra y un permiso que se
 *       otorga sobre algo que no existe: ruido en la pantalla de permisos y una promesa falsa.
 * </ul>
 *
 * <p><b>Y aqui hay una tercera cosa que en los otros cuatro no existe</b>: esta base siembra ademas
 * los catalogos de los otros cuatro sistemas (AC-4), asi que la lista de arriba no es «todo lo que
 * hay en {@code acceso}» sino «lo de ESTE sistema». Lo que ata las cinco copias de {@code
 * docs/10-negocio/catalogo-de-accesos/} con sus originales es la guarda cruzada de {@code
 * infrastructure}; lo que ata la copia de <b>aqui</b> con lo que este repositorio sirve es {@link
 * #elArchivoDiceLoMismoQueLaClase}, que es la unica de las cinco que se puede comprobar sin los
 * otros clones.
 */
@DisplayName("C-7 — el catalogo de este sistema es el de sus endpoints")
class CatalogoDelSistemaTest {

    /**
     * {@code @RequiereAcceso(acceso = "...")}, con la arroba de verdad.
     *
     * <p>Anclado a la arroba a proposito: el javadoc de la propia anotacion trae un ejemplo escrito
     * con la entidad HTML, y contarlo pondria esta prueba roja por una frase.
     */
    private static final Pattern DECLARADO =
            Pattern.compile("@RequiereAcceso\\s*\\(\\s*acceso\\s*=\\s*\"([a-z0-9_]+)\"");

    /** {@code "codigo": "usuarios"} dentro del catalogo de accesos de este sistema. */
    private static final Pattern CODIGO_DEL_ARCHIVO =
            Pattern.compile("\"codigo\"\\s*:\\s*\"([a-z0-9_]+)\"");

    @Test
    @DisplayName("los mismos codigos, en los dos sentidos")
    void losMismosCodigos() throws IOException {
        Set<String> deLosEndpoints = new TreeSet<>();
        try (Stream<Path> fuentes = Files.walk(raizDelRepositorio())) {
            for (Path fuente :
                    fuentes.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> p.toString().contains("/src/main/"))
                            .filter(p -> !p.toString().contains("/build/"))
                            .toList()) {
                Matcher hallazgos =
                        DECLARADO.matcher(Files.readString(fuente, StandardCharsets.UTF_8));
                while (hallazgos.find()) {
                    deLosEndpoints.add(hallazgos.group(1));
                }
            }
        }

        assertThat(deLosEndpoints)
                .as(
                        "ningun endpoint declara acceso. O el patron dejo de reconocer la anotacion"
                                + " o este repositorio se quedo sin capa web: en los dos casos la"
                                + " comparacion de abajo pasaria sin comprobar nada")
                .isNotEmpty();

        List<String> delCatalogo =
                CatalogoDelSistema.opciones().stream()
                        .map(CatalogoDelSistema.Opcion::codigo)
                        .sorted()
                        .toList();

        assertThat(delCatalogo)
                .as(
                        "el catalogo que siembra la implantacion y los accesos que los endpoints"
                                + " exigen tienen que ser el mismo conjunto. Un acceso sin fila es una"
                                + " pantalla a la que nadie puede dar permiso (RF-122); una fila sin"
                                + " acceso es un permiso sobre algo que no existe")
                .containsExactlyElementsOf(deLosEndpoints);
    }

    @Test
    @DisplayName("y ninguna opcion se declara dos veces")
    void ningunaDosVeces() {
        List<String> codigos =
                CatalogoDelSistema.opciones().stream()
                        .map(CatalogoDelSistema.Opcion::codigo)
                        .toList();
        assertThat(codigos).doesNotHaveDuplicates();
    }

    /**
     * El archivo del catalogo unido dice lo mismo que esta clase (AC-4).
     *
     * <p>Es la mitad del AC-4 que se puede comprobar <b>sin los otros clones</b>, y hace falta
     * escribirla: los cinco archivos de {@code docs/10-negocio/catalogo-de-accesos/} son copias, y
     * la guarda que los compara con sus originales vive en {@code infrastructure}. La de {@code
     * identidad} es la unica cuyo original esta aqui, asi que dejarla tambien a la guarda cruzada
     * seria pedirle a otro repositorio que comprobara algo que este puede comprobar solo.
     *
     * <p>Y sin esto la cadena tiene un eslabon suelto: la prueba de arriba ata los endpoints con
     * {@link CatalogoDelSistema}, la implantacion siembra desde el <b>archivo</b>, y nada ataria
     * las dos puntas — una opcion nueva declarada en la clase y olvidada en el archivo dejaria una
     * pantalla sin fila de acceso, que es exactamente RF-122.
     */
    @Test
    @DisplayName("y el archivo del catalogo unido dice lo mismo que esta clase (AC-4)")
    void elArchivoDiceLoMismoQueLaClase() throws IOException {
        Path archivo =
                raizDelRepositorio().resolve("docs/10-negocio/catalogo-de-accesos/identidad.json");
        assertThat(archivo)
                .as(
                        "sin este archivo la implantacion no siembra ninguna opcion de este sistema,"
                                + " y el build lo para antes (exigirCatalogoDeAccesos)")
                .exists();

        List<String> delArchivo = new java.util.ArrayList<>();
        Matcher hallazgos =
                CODIGO_DEL_ARCHIVO.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
        while (hallazgos.find()) {
            delArchivo.add(hallazgos.group(1));
        }

        assertThat(delArchivo)
                .as(
                        "el archivo que se siembra y la clase que se contrasta contra los endpoints"
                                + " tienen que decir lo mismo, en el mismo orden")
                .containsExactlyElementsOf(
                        CatalogoDelSistema.opciones().stream()
                                .map(CatalogoDelSistema.Opcion::codigo)
                                .toList());
    }

    /**
     * La raiz del clon, subiendo hasta encontrar {@code .git}.
     *
     * <p><b>{@code Files.exists} y no {@code Files.isDirectory}</b>: en un {@code git worktree} el
     * {@code .git} de la raiz es un <b>archivo</b> con una linea {@code gitdir:} dentro, asi que
     * con {@code isDirectory} el recorrido sube hasta {@code /} y esta prueba muere sin poder
     * hablar de lo que vigila. No es un rojo util —es «no se pudo comprobar», que es peor que un
     * rojo porque deja el build inejecutable—.
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
                            + ". Sin ella esta prueba no puede leer ningun endpoint, y «no se pudo"
                            + " comprobar» no es «esta bien»");
        }
        return candidato;
    }
}
