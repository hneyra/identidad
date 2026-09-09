package kamayuk.identidad.verificaciones;

import kamayuk.comun.verificaciones.contrato.ContratoConElConsumidorTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

/**
 * {@code identidad} sigue publicando lo que {@code rentas} le pide del buzon (ADR-0030 §4,
 * ADR-0039).
 *
 * <h2>Que vigila, cuando llegue a correr</h2>
 *
 * <p>Que las dos operaciones de {@code EventosController} —{@code GET /eventos/pendientes} y {@code
 * POST /eventos/acuses}— siguen publicando todos los campos que el ingestor de {@code rentas} lee y
 * aceptando el cuerpo que manda. El rojo tiene que llegar <b>aqui</b> y no alli: quien retira un
 * campo de la respuesta es este repositorio, y con la prueba del lado del consumidor el aviso le
 * llegaria a quien no rompio nada.
 *
 * <h2>Por que esta DESACTIVADA hoy, y que la reactiva</h2>
 *
 * <p>Porque {@code rentas/docs/50-api/contratos-que-consume/identidad.json} <b>no existe
 * todavia</b>: lo publica su ingestor, que es de la <b>etapa 4</b>. Y no se puede dejar activa
 * esperando a que llegue — se midio antes de decidirlo: {@code ContratoDelConsumidor.leer}
 * <b>lanza</b> nombrando el archivo antes de que la base consulte {@code desajustesVivos()}, asi
 * que ese gancho —que es como este producto declara la deuda con nombre— no puede cubrir «el
 * consumidor todavia no publica su contrato». Las cuatro saldrian rojas hoy y el build con ellas, y
 * un rojo permanente es la forma segura de que nadie vuelva a mirar una prueba.
 *
 * <p><b>Lo que impide que este {@code @Disabled} se quede aqui para siempre</b> es {@link
 * ContratosDeLosConsumidoresTest}: exige que el archivo <b>siga sin existir</b> en los cuatro
 * clones, asi que el dia que {@code rentas} lo publique esa guarda sale roja pidiendo que se quite
 * esta anotacion. Una prueba desactivada sin nada que la reactive es una prueba borrada con mas
 * lineas.
 */
@Disabled(
        "etapa 4: rentas no publica todavia docs/50-api/contratos-que-consume/identidad.json."
                + " Lo que exige quitar este @Disabled es ContratosDeLosConsumidoresTest, que se pone"
                + " roja el dia que el archivo aparezca")
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
