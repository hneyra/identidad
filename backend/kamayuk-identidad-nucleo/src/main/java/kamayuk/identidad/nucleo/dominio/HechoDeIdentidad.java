package kamayuk.identidad.nucleo.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kamayuk.identidad.autorizacion.Privilegio;
import org.jspecify.annotations.Nullable;

/**
 * Un hecho de autorizacion, listo para el buzon: su tipo, su sujeto, su cuerpo y su huella.
 *
 * <h2>El {@code eventoId} es ALEATORIO, y aqui eso es lo correcto</h2>
 *
 * <p>{@code catastro} lo <b>deriva</b> del contenido (C-8), y hace bien: lo que publica son
 * <b>proyecciones</b> —«asi esta este predio hoy»— que se republican enteras cada dia, y con la
 * identidad derivada del contenido republicar lo que no cambio no produce ni un evento nuevo. Aqui
 * lo que se publica son <b>hechos</b>: alguien dio de alta a alguien, alguien fijo una matriz. Dos
 * altas del mismo usuario no ocurren —la cuenta es unica—, y dos fijaciones seguidas de la misma
 * matriz con el mismo contenido <b>son dos actos</b>, cada uno con su observacion y su fila de
 * auditoria. Derivar la identidad del contenido colapsaria el segundo en el primero: el buzon no
 * escribiria nada y la reconstruccion seguiria cuadrando, pero el consumidor no sabria que alguien
 * volvio a tocarlo. No se emite nada que no haya pasado, y todo lo que paso se emite.
 *
 * <h2>La huella no es la identidad, y sirve para otra cosa</h2>
 *
 * <p>Es el sha256 del cuerpo <b>canonico</b> y viaja con el evento para que el consumidor pueda
 * decir «esto que aplique es exactamente esto», sin volver a calcularla sobre lo que ya escribio
 * —que seria comprobar que lo que se tiene es igual a lo que se tiene (la leccion de {@code
 * catastro_evento.huella})—.
 *
 * <p><b>Se calcula sobre la forma canonica y no sobre el {@code cuerpo} tal como sale de la
 * base</b>, y conviene decir por que: la columna es {@code jsonb}, que <b>reordena las claves y
 * descarta los espacios</b>, asi que el texto que se lee no es byte a byte el que se escribio. Una
 * huella calculada sobre lo leido no coincidiria con la guardada y el consumidor concluiria que el
 * evento llego corrupto.
 *
 * <p>La forma canonica es la lista de campos <b>en el orden en que este archivo los declara</b>,
 * separados por {@code U+001F} —el mismo separador y por el mismo motivo que {@code HuellaDelLote}
 * de {@code catastro}: es el unico caracter que ningun nombre, ninguna cuenta y ningun correo puede
 * contener, asi que dos hechos distintos no pueden producir la misma concatenacion—.
 *
 * <p>Es dominio: sin Spring, sin base de datos y sin reloj (regla 7). El {@code creado_en} lo pone
 * quien escribe, con su {@link java.time.Clock}.
 *
 * @param eventoId la identidad del hecho, aleatoria
 * @param tipo cual de los siete
 * @param sujetoId el usuario o el grupo del que habla el hecho; el {@code CHECK} cruzado de {@code
 *     V2} exige que este para los siete
 * @param cuerpo la fila entera tal como quedo, en JSON canonico
 * @param huella sha256 hexadecimal del cuerpo canonico
 */
public record HechoDeIdentidad(
        UUID eventoId, TipoDeEventoDeIdentidad tipo, long sujetoId, String cuerpo, String huella) {

    /** El separador de campos de la forma canonica: {@code U+001F}, «unit separator» de ASCII. */
    // `(char) 0x1F` y no el caracter literal: un caracter de control dentro de un literal es
    // INVISIBLE en un diff y cualquier formateador puede comerselo sin que nadie lo vea.
    private static final char SEPARADOR = (char) 0x1F;

    public HechoDeIdentidad {
        Objects.requireNonNull(eventoId, "Un hecho tiene su identidad");
        Objects.requireNonNull(tipo, "Un hecho tiene su tipo");
        Objects.requireNonNull(cuerpo, "Un hecho lleva la fila entera, no un delta");
        Objects.requireNonNull(huella, "Un hecho lleva la huella de su cuerpo");
        if (sujetoId <= 0) {
            throw new IllegalArgumentException(
                    "El hecho "
                            + tipo
                            + " no dice de quien habla. El CHECK cruzado de V2 lo exige para los"
                            + " siete tipos, asi que sin sujeto este INSERT falla en el motor y el"
                            + " diagnostico no dice cual es el hecho");
        }
    }

    /** El alta de una cuenta, o su cambio de estado: la fila entera de {@code usuario}. */
    public static HechoDeIdentidad deUsuario(TipoDeEventoDeIdentidad tipo, Usuario usuario) {
        long id = exigirId(usuario.id(), "usuario");
        Campos campos =
                new Campos()
                        .numero("usuarioId", id)
                        .texto("cuenta", usuario.cuenta())
                        .texto("nombre", usuario.nombre())
                        .texto("correo", usuario.correo())
                        .booleano("habilitado", usuario.habilitado())
                        .texto("vigenciaDesde", textoDe(usuario.vigencia().desde()))
                        .texto("vigenciaHasta", textoDe(usuario.vigencia().hasta()));
        return campos.hecho(tipo, id);
    }

    /** El alta de un grupo, o su cambio de estado: la fila entera de {@code grupo}. */
    public static HechoDeIdentidad deGrupo(TipoDeEventoDeIdentidad tipo, Grupo grupo) {
        long id = exigirId(grupo.id(), "grupo");
        Campos campos =
                new Campos()
                        .numero("grupoId", id)
                        .texto("nombre", grupo.nombre())
                        .texto("descripcion", grupo.descripcion())
                        .booleano("habilitado", grupo.habilitado())
                        .texto("vigenciaDesde", textoDe(grupo.vigencia().desde()))
                        .texto("vigenciaHasta", textoDe(grupo.vigencia().hasta()));
        return campos.hecho(tipo, id);
    }

    /**
     * La pertenencia, tal como quedo.
     *
     * <p>El sujeto es el <b>grupo</b> y no el usuario, y es la misma eleccion que hace la auditoria
     * de esta escritura: una afiliacion se administra desde la pantalla del grupo. El otro
     * identificador esta en el cuerpo, asi que no se pierde nada.
     *
     * <h2>Por que ademas de los dos identificadores viajan el nombre y la cuenta</h2>
     *
     * <p>Porque un identificador de esta base <b>no significa nada en la del consumidor</b>. Cada
     * uno de los cuatro tiene su propia base (ADR-0032) y sus propias secuencias: el grupo que aqui
     * es el 3 alli puede ser el 8. Sin la clave natural, aplicar este evento obliga al consumidor a
     * mantener una tabla de correspondencia entre los identificadores de aqui y los suyos, poblada
     * de los eventos de alta — y a no haber perdido ninguno nunca, que es exactamente lo que
     * publicar la fila entera en vez del delta existe para no exigir.
     *
     * <p>Se midio antes de decidirlo: la primera version del cuerpo llevaba solo los dos
     * identificadores y {@code ReconstruccionDesdeElBuzonTest} no podia aplicar {@code
     * MIEMBRO_AFILIADO} sobre una base vacia sin inventarse ese mapa. Los identificadores se
     * conservan igual —son lo que este sistema usa para hablar de la fila, y su auditoria los
     * nombra—, pero <b>lo que el consumidor empareja es la clave natural</b>.
     *
     * @param quien la cuenta que hace el acto; es lo que {@code miembro} guarda en {@code
     *     usuario_alta} o en {@code usuario_baja}, y sin ello la copia del consumidor no puede
     *     decir quien metio a nadie
     */
    public static HechoDeIdentidad deMiembro(
            Miembro miembro, String grupoNombre, String usuarioCuenta, String quien) {
        boolean activo = miembro.activo();
        Campos campos =
                new Campos()
                        .numero("grupoId", miembro.grupoId())
                        .texto("grupoNombre", grupoNombre)
                        .numero("usuarioId", miembro.usuarioId())
                        .texto("usuarioCuenta", usuarioCuenta)
                        .booleano("activo", activo)
                        .texto("usuarioAlta", activo ? quien : null)
                        .texto("usuarioBaja", activo ? null : quien);
        return campos.hecho(
                activo
                        ? TipoDeEventoDeIdentidad.MIEMBRO_AFILIADO
                        : TipoDeEventoDeIdentidad.MIEMBRO_DESAFILIADO,
                miembro.grupoId());
    }

    /**
     * La matriz de un sujeto sobre una opcion, entera.
     *
     * <p><b>Los siete privilegios van siempre, otorgados o no</b>, por lo mismo que las siete
     * columnas se escriben siempre en {@code PermisoRepositoryJdbc}: un cuerpo que solo nombrara
     * los otorgados dejaria al consumidor sin saber si los que faltan se retiraron o si el emisor
     * no los mando, y las dos cosas tienen consecuencias contrarias.
     *
     * <p>La opcion viaja por su <b>par</b> {@code (sistema, codigo)} y no por el {@code acceso_id}:
     * el identificador interno de esta base no significa nada en la del consumidor —cada uno sembro
     * su catalogo por su cuenta— y aplicarlo alli seria escribir un permiso sobre otra opcion. Por
     * lo mismo el sujeto lleva su <b>nombre</b> ademas de su identificador: ver el epigrafe de
     * {@link #deMiembro}.
     *
     * @param sujetoNombre el nombre del grupo, o la cuenta del usuario; es la clave natural con la
     *     que el consumidor lo empareja en su propia base
     * @param quien la cuenta que fija la matriz; es lo que {@code permiso.usuario_registro} guarda
     */
    public static HechoDeIdentidad dePermiso(
            Permiso permiso,
            String sujetoNombre,
            String sistema,
            String codigoDeAcceso,
            String quien) {
        boolean deGrupo = permiso.grupoId() != null;
        long sujetoId = deGrupo ? permiso.grupoId() : Objects.requireNonNull(permiso.usuarioId());
        Campos campos =
                new Campos()
                        .texto("sujeto", deGrupo ? "GRUPO" : "USUARIO")
                        .numero("sujetoId", sujetoId)
                        .texto("sujetoNombre", sujetoNombre)
                        .texto("sistema", sistema)
                        .texto("codigo", codigoDeAcceso);
        campos.abrirPrivilegios();
        for (Privilegio privilegio : Privilegio.values()) {
            campos.booleano(privilegio.columna(), permiso.tiene(privilegio));
        }
        campos.cerrarPrivilegios();
        campos.texto("usuarioRegistro", quien);
        return campos.hecho(TipoDeEventoDeIdentidad.PERMISO_FIJADO, sujetoId);
    }

    private static @Nullable String textoDe(@Nullable Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static long exigirId(@Nullable Long id, String que) {
        if (id == null) {
            throw new IllegalStateException(
                    "No se puede publicar un hecho de un "
                            + que
                            + " sin identificador: el evento se emite DESPUES de escribir la fila,"
                            + " asi que llegar aqui con el nulo significa que se emitio antes");
        }
        return id;
    }

    /**
     * Compone el cuerpo canonico y su huella.
     *
     * <p>Escribe el JSON a mano y no con Jackson porque esto es dominio y la regla 7 lo prohibe
     * ahi. Lo que <b>no</b> se hace a mano es el escapado: {@link #escapar} cubre las comillas, la
     * barra invertida y los caracteres de control, que es justo lo que las cuatro copias de {@code
     * escapar(} del producto <b>no</b> cubren (lo dice el censo de {@code
     * lo-que-los-cinco-comparten}). Aqui importa el doble: la columna es {@code jsonb}, asi que un
     * nombre con una comilla dentro no produce un dato raro — produce un {@code INSERT} que falla.
     */
    private static final class Campos {

        private final StringBuilder json = new StringBuilder("{");
        private final List<String> canonico = new ArrayList<>();
        private boolean primero = true;

        Campos texto(String nombre, @Nullable String valor) {
            coma();
            json.append('"').append(nombre).append("\":");
            if (valor == null) {
                json.append("null");
            } else {
                json.append('"').append(escapar(valor)).append('"');
            }
            canonico.add(nombre + "=" + (valor == null ? "" : valor));
            return this;
        }

        Campos numero(String nombre, long valor) {
            coma();
            json.append('"').append(nombre).append("\":").append(valor);
            canonico.add(nombre + "=" + valor);
            return this;
        }

        Campos booleano(String nombre, boolean valor) {
            coma();
            json.append('"').append(nombre).append("\":").append(valor);
            canonico.add(nombre + "=" + valor);
            return this;
        }

        void abrirPrivilegios() {
            coma();
            json.append("\"privilegios\":{");
            primero = true;
        }

        void cerrarPrivilegios() {
            json.append('}');
            primero = false;
        }

        HechoDeIdentidad hecho(TipoDeEventoDeIdentidad tipo, long sujetoId) {
            String cuerpo = json.append('}').toString();
            StringBuilder nombre = new StringBuilder(tipo.name());
            for (String campo : canonico) {
                nombre.append(SEPARADOR).append(campo);
            }
            return new HechoDeIdentidad(
                    UUID.randomUUID(), tipo, sujetoId, cuerpo, sha256(nombre.toString()));
        }

        private void coma() {
            if (!primero) {
                json.append(',');
            }
            primero = false;
        }
    }

    private static String escapar(String texto) {
        StringBuilder salida = new StringBuilder(texto.length() + 8);
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            switch (caracter) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        salida.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        salida.append(caracter);
                    }
                }
            }
        }
        return salida.toString();
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }
}
