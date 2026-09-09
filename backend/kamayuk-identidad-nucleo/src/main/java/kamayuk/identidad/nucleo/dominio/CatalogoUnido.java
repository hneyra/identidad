package kamayuk.identidad.nucleo.dominio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * El catalogo de opciones de los <b>cinco</b> sistemas, que es lo que esta base siembra (AC-4).
 *
 * <h2>Por que aqui hay cinco catalogos y en los otros cuatro hay uno</h2>
 *
 * <p>En {@code rentas}, {@code catastro}, {@code normativa} y {@code caja} cada base guarda
 * <b>su</b> catalogo: su {@code CatalogoDelSistema} declara sus opciones y su implantacion las
 * siembra. Aqui no, y no es una comodidad: este sistema es el dueño de la autorizacion (ADR-0039),
 * o sea que lo que guarda es <b>a quien se le concede cada opcion de todos</b>. Un permiso sobre
 * una pantalla de {@code catastro} tiene que poder otorgarse desde aqui, y para eso la fila de
 * {@code acceso} de esa pantalla tiene que existir en esta base.
 *
 * <p>De ahi la unica desviacion del baseline: {@code modulo_sistema} y {@code acceso} llavean por
 * {@code (municipalidad_id, sistema, codigo)} y no por el codigo solo. {@code permisos} es una
 * opcion de {@code identidad} y otra de {@code rentas}; {@code SEGURIDAD} es un modulo de tres de
 * los cinco. Sin la columna, sembrar el segundo chocaria con el UNIQUE del primero y el {@code ON
 * CONFLICT ... DO NOTHING} del sembrador lo dejaria pasar <b>en silencio</b>.
 *
 * <h2>Lo que este tipo NO decide, y quien lo decide</h2>
 *
 * <p>El contenido de cada catalogo es de su dueño, y aqui hay una <b>copia</b>. Lo que impide que
 * las copias se separen del original no vive en este repositorio: es la guarda cruzada de {@code
 * infrastructure} (AC-4 de la etapa 2), que compara cada archivo con el catalogo real de su clon
 * <b>en las dos direcciones</b> y sale roja nombrando la opcion que sobra o falta. Lo que si se
 * comprueba aqui es lo que se puede comprobar sin los otros clones: que los cinco archivos estan,
 * que traen las cifras medidas y que ningun par {@code (sistema, codigo)} se repite.
 *
 * <p>Es dominio: sin Spring, sin base de datos y sin reloj (regla 7). <b>Quien lee los archivos del
 * jar es {@code CatalogoUnidoDelJar}</b>, en {@code infraestructura}, y no este tipo: la regla 7
 * prohibe Jackson en {@code ..dominio..} —lo nombra con todas las letras— y hacer aqui un analisis
 * de JSON a mano para esquivarla seria peor que la dependencia que evita.
 */
public final class CatalogoUnido {

    private final List<Opcion> opciones;

    private CatalogoUnido(List<Opcion> opciones) {
        this.opciones = List.copyOf(opciones);
    }

    /**
     * Una opcion del menu de un sistema, con el modulo al que pertenece.
     *
     * <p>Es la misma forma que el {@code CatalogoDelSistema.Opcion} de los otros cuatro <b>mas el
     * sistema</b>, que es justamente lo que alli no hace falta.
     */
    public record Opcion(
            String sistema,
            String moduloCodigo,
            String moduloNombre,
            String codigo,
            String nombre) {

        public Opcion {
            Objects.requireNonNull(sistema, "Una opcion dice de que catalogo es");
            Objects.requireNonNull(moduloCodigo, "Una opcion pertenece a un modulo");
            Objects.requireNonNull(moduloNombre, "El modulo de una opcion tiene nombre");
            Objects.requireNonNull(codigo, "Una opcion tiene su codigo");
            Objects.requireNonNull(nombre, "Una opcion tiene su nombre");
            if (!SistemasDelProducto.esUnSistema(sistema)) {
                throw new IllegalArgumentException(
                        "El catalogo declara el sistema '"
                                + sistema
                                + "', que no es uno de los cinco "
                                + SistemasDelProducto.TODOS
                                + ". Sembrarlo dejaria una fila que el CHECK del esquema rechaza, y"
                                + " si algun dia no lo rechazara seria un permiso que nadie va a"
                                + " consultar nunca");
            }
        }
    }

    /**
     * Compone el catalogo unido, comprobando lo unico que se puede comprobar sin los otros clones.
     *
     * @throws IllegalArgumentException si viene vacio o si un par {@code (sistema, codigo)} se
     *     repite
     */
    public static CatalogoUnido de(List<Opcion> opciones) {
        Objects.requireNonNull(opciones, "El catalogo unido es una lista, aunque sea vacia");
        if (opciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "El catalogo unido vino vacio. Sembrar cero accesos dejaria la municipalidad sin"
                            + " ninguna opcion configurable —nadie podria dar permiso a ninguna"
                            + " pantalla de ninguno de los cinco— y en silencio");
        }
        Set<String> vistos = new LinkedHashSet<>();
        List<String> repetidos = new ArrayList<>();
        for (Opcion opcion : opciones) {
            if (!vistos.add(opcion.sistema() + ":" + opcion.codigo())) {
                repetidos.add(opcion.sistema() + ":" + opcion.codigo());
            }
        }
        if (!repetidos.isEmpty()) {
            // Con el par repetido, el segundo INSERT lo descarta el `ON CONFLICT ... DO NOTHING`
            // del sembrador: la opcion no se crea, nadie puede darle permiso, y no hay error.
            throw new IllegalArgumentException(
                    "El catalogo unido repite el par (sistema, codigo): "
                            + repetidos
                            + ". El sembrador descartaria la segunda en silencio y esa pantalla se"
                            + " quedaria sin fila de acceso (RF-122)");
        }
        return new CatalogoUnido(opciones);
    }

    /** Todas las opciones de los cinco, en el orden en que se leyeron. */
    public List<Opcion> opciones() {
        return opciones;
    }

    /** Cuantas opciones declara ese sistema. Para el censo, y para que se pueda contar. */
    public long cuantasDe(String sistema) {
        return opciones.stream().filter(o -> o.sistema().equals(sistema)).count();
    }

    /** Los sistemas que traen al menos una opcion, en el orden en que aparecen. */
    public Set<String> sistemas() {
        Set<String> sistemas = new LinkedHashSet<>();
        opciones.forEach(o -> sistemas.add(o.sistema()));
        return sistemas;
    }
}
