package kamayuk.identidad.nucleo.dominio;

import java.util.List;
import java.util.UUID;

/**
 * El buzon de salida de {@code identidad} (AC-2, ADR-0028 §3).
 *
 * <h2>La propiedad que este puerto existe para tener</h2>
 *
 * <p><b>El evento se escribe en la MISMA transaccion que la fila.</b> No hay {@code REQUIRES_NEW},
 * ni {@code @TransactionalEventListener}, ni {@code @Scheduled}: {@link #emitir} es una llamada mas
 * dentro del metodo {@code @Transactional} del caso de uso, despues de escribir. Con eso, «si la
 * fila esta, el evento esta» y «si el evento esta, la fila esta» son las dos ciertas, y una
 * escritura que se deshace se lleva su evento con ella.
 *
 * <p>Las tres alternativas se descartan por lo mismo y conviene decirlo, porque las tres parecen
 * mas limpias: una transaccion aparte deja el evento cuando la escritura se deshizo —el consumidor
 * aplica un alta que aqui no existe—, un escucha de eventos de Spring que se ate al {@code COMMIT}
 * deja la fila sin evento cuando el proceso muere entre las dos, y un {@code @Scheduled} que barra
 * la tabla tiene que saber que ha cambiado, o sea reimplementar el buzon peor.
 *
 * <h2>Lo que este puerto NO tiene, y por que</h2>
 *
 * <p>No hay {@code marcarEntregados} ni {@code anotarIntentoFallido}, que es lo que {@code
 * catastro} si tiene. Su tabla lleva un {@code estado} porque tiene <b>un</b> consumidor; aqui hay
 * <b>cuatro</b>, y un solo estado no puede decir «entregado a {@code caja} y no a {@code rentas}».
 * Desde la <b>etapa 3</b> lo que hay en su lugar es {@link #acusar}, que escribe en otra tabla
 * —{@code identidad_evento_acuse}, {@code V3}—, asi que {@code identidad_evento} sigue siendo
 * <b>inmutable</b>: se inserta y se lee, y su {@code GRANT} no tiene {@code UPDATE} ni {@code
 * DELETE}.
 */
public interface BuzonDeIdentidad {

    /**
     * Escribe el hecho. Se llama dentro de la transaccion del caso de uso, despues de la fila.
     *
     * @return el hecho tal como quedo, con la secuencia que le dio el motor
     */
    EventoDeIdentidad emitir(HechoDeIdentidad hecho);

    /**
     * Lo que hay a partir de una secuencia, en el orden en que se emitio.
     *
     * <p>La sirve la <b>etapa 3</b>, y esta declarada desde ya porque es lo que hace comprobable a
     * la etapa 2: {@code ReconstruccionDesdeElBuzonTest} lee por aqui todo lo emitido y lo aplica
     * sobre una copia vacia. Sin lectura, «los tipos son suficientes» seria una afirmacion sobre
     * codigo que nadie ejecuto.
     *
     * <p><b>Un cursor por secuencia pierde eventos y hay que saberlo</b> (lo midio {@code V5} de
     * {@code catastro}): el {@code id} se asigna al {@code INSERT} y no al {@code COMMIT}, asi que
     * una transaccion que toma el 100 y confirma despues de otra que tomo el 101 queda por detras
     * de un cursor que ya paso por 101. Aqui vale porque quien lee lo hace cuando ya no hay nadie
     * escribiendo —una prueba— y porque el consumidor de la etapa 3 <b>no puede</b> usar un cursor
     * por eso mismo: tendra que acusar por evento.
     */
    List<EventoDeIdentidad> pendientesDesde(long secuencia, int limite);

    /**
     * Lo que a <b>este</b> consumidor le falta por aplicar, en el orden en que se emitio.
     *
     * <p>Es la lectura que sirve la etapa 3, y no es {@link #pendientesDesde} con otro nombre: alli
     * el corte es una <b>posicion</b> y aqui es la <b>ausencia de acuse</b>. La diferencia importa
     * y la midio {@code V5} de {@code catastro}: la secuencia se asigna al {@code INSERT} y no al
     * {@code COMMIT}, asi que una transaccion que tomo el 100 y confirma despues de otra que tomo
     * el 101 queda por detras de un cursor que ya paso por 101 — la fila esta, el consumidor no la
     * vera nunca, y nada lo dice. Con el acuse no hay posicion que adelantar.
     *
     * <p>El orden es el de la <b>secuencia</b> y no el del instante: el instante lo pone el reloj
     * de la aplicacion y dos eventos de la misma transaccion lo comparten, asi que ordenar por el
     * dejaria el orden de dos hechos del mismo acto a merced del planificador. Y aqui el orden
     * <b>es</b> contenido: un {@code MIEMBRO_AFILIADO} que llegue antes que el alta de su grupo no
     * se puede aplicar.
     *
     * @param limite cuantos como maximo; quien lo acota es el borde
     * @return los eventos y <b>cuantos le faltan en total</b> a ese consumidor, contando los que
     *     van en esta pagina. Es el retraso del buzon visto desde ese lado
     */
    Lote pendientesPara(Consumidor consumidor, int limite);

    /**
     * Anota que ese consumidor ya aplico y confirmo esos eventos.
     *
     * <p><b>Idempotente</b>: acusar dos veces el mismo evento es lo que pasa cada vez que un acuse
     * se pierde despues de que el receptor confirmara, y no es un error. La entrega es <b>al menos
     * una vez</b> y quien deduplica es el receptor, por {@code evento_id}.
     *
     * <p><b>Y falla nombrandolo si un evento no existe</b>, que es lo contrario: acusar lo que este
     * buzon no sirvio no puede ser un descuido tolerable, porque lo acusado no se vuelve a servir.
     * Un identificador inventado —o el de otra municipalidad, que desde aqui es lo mismo: la
     * politica RLS no lo deja ver— es un defecto del cliente y se contesta como tal.
     *
     * @return cuantas filas se escribieron, que puede ser menos que los acusados
     * @throws EventoQueNoConsta si alguno no esta en el buzon de esta municipalidad
     */
    int acusar(Consumidor consumidor, List<UUID> eventoIds);

    /** Un tramo de la cola de un consumidor, con su retraso. */
    record Lote(List<EventoDeIdentidad> eventos, long quedan) {}

    /**
     * Se acuso algo que este buzon no sirvio.
     *
     * <p>Tipo propio y no la violacion de la foranea que el motor daria de todos modos: un {@code
     * 23503} nombra la restriccion y no el evento, y quien lo recibe no puede saber cual de los
     * doscientos que mando esta mal. Lo comprueba el adaptador ANTES de insertar, precisamente para
     * poder decirlo.
     */
    final class EventoQueNoConsta extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String motivo;

        public EventoQueNoConsta(String mensaje) {
            super(mensaje);
            this.motivo = mensaje;
        }

        /** Lo mismo que {@code getMessage()}, y sin nulo posible: lo compone el borde. */
        public String motivo() {
            return motivo;
        }
    }
}
