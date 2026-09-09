package kamayuk.identidad.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * El discriminador que separa «falta una cifra normativa» de «falta un campo de la peticion»
 * (#604).
 *
 * <h2>Que problema resuelve</h2>
 *
 * <p>#547 dejo el area de convenios alcanzable traduciendo a {@code 422 VALIDACION} las seis
 * excepciones de lo que no esta publicado. Pero el cuerpo del problema solo lleva {@code codigo} y
 * {@code mensaje}, y con <b>el mismo par</b> salen dos cosas que piden acciones distintas: «Falta
 * el campo 'nroDeCuotas'» lo arregla quien atiende, en la misma pantalla; «El ejercicio 2026 no
 * tiene un conjunto de parametros sellado» <b>no lo arregla nadie desde la pantalla</b> —hay que
 * sellar el conjunto o publicar la cifra (D-02a, D-02b)—.
 *
 * <p>Sin un miembro que las separe, la interfaz solo puede distinguirlas leyendo el texto, y el
 * texto se reescribe en cuanto alguien lo lee en voz alta. Este miembro es la version legible por
 * programa de esa diferencia: <b>si esta, no se arregla desde la pantalla</b>.
 *
 * <h2>Por que un objeto y no una cadena</h2>
 *
 * <p>Porque hay <b>dos</b> situaciones distintas y una sola cadena tendria que mentir en una:
 * cuando falta una fila concreta se puede nombrar su llave, y cuando falta el conjunto sellado
 * entero <b>no hay ninguna llave</b> —no hay donde publicarla—. Con dos miembros planos («{@code
 * llave}» y «{@code ejercicioSinSellar}») el cliente tendria que comprobar los dos, y olvidarse de
 * uno es exactamente la clase de arreglo a medias que este repositorio ya midio varias veces. Con
 * un objeto hay <b>un</b> discriminador —esta o no esta— y dentro, lo que se sepa.
 *
 * <h2>Que va dentro</h2>
 *
 * <ul>
 *   <li>{@code ejercicio}: siempre. Todas hablan del conjunto sellado <b>de un ejercicio</b>.
 *   <li>{@code llave}: {@code TIPO:CLAVE} cuando falta exactamente una fila; el {@code TIPO} solo
 *       cuando falta el bloque entero; y <b>ausente</b> cuando lo que falta es el conjunto. Nunca
 *       una clave inventada para rellenar el hueco (ver {@code ParametroSinPublicar} de {@code
 *       kamayuk-normativa-parametros}, en OTRO repositorio).
 * </ul>
 *
 * <p>Ni tabla, ni columna, ni restriccion, ni SQL (RNF-033): el ejercicio y la llave son datos del
 * corpus normativo —lo que hay que publicar y para que ano—, no del esquema.
 *
 * <h2>Por que la llave es un {@code String} y no el tipo del contexto de parametros</h2>
 *
 * <p>{@code kamayuk.identidad.web} vive en {@code kamayuk-identidad-plataforma}, que no depende —ni
 * debe— del contexto acotado que publica el conjunto sellado. Quien traduce la excepcion es el
 * controlador de su modulo, y lo que cruza la frontera es texto.
 *
 * <p><b>En `identidad` esto no lo usa nadie</b>, y se dice en vez de dejarlo parecer vivo: este
 * sistema no consume ningun conjunto sellado y no tiene capa web todavia. Viaja porque {@code
 * kamayuk.identidad.web} es la <b>quinta copia</b> de la capa web comun que ADR-0038 manda a {@code
 * kamayuk-lib} —hoy vacio—, y recortarla aqui la volveria la unica de las cinco distinta, que es
 * justo lo que esa decision existe para acabar.
 */
public record ParametroQueFalta(int ejercicio, @Nullable String llave) {

    /** Falta una fila concreta del conjunto sellado, o el bloque entero de un tipo. */
    public static ParametroQueFalta llave(int ejercicio, String llave) {
        return new ParametroQueFalta(ejercicio, llave);
    }

    /** Falta el conjunto sellado del ejercicio: no hay ninguna fila que nombrar. */
    public static ParametroQueFalta conjuntoDelEjercicio(int ejercicio) {
        return new ParametroQueFalta(ejercicio, null);
    }

    /**
     * El miembro tal y como sale en el cuerpo {@code problem+json}.
     *
     * <p>Se compone a mano y no se deja a la serializacion del record para que {@code llave}
     * <b>desaparezca</b> cuando no la hay, en vez de salir como {@code null}: un {@code null} en el
     * cuerpo es un valor, y el cliente que pregunte por el lo vera presente.
     */
    Map<String, Object> comoMiembro() {
        Map<String, Object> miembro = new LinkedHashMap<>();
        miembro.put("ejercicio", ejercicio);
        String nombrada = llave;
        if (nombrada != null) {
            miembro.put("llave", nombrada);
        }
        return miembro;
    }
}
