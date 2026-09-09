package kamayuk.identidad.nucleo.dominio;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Cual de los cuatro sistemas esta leyendo el buzon (etapa 3, ADR-0028 §2 y §3).
 *
 * <h2>Sale del {@code azp} del token, y NUNCA de un parametro</h2>
 *
 * <p>Esta es la decision entera de este tipo, y no es una preferencia. Lo que un acuse hace es
 * <b>retirar un evento de la cola de un consumidor</b>, y lo retirado no se vuelve a servir: un
 * cliente que pudiera decir «soy {@code caja}» dejaria a {@code caja} sin eventos que ya nunca
 * recibira, y el sintoma llegaria semanas despues como un permiso que aqui esta y alli no. Con el
 * consumidor sacado del token, decir quien se es cuesta tener la clave del cliente confidencial de
 * ese sistema, que es lo mismo que cuesta autenticarse.
 *
 * <p>El {@code azp} —«authorized party»— de un token emitido por {@code client_credentials} es el
 * identificador del cliente que lo pidio, y ADR-0028 §2 fija su forma: <b>uno por par (sistema,
 * municipalidad)</b>, {@code kamayuk-<sistema>-servicio-<ubigeo>}. La compone {@code
 * clienteDeServicio()} de {@code infra/verificaciones/identidad-de-servicio.ts}, y es la misma que
 * los cuatro sistemas ya usan para llamarse entre si desde {@code infrastructure}#21.
 *
 * <h2>El ubigeo del {@code azp} NO se compara con el del contexto, y se dice</h2>
 *
 * <p>El inquilino de la peticion lo fija {@code TenantContextFilter} desde el claim {@code
 * municipalidad_id} del <b>mismo</b> token firmado (ADR-0005), asi que comparar el ubigeo del
 * {@code azp} contra el de la fila {@code municipalidad} seria comprobar un token consigo mismo por
 * un camino mas largo — y anadiria una segunda fuente de la verdad del inquilino, que es
 * exactamente lo que ADR-0005 prohibe. De aqui sale <b>quien</b> lee; de alli, <b>de que
 * municipalidad</b>.
 *
 * <h2>{@code identidad} no esta, y es una afirmacion</h2>
 *
 * <p>Este sistema no se consume a si mismo: lo que el buzon publica es lo que esta base acaba de
 * escribir. Un {@code azp} de {@code kamayuk-identidad-servicio-…} se rechaza igual que uno de
 * usuario. Ver la cabecera de {@code V3}.
 *
 * <p>Es dominio: sin Spring, sin base de datos y sin reloj (regla 7).
 */
public enum Consumidor {
    RENTAS("rentas"),
    CATASTRO("catastro"),
    NORMATIVA("normativa"),
    CAJA("caja");

    /**
     * {@code kamayuk-<sistema>-servicio-<ubigeo>}, con el ubigeo de seis digitos del INEI.
     *
     * <p>El sistema se captura sin enumerar los cuatro dentro del patron a proposito: asi un {@code
     * azp} de {@code identidad} —o de un sexto sistema que no existe— casa con la forma y falla
     * <b>diciendo cual llego</b>, en vez de salir como «esto no parece un cliente de servicio», que
     * manda a mirar el emisor cuando lo que pasa es otra cosa.
     */
    private static final Pattern CLIENTE_DE_SERVICIO =
            Pattern.compile("^kamayuk-([a-z]+)-servicio-([0-9]{6})$");

    /**
     * Como nombra el emisor a la cuenta de servicio de un cliente confidencial.
     *
     * <p>Es la convencion de Keycloak y no una eleccion de este producto: un cliente con cuenta de
     * servicio activada tiene un usuario {@code service-account-<clientId>}, y ese es el {@code
     * preferred_username} que viaja en el token de {@code client_credentials}. Es la cuenta que el
     * guardia compara con la columna {@code usuario.cuenta}, asi que es la que la implantacion
     * tiene que dar de alta.
     */
    private static final String CUENTA_DE_SERVICIO = "service-account-";

    private final String sistema;

    Consumidor(String sistema) {
        this.sistema = sistema;
    }

    /** Como se llama en la columna {@code consumidor} y en la de {@code sistema}. */
    public String sistema() {
        return sistema;
    }

    /** Los cuatro, en el orden del reparto de ADR-0029. */
    public static List<String> sistemas() {
        return List.of(RENTAS.sistema, CATASTRO.sistema, NORMATIVA.sistema, CAJA.sistema);
    }

    /**
     * El cliente confidencial con el que este sistema pide su token en esa municipalidad.
     *
     * <p>Es la otra direccion de {@link #deAzp(String)}: aquella lee el {@code azp} que llega y
     * esta lo compone. Vive en la misma clase a proposito —y no en la implantacion, que es quien la
     * usa— porque una forma que se compone en un sitio y se analiza en otro se separa: la que se
     * quedaria vieja seria justo la que da de alta la cuenta, y su sintoma es una fila de {@code
     * usuario} que ningun token nombra nunca, o sea un 403 en los cuatro consumidores semanas
     * despues del despliegue. Que las dos direcciones cuadren lo mide {@code ConsumidorTest},
     * componiendo y volviendo a analizar.
     *
     * @param ubigeo los seis digitos del INEI de la municipalidad que se implanta
     * @throws IllegalArgumentException si con ese ubigeo no sale un cliente de la forma que {@link
     *     #deAzp(String)} admite — que es peor que un error, porque la cuenta se daria de alta y no
     *     serviria para nada
     */
    public String clienteDeServicio(String ubigeo) {
        String cliente = "kamayuk-" + sistema + "-servicio-" + ubigeo;
        if (!CLIENTE_DE_SERVICIO.matcher(cliente).matches()) {
            throw new IllegalArgumentException(
                    "Con el ubigeo «"
                            + ubigeo
                            + "» sale «"
                            + cliente
                            + "», que no tiene la forma «kamayuk-<sistema>-servicio-<ubigeo>» de"
                            + " ADR-0028 §2: el ubigeo son seis digitos. Dar de alta esa cuenta"
                            + " dejaria una fila de `usuario` que ningun token nombra.");
        }
        return cliente;
    }

    /**
     * La cuenta con la que ese cliente llega al guardia: {@code service-account-<cliente>}.
     *
     * <p>Es lo que la implantacion escribe en {@code usuario.cuenta}, y lo que el guardia compara
     * con el {@code preferred_username} del token. Medido el 2026-09-09 contra las cinco
     * aplicaciones levantadas: sin esta fila, {@code GET /eventos/pendientes} con el token de
     * servicio de {@code normativa} contesta <b>403</b> «La cuenta
     * «service-account-kamayuk-normativa-servicio-200105» no esta dada de alta en este sistema».
     */
    public String cuentaDeServicio(String ubigeo) {
        return CUENTA_DE_SERVICIO + clienteDeServicio(ubigeo);
    }

    /**
     * Quien es quien pidio este token, o el rechazo que dice por que no se puede saber.
     *
     * @param azp el claim {@code azp} del token de acceso ya validado, o {@code null} si no viene
     * @throws NoEsUnaCuentaDeServicio si el {@code azp} falta, no tiene la forma de un cliente de
     *     servicio, o nombra un sistema que no consume este buzon
     */
    public static Consumidor deAzp(@Nullable String azp) {
        if (azp == null || azp.isBlank()) {
            throw new NoEsUnaCuentaDeServicio(
                    "El token no trae `azp`, asi que no dice de quien es. El buzon se lee con la"
                            + " cuenta de servicio del sistema que lo consume"
                            + " («kamayuk-<sistema>-servicio-<ubigeo>», ADR-0028 §2), y no con un"
                            + " token de usuario: quien acusa retira el evento de una cola, y lo"
                            + " retirado no se vuelve a servir.");
        }
        Matcher forma = CLIENTE_DE_SERVICIO.matcher(azp.strip());
        if (!forma.matches()) {
            throw new NoEsUnaCuentaDeServicio(
                    "«"
                            + azp
                            + "» no es el cliente de una cuenta de servicio. Se esperaba"
                            + " «kamayuk-<sistema>-servicio-<ubigeo>» (ADR-0028 §2), con"
                            + " <sistema> uno de: "
                            + String.join(", ", sistemas())
                            + ". Un token de usuario —el del backoffice— no puede acusar: retira"
                            + " el evento de la cola de un sistema y lo retirado no se vuelve a"
                            + " servir.");
        }
        String nombre = forma.group(1).toLowerCase(Locale.ROOT);
        for (Consumidor candidato : values()) {
            if (candidato.sistema.equals(nombre)) {
                return candidato;
            }
        }
        throw new NoEsUnaCuentaDeServicio(
                "«"
                        + azp
                        + "» nombra el sistema «"
                        + nombre
                        + "», que no consume este buzon. Los que lo consumen son: "
                        + String.join(", ", sistemas())
                        + (SistemasDelProducto.IDENTIDAD.equals(nombre)
                                ? ". `identidad` NO se consume a si mismo: lo que este buzon"
                                        + " publica es lo que esta misma base acaba de escribir, y"
                                        + " un acuse suyo retiraria un evento que nadie ha"
                                        + " aplicado."
                                : "."));
    }

    /**
     * El token no identifica a ninguno de los cuatro.
     *
     * <p>Es un tipo propio y no una {@code IllegalArgumentException} porque el borde tiene que
     * contestarlo con un codigo distinto de los demas: no es que a esta cuenta le falte un
     * privilegio —eso se arregla concediendoselo—, es que <b>esta cuenta no es un sistema</b>, y
     * eso se arregla pidiendo el token con el cliente confidencial que toca. Dos remedios
     * distintos, dos respuestas distintas.
     */
    public static final class NoEsUnaCuentaDeServicio extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String motivo;

        public NoEsUnaCuentaDeServicio(String mensaje) {
            super(mensaje);
            this.motivo = mensaje;
        }

        /**
         * Lo mismo que {@code getMessage()}, y sin nulo posible.
         *
         * <p>Existe porque {@code Throwable.getMessage()} declara devolver {@code @Nullable} y el
         * borde compone con el la respuesta: con el, NullAway obliga a un {@code requireNonNull} en
         * cada sitio que la atrape. Aqui el mensaje es obligatorio por construccion.
         */
        public String motivo() {
            return motivo;
        }
    }
}
