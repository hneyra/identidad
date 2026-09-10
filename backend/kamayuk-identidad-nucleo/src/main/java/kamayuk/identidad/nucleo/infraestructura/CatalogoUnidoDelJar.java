package kamayuk.identidad.nucleo.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee del jar los cinco {@code catalogo-de-accesos/&lt;sistema&gt;.json} (AC-4).
 *
 * <h2>Por que los archivos y no cinco listas escritas en Java</h2>
 *
 * <p>Porque son <b>157 opciones</b> —130 de {@code rentas}, 16 de {@code catastro}, 7 de aqui, 3 de
 * {@code caja} y 1 de {@code normativa}; la cifra se mueve con las etapas, y lo que la mide es
 * {@code CatalogoUnidoTest}— y porque no son de este repositorio: son copias de los cinco
 * catalogos, y lo que las tiene que atar al original es una guarda que lee los dos lados. Una lista
 * escrita en Java se compara peor: la guarda cruzada de {@code infrastructure} tendria que analizar
 * codigo fuente para leerla, y las de {@code rentas} habria que transcribirlas a mano cada vez que
 * ese repositorio regenere su catalogo. En un archivo de datos, la comparacion es un {@code
 * JSON.parse} de los dos lados.
 *
 * <p>Y por eso el de {@code rentas} <b>se deriva</b> con {@code
 * docs/10-negocio/derivar-catalogo-de-rentas.mjs} del clon hermano, en vez de teclearse.
 *
 * <h2>Aqui vive el analisis, y no en el dominio</h2>
 *
 * <p>{@link CatalogoUnido} es puro y no puede usar Jackson: la regla 7 lo prohibe en {@code
 * ..dominio..} por su nombre. Este adaptador lo lee y le entrega la lista ya construida, que es el
 * mismo reparto que {@code CatalogoDeOpciones} de {@code rentas} hace con su markdown salvo que
 * alli el analisis cabe en dos expresiones regulares del JDK.
 *
 * <h2>Un recurso que falta se dice, no se salta</h2>
 *
 * <p>El {@code Copy} del build es lo que deja los cinco archivos en el jar, y un {@code Copy} sin
 * fuente <b>se salta en verde</b> (NO-SOURCE): el jar sale sin catalogo, la aplicacion arranca,
 * atiende peticiones y la implantacion no encuentra ninguna opcion que sembrar. Le paso de verdad a
 * {@code rentas} con su markdown y su imagen de Docker. Aqui hay dos guardas contra eso —la tarea
 * {@code exigirCatalogoDeAccesos} del build y este {@code IllegalStateException}— y ninguna sobra:
 * la primera cae al construir y la segunda al implantar.
 */
public final class CatalogoUnidoDelJar {

    /** Donde el build deja los cinco archivos. */
    public static final String CARPETA = "/catalogo-de-accesos/";

    private CatalogoUnidoDelJar() {}

    /** Los cinco catalogos, unidos. */
    public static CatalogoUnido leer() {
        JsonMapper json = JsonMapper.builder().build();
        List<CatalogoUnido.Opcion> opciones = new ArrayList<>();
        for (String sistema : SistemasDelProducto.TODOS) {
            opciones.addAll(opcionesDe(json, sistema));
        }
        return CatalogoUnido.de(opciones);
    }

    private static List<CatalogoUnido.Opcion> opcionesDe(JsonMapper json, String sistema) {
        String recurso = CARPETA + sistema + ".json";
        try (InputStream entrada = CatalogoUnidoDelJar.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "No se encontro "
                                + recurso
                                + ". Lo copia la tarea copiarCatalogoDeAccesos del build; sin el, la"
                                + " implantacion no tiene los accesos de '"
                                + sistema
                                + "' que sembrar y nadie puede dar permiso a ninguna de sus"
                                + " pantallas (RF-122)");
            }
            JsonNode raiz = json.readTree(entrada);
            String declarado = raiz.path("sistema").asString("");
            if (!sistema.equals(declarado)) {
                // El nombre del archivo y el campo de dentro son dos sitios que dicen lo mismo, asi
                // que se comparan: un `caja.json` que declarara `rentas` sembraria las opciones de
                // caja bajo el sistema equivocado, y desde ese momento el permiso de la ventanilla
                // se otorgaria sobre una fila que su guardia no consulta nunca.
                throw new IllegalStateException(
                        recurso
                                + " declara el sistema '"
                                + declarado
                                + "' y el archivo se llama '"
                                + sistema
                                + "'");
            }
            List<CatalogoUnido.Opcion> opciones = new ArrayList<>();
            for (JsonNode modulo : raiz.path("modulos")) {
                String moduloCodigo = modulo.path("codigo").asString("");
                String moduloNombre = modulo.path("nombre").asString("");
                for (JsonNode opcion : modulo.path("opciones")) {
                    opciones.add(
                            new CatalogoUnido.Opcion(
                                    sistema,
                                    moduloCodigo,
                                    moduloNombre,
                                    opcion.path("codigo").asString(""),
                                    opcion.path("nombre").asString("")));
                }
            }
            if (opciones.isEmpty()) {
                throw new IllegalStateException(
                        recurso
                                + " no trae ninguna opcion. Un catalogo vacio no es «ese sistema no"
                                + " tiene pantallas»: es un archivo que se leyo mal o se genero mal,"
                                + " y sembrarlo dejaria a ese sistema sin ninguna opcion"
                                + " configurable");
            }
            return opciones;
        } catch (IOException noSePudo) {
            throw new UncheckedIOException("No se pudo leer " + recurso, noSePudo);
        }
    }
}
