package kamayuk.identidad.nucleo.dominio;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Algo sobre lo que se otorgan privilegios: una opcion de menu o una politica.
 *
 * <p>El {@code codigo} de una opcion de menu es el <b>id de la pantalla en el catalogo</b>
 * (NEG-03), y es lo que un controlador declara en {@code @RequiereAcceso}. Que sea el mismo
 * identificador en los tres sitios —catalogo, tabla y anotacion— es lo que permite que sembrar los
 * accesos sea copiar el catalogo, y lo que hace cierta la promesa del manual (RF-122).
 *
 * <h2>Aqui el codigo NO identifica una opcion: hace falta el sistema (etapa 2)</h2>
 *
 * <p>En los otros cuatro sistemas cada base guarda <b>su</b> catalogo, asi que el codigo basta.
 * Esta base guarda a quien se le concede cada opcion de los <b>cinco</b> (ADR-0039), y dos sistemas
 * pueden nombrar igual dos opciones distintas: {@code permisos} es una opcion de {@code identidad}
 * y otra de {@code rentas}, y {@code SEGURIDAD} es un modulo de tres. Por eso el esquema llavea por
 * {@code (municipalidad_id, sistema, codigo)} desde {@code V1} y por eso el par viaja junto en todo
 * este contexto: quien resuelva un acceso por el codigo solo acabaria autorizando contra el permiso
 * de otro sistema, que es peor que fallar.
 *
 * @param id nulo mientras no se ha guardado
 * @param sistema de que catalogo es la opcion; uno de los cinco de {@code acceso_sistema_check}
 * @param activo un acceso retirado se desactiva; los permisos que cuelgan de el son constancia
 */
public record Acceso(
        @Nullable Long id,
        long moduloId,
        String sistema,
        TipoDeAcceso tipo,
        String codigo,
        String nombre,
        boolean activo) {

    private static final int CODIGO_MAXIMO = 60;
    private static final int NOMBRE_MAXIMO = 160;

    public Acceso {
        Objects.requireNonNull(sistema, "El acceso necesita decir de que sistema es su opcion");
        Objects.requireNonNull(tipo, "El acceso necesita su tipo");
        Objects.requireNonNull(codigo, "El acceso necesita su codigo");
        Objects.requireNonNull(nombre, "El acceso necesita su nombre");
        codigo = codigo.strip();
        nombre = nombre.strip();
        if (codigo.isEmpty() || codigo.length() > CODIGO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El codigo de acceso va de 1 a " + CODIGO_MAXIMO + ": '" + codigo + "'");
        }
        if (nombre.isEmpty() || nombre.length() > NOMBRE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El nombre de acceso va de 1 a " + NOMBRE_MAXIMO + " caracteres");
        }
    }
}
