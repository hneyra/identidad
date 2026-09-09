package kamayuk.identidad.nucleo.aplicacion;

import java.util.List;
import java.util.UUID;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sirve el buzon de salida y recoge su acuse (etapa 3, ADR-0028 §3).
 *
 * <h2>Aqui se SIRVE y no se EMPUJA, y la decision no es de gusto</h2>
 *
 * <p>{@code caja} empuja sus pagos a un endpoint de {@code rentas}; {@code catastro} sirve su buzon
 * para que {@code rentas} venga a buscarlo. Aqui hay <b>cuatro</b> consumidores, y esa es la
 * diferencia que lo decide: empujar obligaria a este sistema a conocer las cuatro direcciones, a
 * pedir cuatro credenciales de servicio y a reintentar cuatro veces — o sea a que el dueno de la
 * autorizacion dependa de que los cuatro esten arriba. Sirviendo, un consumidor caido es un
 * consumidor con retraso: su cola crece y nadie mas se entera.
 *
 * <p>Y hay un motivo mas fuerte, el mismo que {@code catastro} escribio: este sistema declara que
 * <b>no llama a nadie</b> —su egreso es DNS, su motor y Keycloak—, y empujar seria exactamente
 * dejar de cumplirlo.
 *
 * <h2>Existe para que el controlador no sostenga un repositorio</h2>
 *
 * <p>Y no es una formalidad: ningun {@code *RepositoryJdbc} de este sistema anota
 * {@code @Transactional} —no tiene por que, la transaccion es del caso de uso—, asi que un
 * controlador que llamara al repositorio correria sin {@code SET LOCAL} y la politica RLS <b>no
 * devolveria vacio: reventaria</b> con «invalid input syntax for type bigint: ""».
 *
 * <h2>Las dos operaciones van en DOS transacciones, y no en una</h2>
 *
 * <p>Son dos peticiones distintas: entre servir y acusar hay una vuelta entera del consumidor
 * —leerlo, aplicarlo en SU base y confirmarlo— y nada de eso pasa aqui. Un acuse que llegara dentro
 * de la misma transaccion que la lectura estaria diciendo que se aplico algo que todavia viaja.
 */
@Service
public class EntregaDeEventos {

    private final BuzonDeIdentidad buzon;

    public EntregaDeEventos(BuzonDeIdentidad buzon) {
        this.buzon = buzon;
    }

    /** Lo que a ese consumidor le falta, en el orden en que se emitio. */
    @Transactional(readOnly = true)
    public BuzonDeIdentidad.Lote pendientesPara(Consumidor consumidor, int limite) {
        return buzon.pendientesPara(consumidor, limite);
    }

    /**
     * Anota lo que ese consumidor ya aplico.
     *
     * @return cuantas filas se escribieron, que puede ser menos que las acusadas
     */
    @Transactional
    public int acusar(Consumidor consumidor, List<UUID> eventoIds) {
        return buzon.acusar(consumidor, eventoIds);
    }
}
