package kamayuk.identidad.nucleo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-4, la mitad que se puede comprobar sin los otros clones: los cinco catalogos cargan y traen
 * las opciones que se midieron.
 *
 * <h2>Que sostiene esta prueba y que no</h2>
 *
 * <p><b>No</b> comprueba que la copia de {@code catastro} diga lo mismo que el {@code
 * CatalogoDelSistema} de {@code catastro}: eso hace falta tener el clon delante, y es la guarda
 * cruzada de {@code infrastructure} (PR hermano), que los compara en las dos direcciones y sale
 * roja nombrando la opcion que sobra o falta.
 *
 * <p>Lo que <b>si</b> sostiene, y hace falta igual: que los cinco archivos estan en el jar, que
 * cargan, y <b>cuantas opciones trae cada uno</b>. Las cifras son la referencia contra la que la
 * guarda hermana se escribe, y sobre todo son lo que impide que un archivo se quede a medias sin
 * que nada lo diga — un catalogo con la mitad de las opciones no revienta: siembra la mitad, y las
 * pantallas que faltan son pantallas a las que nadie puede dar permiso (RF-122).
 */
@DisplayName("AC-4 — los cinco catalogos de accesos, y cuantas opciones trae cada uno")
class CatalogoUnidoTest {

    /**
     * Lo medido el 2026-09-09, leyendo los cinco originales.
     *
     * <p>{@code rentas} son las 134 de {@code docs/10-negocio/catalogo-de-opciones.md} —<b>las 134,
     * sin quitar las once de Seguridad</b>: mientras {@code rentas} conserve sus escrituras hasta
     * la etapa 4 (AC-7), su catalogo real las tiene, y la guarda cruzada compara contra el catalogo
     * REAL de cada clon. Quitarlas aqui pondria roja esa guarda por once opciones que ese sistema
     * si declara—. Las otras cuatro son lo que declara el {@code CatalogoDelSistema} de cada uno.
     */
    private static final Map<String, Integer> CUANTAS =
            Map.of("rentas", 134, "catastro", 16, "normativa", 1, "caja", 3, "identidad", 6);

    @Test
    @DisplayName("los cinco cargan del jar, con las opciones medidas")
    void losCincoCargan() {
        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();

        assertThat(catalogo.sistemas())
                .as(
                        "los cinco, y en el orden del reparto. Uno que falte deja a ese sistema sin"
                                + " ninguna opcion configurable en esta base")
                .containsExactlyElementsOf(SistemasDelProducto.TODOS);

        for (Map.Entry<String, Integer> esperado : CUANTAS.entrySet()) {
            assertThat(catalogo.cuantasDe(esperado.getKey()))
                    .as(
                            "el catalogo de «%s» trajo otra cantidad de opciones. O el archivo"
                                    + " cambio —y hay que remedirlo aqui y en la guarda cruzada de"
                                    + " `infrastructure`— o se leyo a medias",
                            esperado.getKey())
                    .isEqualTo(esperado.getValue().longValue());
        }

        assertThat(catalogo.opciones())
                .as("las 160 de los cinco, que son las que la implantacion siembra")
                .hasSize(CUANTAS.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    @DisplayName("y ningun par (sistema, codigo) se repite")
    void ningunParSeRepite() {
        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();
        assertThat(catalogo.opciones().stream().map(o -> o.sistema() + ":" + o.codigo()).toList())
                .as(
                        "un par repetido lo descarta el `ON CONFLICT ... DO NOTHING` del sembrador"
                                + " EN SILENCIO: la segunda opcion no se crea y esa pantalla se queda"
                                + " sin fila de acceso")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("el CODIGO solo no identifica una opcion, y por eso el par viaja junto")
    void elCodigoSoloNoIdentifica() {
        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();

        // El contraste que justifica la columna `sistema` del baseline. Sin el, «se llavea por el
        // par» seria una decision de esquema que ninguna prueba ejerce, y con un solo catalogo
        // sembrado —el estado de la etapa 1— habria sido cierto que el codigo bastaba.
        assertThat(
                        catalogo.opciones().stream()
                                .filter(o -> "permisos".equals(o.codigo()))
                                .map(CatalogoUnido.Opcion::sistema)
                                .sorted()
                                .toList())
                .as(
                        "«permisos» es una opcion de este sistema Y otra de `rentas`. Resolver un"
                                + " acceso por el codigo solo devolveria dos filas, y elegir una seria"
                                + " autorizar contra el catalogo de otro sistema")
                .containsExactly("identidad", "rentas");
    }

    @Test
    @DisplayName("un catalogo vacio no se siembra: se dice")
    void unCatalogoVacioSeDice() {
        assertThatThrownBy(() -> CatalogoUnido.de(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ninguna opcion configurable");
    }

    @Test
    @DisplayName("y un sistema que no es uno de los cinco tampoco")
    void unSistemaDesconocidoSeDice() {
        assertThatThrownBy(
                        () ->
                                new CatalogoUnido.Opcion(
                                        "tesoreria", "MOD", "Modulo", "opcion", "Opcion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es uno de los cinco");
    }
}
