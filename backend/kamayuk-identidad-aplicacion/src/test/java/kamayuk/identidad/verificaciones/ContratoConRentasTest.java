package kamayuk.identidad.verificaciones;

import kamayuk.comun.verificaciones.contrato.ContratoConElConsumidorTestBase;
import org.junit.jupiter.api.DisplayName;

/**
 * {@code identidad} sigue publicando lo que {@code rentas} le pide del buzon (ADR-0030 §4,
 * ADR-0039).
 *
 * <h2>Que vigila</h2>
 *
 * <p>Que las dos operaciones de {@code EventosController} —{@code GET /eventos/pendientes} y {@code
 * POST /eventos/acuses}— siguen publicando todos los campos que el ingestor de {@code rentas} lee y
 * aceptando el cuerpo que manda, tal como ese ingestor lo declara en {@code
 * rentas/docs/50-api/contratos-que-consume/identidad.json}. El rojo tiene que llegar <b>aqui</b> y
 * no alli: quien retira un campo de la respuesta es este repositorio, y con la prueba del lado del
 * consumidor el aviso le llegaria a quien no rompio nada.
 *
 * <h2>Corre desde la etapa 4, y antes no podia</h2>
 *
 * <p>Nacio en la etapa 3 con {@code @Disabled}, porque el archivo que lee lo publica el ingestor de
 * {@code rentas} y ese ingestor es de la etapa 4 — y no se podia dejar activa esperando: {@code
 * ContratoDelConsumidor.leer} <b>lanza</b> nombrando el archivo antes de que la base consulte
 * {@code desajustesVivos()}, asi que ese gancho no cubria «el consumidor todavia no publica». Lo
 * que la reactivo fue {@link ContratosDeLosConsumidoresTest}, que afirmaba que el archivo seguia
 * sin existir y se puso roja el dia que aparecio. Desde entonces esa misma guarda afirma lo
 * contrario: que el archivo esta y que esta clase <b>no</b> lleva la anotacion, porque una prueba
 * desactivada se salta en verde.
 *
 * <p>Lo que hoy compara, campo a campo, es lo que {@code
 * BuzonServidoDePuntaAPuntaTest.laFormaDelEventoServido} fija del lado de este repositorio: los
 * siete campos del evento, {@code quedan}, y los tres del acuse.
 */
@DisplayName("Contrato con rentas (identidad es el proveedor)")
class ContratoConRentasTest extends ContratoConElConsumidorTestBase {

    @Override
    protected String consumidor() {
        return "rentas";
    }

    @Override
    protected String proveedor() {
        return "identidad";
    }
}
