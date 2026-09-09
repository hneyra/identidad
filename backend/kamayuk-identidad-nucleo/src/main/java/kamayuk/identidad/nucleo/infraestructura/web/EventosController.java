package kamayuk.identidad.nucleo.infraestructura.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.autorizacion.RequiereAcceso;
import kamayuk.identidad.nucleo.aplicacion.EntregaDeEventos;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.web.Api;
import kamayuk.identidad.web.CodigoDeError;
import kamayuk.identidad.web.ProblemaDeNegocio;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El buzon de salida de {@code identidad}, servido para que los <b>cuatro</b> sistemas lo consuman
 * (etapa 3 de {@code infrastructure}#52, ADR-0028 §3, ADR-0039).
 *
 * <h2>Quien pregunta sale del TOKEN, y nunca de un parametro</h2>
 *
 * <p>Es la unica decision de diseno de este controlador y esta escrita entera en {@link
 * Consumidor}: acusar <b>retira</b> un evento de la cola de un sistema y lo retirado no se vuelve a
 * servir, asi que un {@code ?consumidor=caja} dejaria que cualquiera con acceso al buzon vaciara la
 * cola de {@code caja} — y el sintoma llegaria semanas despues, en {@code caja}, como un permiso
 * que aqui esta y alli no. El {@code azp} del token de acceso lo firma el emisor: decir «soy {@code
 * caja}» cuesta la clave de su cliente confidencial.
 *
 * <h2>Que pasa si el token no lo trae</h2>
 *
 * <p><b>403 con {@link CodigoDeError#SIN_IDENTIDAD_DE_SERVICIO}</b>, y no un consumidor por
 * omision. Un valor por omision aqui —«si no dice quien es, {@code rentas}»— seria un acuse en
 * nombre de otro producido por un descuido de configuracion, o sea el defecto de arriba sin que
 * nadie lo haya escrito. El codigo es propio y no {@code SIN_PRIVILEGIO} porque el remedio es otro:
 * no falta un permiso, falta pedir el token con el cliente que toca.
 *
 * <h2>Y por que hay acuse, en vez de que el consumidor lleve un cursor</h2>
 *
 * <p>Porque un cursor <b>pierde eventos en silencio</b>: {@code identidad_evento.id} se asigna al
 * {@code INSERT} y no al {@code COMMIT}, asi que una transaccion que tomo el 100 y confirma despues
 * de otra que tomo el 101 queda por detras de un cursor que ya paso por 101. La fila esta, el
 * consumidor no la vera nunca, y nada lo dice. Lo midio {@code V5} de {@code catastro} y esta
 * escrito en el javadoc de {@code identidad_evento.id}.
 *
 * <p>El acuse llega <b>despues</b> de que el consumidor haya confirmado su transaccion, asi que un
 * acuse perdido reentrega: la entrega es <b>al menos una vez</b> y quien deduplica es el receptor,
 * por {@code evento_id}.
 *
 * <h2>Quien puede llamar, y quien se lo concede</h2>
 *
 * <p>Las dos operaciones exigen la opcion {@code eventos} del catalogo de este sistema. Es una
 * opcion <b>propia</b> y no una de las seis de administracion, y ahi este controlador se aparta de
 * lo que hizo {@code catastro} —que reuso {@code consulta_fichas} para no crear una opcion de menu
 * que nadie abre—: alli el buzon lleva el padron, o sea lo mismo que esa opcion ya deja leer; aqui
 * el buzon lleva <b>quien puede hacer que</b>, y darlo con {@code usuarios} o con {@code permisos}
 * significaria que todo administrador de la municipalidad puede ademas vaciarle la cola a {@code
 * caja}. Son dos cosas distintas y por eso son dos opciones distintas.
 *
 * <p><b>El codigo va escrito como literal en las dos anotaciones</b>, y no como una constante
 * compartida aunque compile igual: {@code CatalogoDelSistemaTest} ata el catalogo con los endpoints
 * <b>leyendo el fuente</b> —{@code @RequiereAcceso(acceso = "…")}, con la cadena dentro—, asi que
 * una constante lo esconderia de esa comparacion. Medido: con {@code acceso = ACCESO} el escaner
 * encuentra seis codigos donde el catalogo declara siete, y la prueba acusa al CATALOGO de tener
 * uno de mas —«some elements were not expected: ["eventos"]»—, que manda a mirar lo contrario de lo
 * que pasa.
 *
 * <p>Quien se la concede es la <b>implantacion</b>, que siembra el grupo «Consumidores del buzon»
 * con {@code eventos} y nada mas, y que desde la <b>etapa 4</b> le afilia ademas las <b>cuatro
 * cuentas de servicio</b> —{@code service-account-kamayuk-<sistema>-servicio-<ubigeo>}—. Hasta
 * entonces ese grupo nacia vacio «porque a quien se afilia lo decide quien despliegue»; medido con
 * las cinco aplicaciones levantadas, no lo decidia nadie: el emisor crea el cliente confidencial de
 * cada satelite, o sea que el consumidor <b>consigue su token</b> y llega hasta aqui, y aqui su
 * cuenta no tenia fila en {@code usuario} — 403 {@code SIN_PRIVILEGIO} «la cuenta no esta dada de
 * alta en este sistema» en los cuatro, y la copia local de cada uno congelada sin un solo error que
 * lo dijera.
 *
 * <p>Ese 403 sigue siendo lo correcto para una cuenta que de verdad no esta dada de alta —la de
 * otra municipalidad, por ejemplo—, y el resto de su frase, «la administracion de usuarios, grupos
 * y permisos vive en rentas», es de {@code GuardiaDeAcceso} de la plataforma compartida y ya no es
 * verdad en ninguno de los cinco: vive aqui. Se corrige en la pieza compartida, no en una copia.
 */
@RestController
@RequestMapping(Api.RAIZ + "/eventos")
public class EventosController {

    /** Cuantos se sirven por peticion como maximo. Una vuelta tiene que acabar. */
    private static final int TOPE = 500;

    /** Cuantos se sirven cuando no se pide un limite. */
    private static final int POR_OMISION = 200;

    private final EntregaDeEventos entrega;

    public EventosController(EntregaDeEventos entrega) {
        this.entrega = entrega;
    }

    /** Lo que a quien pregunta le falta por aplicar, en el orden en que se emitio. */
    @GetMapping("/pendientes")
    @RequiereAcceso(acceso = "eventos", privilegio = Privilegio.LECTURA)
    public LoteDeEventosResource pendientes(
            @RequestParam(defaultValue = "" + POR_OMISION) int limite) {
        if (limite < 1 || limite > TOPE) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "El limite va de 1 a " + TOPE + ", y llego " + limite);
        }
        BuzonDeIdentidad.Lote lote = entrega.pendientesPara(quienPregunta(), limite);
        List<EventoResource> eventos = new ArrayList<>();
        for (EventoDeIdentidad evento : lote.eventos()) {
            eventos.add(EventoResource.de(evento));
        }
        return new LoteDeEventosResource(List.copyOf(eventos), lote.quedan());
    }

    /**
     * Acusa los eventos que quien pregunta ya aplico y confirmo.
     *
     * <p>Devuelve <b>cuantos se escribieron</b>, que puede ser menos de los que llegaron: acusar
     * dos veces el mismo evento es lo que pasa cada vez que un acuse se pierde despues de que el
     * receptor confirmara, y no es un error. Devolver 204 habria dicho lo mismo con menos
     * informacion — y esa diferencia entre lo recibido y lo escrito es la unica senal de que la
     * entrega se esta repitiendo.
     */
    @PostMapping("/acuses")
    @RequiereAcceso(acceso = "eventos", privilegio = Privilegio.REGISTRO)
    public AcuseResource acusar(@RequestBody PeticionDeAcuse peticion) {
        Consumidor consumidor = quienPregunta();
        List<String> ids = peticion.eventos();
        if (ids == null || ids.isEmpty()) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Un acuse sin ningun evento no dice nada: o se acusa algo, o no se llama");
        }
        if (ids.size() > TOPE) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.VALIDACION,
                    "Un acuse de mas de " + TOPE + " eventos: llegaron " + ids.size());
        }
        List<UUID> eventos = new ArrayList<>();
        for (String id : ids) {
            if (id == null) {
                throw new ProblemaDeNegocio(
                        CodigoDeError.VALIDACION,
                        "La lista de eventos trae un nulo: un acuse nombra lo que aplico");
            }
            try {
                eventos.add(UUID.fromString(id.strip()));
            } catch (IllegalArgumentException noEsUuid) {
                throw new ProblemaDeNegocio(
                        CodigoDeError.VALIDACION, "«" + id + "» no es un identificador de evento");
            }
        }
        int escritos;
        try {
            escritos = entrega.acusar(consumidor, List.copyOf(eventos));
        } catch (BuzonDeIdentidad.EventoQueNoConsta noSeSirvio) {
            // 422 y no 404: lo que esta mal no es la ruta sino el cuerpo, y el cliente puede
            // corregirlo mandando lo que se le sirvio. Un 404 mandaria a mirar la URL.
            throw new ProblemaDeNegocio(CodigoDeError.VALIDACION, noSeSirvio.motivo());
        }
        return new AcuseResource(
                ids.size(), escritos, entrega.pendientesPara(consumidor, 1).quedan());
    }

    /**
     * Quien pregunta, sacado del {@code azp} del token ya validado.
     *
     * <p>Se lee de {@link SecurityContextHolder} —o sea de lo que Spring Security ya comprobo
     * criptograficamente— y no de la peticion, que es exactamente la misma regla con que {@code
     * TenantContextFilter} saca la municipalidad del claim {@code municipalidad_id} (ADR-0005). Las
     * dos salen del mismo token firmado: una dice <b>quien</b> lee y la otra <b>de que
     * municipalidad</b>.
     *
     * <p>No hay filtro que lo fije en un contexto de hilo, y es deliberado: el consumidor solo
     * tiene sentido en estas dos operaciones, y un {@code ConsumidorContext} que se poblara en toda
     * peticion seria un dato disponible para cualquier controlador que decidiera fiarse de el.
     */
    private static Consumidor quienPregunta() {
        try {
            return Consumidor.deAzp(azpDelToken());
        } catch (Consumidor.NoEsUnaCuentaDeServicio noEsUnSistema) {
            throw new ProblemaDeNegocio(
                    CodigoDeError.SIN_IDENTIDAD_DE_SERVICIO, noEsUnSistema.motivo());
        }
    }

    private static @Nullable String azpDelToken() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || !(autenticacion.getPrincipal() instanceof Jwt token)) {
            return null;
        }
        return token.getClaimAsString("azp");
    }

    /** Lo que el consumidor manda para acusar. */
    public record PeticionDeAcuse(@Nullable List<String> eventos) {}

    /**
     * Un tramo de la cola de quien pregunta.
     *
     * @param quedan cuantos le faltan en total, contando los de esta pagina. Es su retraso
     */
    public record LoteDeEventosResource(List<EventoResource> eventos, long quedan) {}

    /**
     * Un evento tal como viaja.
     *
     * <p>{@code cuerpo} viaja como <b>cadena</b> y no como objeto anidado, igual que en {@code
     * catastro}: lo que el consumidor tiene que poder hacer con el es leerlo con su propio lector y
     * —si quiere— guardarlo tal cual; embebido como objeto, el JSON de salida lo reordenaria y la
     * {@code huella}, que se calcula sobre la forma canonica, dejaria de poder comprobarse contra
     * nada.
     *
     * <p>{@code creadoEn} tambien como cadena, en ISO-8601: es lo que {@code Instant.toString}
     * produce y lo que {@code Instant.parse} lee del otro lado, sin depender de como este
     * configurado el serializador de ninguno de los dos.
     */
    public record EventoResource(
            String eventoId,
            long secuencia,
            String tipo,
            long sujetoId,
            String cuerpo,
            String huella,
            String creadoEn) {

        static EventoResource de(EventoDeIdentidad evento) {
            return new EventoResource(
                    evento.eventoId().toString(),
                    evento.secuencia(),
                    evento.tipo().name(),
                    evento.sujetoId(),
                    evento.cuerpo(),
                    evento.huella(),
                    evento.creadoEn().toString());
        }
    }

    /** El resultado de un acuse. */
    public record AcuseResource(int recibidos, int escritos, long quedan) {}
}
