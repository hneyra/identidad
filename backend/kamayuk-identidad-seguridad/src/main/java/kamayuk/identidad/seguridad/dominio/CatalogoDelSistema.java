package kamayuk.identidad.seguridad.dominio;

import java.util.List;

/**
 * Las opciones del menu que sirve <b>este</b> sistema (NEG-03, RF-122).
 *
 * <h2>Por que aqui hay una lista y en {@code rentas} se lee un documento</h2>
 *
 * <p>{@code rentas} lee {@code docs/10-negocio/catalogo-de-opciones.md} —las 134 opciones del
 * manual— porque ese documento vive en su repositorio. Aqui no vive: leerlo obligaria a que el
 * build de {@code identidad} dependiera del clon de {@code rentas} <b>en produccion</b>, y no solo
 * en las pruebas. Lo que se hace en su lugar es lo que el inventario del corte ya decia: «cada
 * sistema siembra <b>su parte</b>».
 *
 * <p>El codigo, el nombre y el orden de las seis estan transcritos de la seccion {@code ##
 * Seguridad} de {@code rentas/docs/10-negocio/catalogo-de-opciones.md}, que sigue siendo la fuente
 * del manual (cap. 4), y son <b>exactamente</b> las que la etapa 2 se lleva de alli: son las
 * pantallas con que se administra quien puede hacer que.
 *
 * <p><b>Las otras cinco de esa seccion NO estan, y no es un recorte a ojo</b>: {@code cambiar_anio}
 * y {@code cambiar_clave} son actos de la SESION de cada sistema, {@code auditoria} es la consulta
 * de la bitacora que cada base guarda por separado, {@code parametros} es de {@code normativa} —ya
 * lo declara su catalogo— y {@code respaldo} lo ejecuta el proceso de despliegue, no una pantalla.
 * Traerlas aqui seria decir que este sistema las sirve, y no las sirve.
 *
 * <h2>Lo que hoy NO se puede comprobar, y por que se dice en vez de fingirlo</h2>
 *
 * <p>En los otros cuatro sistemas esta lista se contrasta contra los {@code @RequiereAcceso} que
 * sus propios endpoints declaran, en los dos sentidos. Aqui <b>no hay ni un endpoint</b>: la etapa
 * 1 no trae capa web (ver {@code kamayuk.identidad.nucleo}), asi que el contraste no tiene con que
 * hacerse y {@code CatalogoDelSistemaTest} lo dice y lo <b>sujeta</b> — afirma que hoy son cero, de
 * modo que el primer controlador que llegue pone la prueba en rojo pidiendo que se restaure la
 * comparacion. Es una exencion que caduca sola, no una que se queda dentro para siempre.
 *
 * <h2>En la etapa 2 esta lista pasa a ser la UNION de los cinco sistemas</h2>
 *
 * <p>Lo que este sistema guarda no es su propio menu: es a quien se le concede cada opcion de
 * <b>todos</b> los catalogos (ADR-0039). Por eso su esquema le anade a {@code modulo_sistema} y a
 * {@code acceso} la columna {@code sistema} y llavea por ella —dos sistemas pueden nombrar igual
 * dos opciones distintas—, y por eso la etapa 2 tiene que traer aqui tambien las opciones de {@code
 * rentas}, {@code catastro}, {@code normativa} y {@code caja}. Mientras eso no ocurra, lo que se
 * siembra es solo lo de aqui, y {@code SembradorDeLaCopiaLocal} lo escribe con {@code sistema =
 * 'identidad'}.
 *
 * <p>Es una clase de dominio: sin Spring, sin base de datos y sin reloj (regla 7).
 */
public final class CatalogoDelSistema {

    private CatalogoDelSistema() {}

    /** Una opcion del menu, con el modulo al que pertenece. */
    public record Opcion(String moduloCodigo, String moduloNombre, String codigo, String nombre) {}

    /** El modulo del manual al que pertenecen las seis, con su nombre. */
    public static final String MODULO_CODIGO = "SEGURIDAD";

    /** Como se llama ese modulo en el arbol del menu. */
    public static final String MODULO_NOMBRE = "Seguridad";

    private static final List<Opcion> OPCIONES =
            List.of(
                    opcion("modulos", "Modulos del sistema"),
                    opcion("usuarios", "Usuarios del sistema"),
                    opcion("grupos", "Grupos de usuarios"),
                    opcion("accesos", "Accesos y politicas"),
                    opcion("miembros", "Gestion de miembros"),
                    opcion("permisos", "Permisos y niveles de accesibilidad"));

    private static Opcion opcion(String codigo, String nombre) {
        return new Opcion(MODULO_CODIGO, MODULO_NOMBRE, codigo, nombre);
    }

    /** Las opciones de este sistema, en el orden del catalogo del manual. */
    public static List<Opcion> opciones() {
        return OPCIONES;
    }
}
