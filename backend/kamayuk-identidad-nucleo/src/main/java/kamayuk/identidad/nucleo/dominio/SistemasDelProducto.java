package kamayuk.identidad.nucleo.dominio;

import java.util.List;
import java.util.Set;

/**
 * Los cinco sistemas de Kamayuk, que es el vocabulario cerrado de la columna {@code sistema}.
 *
 * <h2>Por que existe, y por que es una lista escrita</h2>
 *
 * <p>Esta base guarda a quien se le concede cada opcion de los <b>cinco</b> catalogos (ADR-0039),
 * asi que {@code modulo_sistema} y {@code acceso} llevan una columna {@code sistema} y su {@code
 * CHECK} nombra los cinco uno a uno ({@code acceso_sistema_check}, {@code V1}). Esta clase es el
 * mismo vocabulario del lado de Java, y existe para que un valor que el {@code CHECK} rechazaria no
 * llegue nunca hasta el motor: lo que sale de ahi es un {@code 23514} sin nombre de campo, y lo que
 * sale de aqui es un 422 que dice cual es el problema y enumera los cinco.
 *
 * <p><b>Es una lista escrita y no se deriva de nada</b>, y conviene decir por que: lo unico de lo
 * que podria derivarse es del propio {@code CHECK} —o sea, de la base—, y entonces la comprobacion
 * de la capa web dependeria de una conexion. Que las dos listas no se separen lo sujeta {@code
 * SistemasDelProductoTest}, que lee el {@code CHECK} del baseline y lo compara con esta.
 *
 * <p>El orden es el del reparto de ADR-0029 mas este sistema al final, y no significa nada: lo
 * unico que se le pide a esta lista es ser un conjunto cerrado.
 *
 * <p>Es dominio: sin Spring, sin base de datos y sin reloj (regla 7).
 */
public final class SistemasDelProducto {

    /** Este sistema, que es el unico cuyo catalogo sirven sus propios endpoints. */
    public static final String IDENTIDAD = "identidad";

    /** Los cinco, en el orden del reparto. */
    public static final List<String> TODOS =
            List.of("rentas", "catastro", "normativa", "caja", IDENTIDAD);

    private static final Set<String> CONJUNTO = Set.copyOf(TODOS);

    private SistemasDelProducto() {}

    /** Si esa palabra es uno de los cinco. Distingue mayusculas: la columna guarda minusculas. */
    public static boolean esUnSistema(String sistema) {
        return CONJUNTO.contains(sistema);
    }
}
