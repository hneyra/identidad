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
 * El catalogo de este sistema, y <b>lo que hoy no se puede contrastar</b>.
 *
 * <h2>La diferencia con los otros cuatro, y por que esta prueba esta escrita al reves</h2>
 *
 * <p>En {@code rentas}, {@code catastro}, {@code normativa} y {@code caja} la prueba hermana
 * recorre {@code src/main}, junta los {@code @RequiereAcceso(acceso = "...")} de sus endpoints y
 * exige que sean <b>exactamente</b> los codigos del catalogo, en los dos sentidos. Aqui eso no se
 * puede: la etapa 1 de {@code infrastructure#52} no trae capa web y <b>no hay ni un endpoint</b>,
 * asi que esa comparacion se cumpliria sobre el conjunto vacio y no diria nada.
 *
 * <p>Copiarla igual habria sido peor que no tenerla: {@link CatalogoDelSistema} declara seis
 * opciones y ningun endpoint las declara, de modo que la version de los otros cuatro saldria
 * <b>roja</b> —acusando al catalogo de tener seis de mas— y el remedio evidente seria vaciarlo, que
 * es exactamente lo contrario de lo que la etapa 2 necesita.
 *
 * <p>Lo que se afirma en su lugar es el <b>estado de partida</b>, y es una exencion que caduca
 * sola: hoy los endpoints son CERO. El primer controlador que llegue pone esta prueba en rojo, y su
 * mensaje dice que lo que toca entonces es traer la comparacion de los dos sentidos.
 */
@DisplayName("Etapa 1 — el catalogo de este sistema, y lo que todavia no lo ata")
class CatalogoDelSistemaTest {

    /**
     * {@code @RequiereAcceso(acceso = "...")}, con la arroba de verdad.
     *
     * <p>Anclado a la arroba a proposito: el javadoc de la propia anotacion trae un ejemplo escrito
     * con la entidad HTML, y contarlo pondria esta prueba roja por una frase.
     */
    private static final Pattern DECLARADO =
            Pattern.compile("@RequiereAcceso\\s*\\(\\s*acceso\\s*=\\s*\"([a-z0-9_]+)\"");

    @Test
    @DisplayName("hoy ningun endpoint declara acceso, y por eso el catalogo no se contrasta")
    void hoyNingunEndpointDeclaraAcceso() throws IOException {
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
                        "llego el primer endpoint de este sistema, asi que esta prueba deja de ser"
                                + " cierta y hay que sustituirla por la de los otros cuatro: recorrer"
                                + " src/main, juntar los @RequiereAcceso y exigir que sean EXACTAMENTE"
                                + " los codigos del catalogo, en los dos sentidos. Un acceso sin fila"
                                + " es una pantalla a la que nadie puede dar permiso (RF-122); una fila"
                                + " sin acceso es un permiso sobre algo que no existe")
                .isEmpty();
    }

    @Test
    @DisplayName("y aun asi el catalogo tiene las seis, sin repetir y todas de Seguridad")
    void elCatalogoTieneLasSeis() {
        List<CatalogoDelSistema.Opcion> opciones = CatalogoDelSistema.opciones();

        // El sujeto. Sin esto, las dos afirmaciones de abajo se cumplen sobre la lista vacia — y
        // una lista vacia es justo lo que deja `SembradorDeLaCopiaLocal` lanzando «el catalogo de
        // este sistema vino vacio» en la implantacion, o sea sin ninguna opcion configurable.
        assertThat(opciones).as("las seis opciones que la etapa 2 trae de `rentas`").hasSize(6);

        assertThat(opciones.stream().map(CatalogoDelSistema.Opcion::codigo).toList())
                .as(
                        "un codigo repetido siembra dos filas de `acceso` con el mismo nombre y"
                                + " ninguna forma de saber cual otorga que")
                .doesNotHaveDuplicates();

        assertThat(opciones)
                .as(
                        "las seis son del modulo `%s` del manual (cap. 4). Una opcion de otro"
                                + " modulo haria que la implantacion sembrara un modulo del menu que"
                                + " este sistema no sirve",
                        CatalogoDelSistema.MODULO_CODIGO)
                .allSatisfy(
                        opcion -> {
                            assertThat(opcion.moduloCodigo())
                                    .isEqualTo(CatalogoDelSistema.MODULO_CODIGO);
                            assertThat(opcion.moduloNombre())
                                    .isEqualTo(CatalogoDelSistema.MODULO_NOMBRE);
                        });
    }

    /**
     * La raiz del clon, subiendo hasta encontrar {@code .git}.
     *
     * <p><b>{@code Files.exists} y no {@code Files.isDirectory}</b>: en un {@code git worktree} el
     * {@code .git} de la raiz es un <b>archivo</b> con una linea {@code gitdir:} dentro, asi que
     * con {@code isDirectory} el recorrido sube hasta {@code /} y esta prueba muere sin poder
     * hablar de lo que vigila. No es un rojo util —es «no se pudo comprobar», que es peor que un
     * rojo porque deja el build inejecutable—, y es el mismo defecto que {@code catastro} cerro en
     * sus dos ayudantes al medir la linea base de su #5.
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
