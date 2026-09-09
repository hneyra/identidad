package kamayuk.identidad.nucleo.dominio;

import java.util.List;

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
 * El acuse por consumidor es de la <b>etapa 3</b> y va en otra tabla, asi que {@code
 * identidad_evento} es <b>inmutable</b>: se inserta y se lee, y su {@code GRANT} no tiene {@code
 * UPDATE} ni {@code DELETE}.
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
}
