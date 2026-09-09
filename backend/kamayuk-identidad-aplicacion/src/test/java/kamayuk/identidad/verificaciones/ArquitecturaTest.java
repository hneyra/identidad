package kamayuk.identidad.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kamayuk.comun.verificaciones.ArquitecturaTestBase;
import kamayuk.comun.verificaciones.ReglasDeArquitectura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Las reglas de ARQ-04 §2 aplicadas al codigo de `identidad`.
 *
 * <p>El cuerpo esta en {@code comun-verificaciones}; lo que cambia lo declara {@link
 * ConfiguracionDeIdentidad}, que encuentra {@code ServiceLoader}.
 *
 * <p>Esta clase tiene que existir: sin ella la barrera no corre en este build.
 *
 * <h2>La unica de las cinco barreras que en la etapa 1 no se puede aplicar tal cual</h2>
 *
 * <p>Este repositorio es la combinacion que {@code comun-verificaciones} no contempla: <b>tiene
 * dominio y no tiene capa web</b>. {@code kamayuk.identidad.dominio} llega entero con el
 * vocabulario compartido, y controladores no hay ninguno porque la etapa 1 no trae negocio (ver
 * {@code kamayuk.identidad.nucleo}). Los otros cuatro sistemas tienen controladores desde el corte,
 * asi que este caso no habia aparecido.
 *
 * <p>Consecuencia: las cinco reglas acotadas a {@code son controladores HTTP} no encuentran ni una
 * clase, y ArchUnit las rechaza —correctamente: una regla que no puede fallar no protege nada—. El
 * unico permiso que la libreria publica para eso es {@code sinContextosAcotadosTodavia()}, y
 * <b>aqui no se puede declarar</b>: para admitirlo, {@code ArquitecturaTestBase} exige que no haya
 * <b>ninguna</b> clase en {@code ..dominio..}, y aqui hay treinta y tantas. Medido: declararlo pone
 * roja {@code lasReglasAcotadasEncuentranClasesDeVerdad} pidiendo que se retire.
 *
 * <p><b>El hueco es de {@code comun-verificaciones} y se declara sin tocarla</b> —cambiarla desde
 * este repositorio le impondria a los cuatro consumidores una carencia de uno—: lo que falta alli
 * es poder dar el permiso por AMBITO, como ya se hace con {@code fiscalizacion} e {@code
 * indicadores} en {@link ConfiguracionDeLasVerificaciones#ambitosAusentes()}. Mientras tanto, aqui
 * se desactiva <b>ese unico metodo</b> de la clase base y se sustituye por {@link
 * #lasVeinteReglasCorrenYLasQueNoTienenSujetoSeDicen()}, que aplica <b>las mismas</b> reglas sobre
 * <b>las mismas</b> clases y solo perdona las que se quedan sin sujeto, con su censo comprobado en
 * las dos direcciones.
 *
 * <p><b>No se sustituye con un metodo del mismo nombre</b> y conviene decir por que, porque es la
 * trampa evidente: la clase base esta en OTRO paquete y su metodo es de paquete, asi que un metodo
 * homonimo aqui <b>no lo sobrescribe</b> — JUnit descubre los dos y los ejecuta los dos. Medido: «6
 * tests completed, 2 failed», con el mismo nombre de prueba dos veces y el rojo original intacto.
 */
@ExtendWith(ArquitecturaTest.SinCapaWebTodavia.class)
class ArquitecturaTest extends ArquitecturaTestBase {

    /** El metodo de la clase base que esta etapa no puede correr tal cual. */
    static final String METODO_DESACTIVADO = "elCodigoDeProduccionCumpleTodasLasReglas";

    /**
     * Las anotaciones con que Spring marca un controlador. Son las mismas que {@code
     * ReglasDeArquitectura} usa para acotar sus reglas de capa web, y se repiten aqui porque la
     * libreria no las publica; si algun dia dejaran de coincidir, el censo de abajo lo diria.
     */
    private static final Set<String> ANOTACIONES_DE_CONTROLADOR =
            Set.of(
                    "org.springframework.web.bind.annotation.RestController",
                    "org.springframework.stereotype.Controller");

    /** Como empieza la descripcion de toda regla acotada a la capa web. */
    private static final String ACOTADA_A_CONTROLADORES = "classes that son controladores HTTP";

    /**
     * Cuantas hay hoy, medido y no supuesto.
     *
     * <p>Se cuenta ademas de comprobar la forma: sin el numero, una regla nueva de capa web
     * entraria en el grupo de las perdonadas sin que nadie lo decidiera; con el, sale roja pidiendo
     * que alguien mire.
     */
    private static final int REGLAS_DE_CAPA_WEB = 5;

    /**
     * Las veinte reglas, sobre las mismas clases, perdonando solo las que no tienen a quien mirar.
     *
     * <p>El permiso se da <b>solo</b> mientras este arbol no tenga ni un controlador, y eso se
     * comprueba <b>antes</b> de darlo; y <b>solo</b> a reglas acotadas a la capa web, y eso se
     * comprueba despues. El dia que llegue el primer controlador —la etapa 2— caen las dos cosas a
     * la vez, y el remedio es borrar esta clase entera y dejar que corra la de la libreria.
     */
    @Test
    @DisplayName("las veinte reglas corren, y las que no tienen sujeto se dicen")
    void lasVeinteReglasCorrenYLasQueNoTienenSujetoSeDicen() {
        JavaClasses deProduccion = ReglasDeArquitectura.clasesDeProduccion();

        assertThat(
                        deProduccion.stream()
                                .filter(ArquitecturaTest::esControlador)
                                .map(JavaClass::getName)
                                .sorted()
                                .toList())
                .as(
                        "llego el primer controlador de este sistema, asi que las reglas de capa web"
                                + " YA tienen a quien mirar: borra esta clase entera —el @ExtendWith"
                                + " incluido— y deja correr la de ArquitecturaTestBase, que las aplica"
                                + " sin ningun permiso")
                .isEmpty();

        List<String> sinSujeto = new ArrayList<>();
        for (ArchRule regla : ReglasDeArquitectura.todas()) {
            try {
                regla.check(deProduccion);
            } catch (AssertionError fallo) {
                if (!seQuedoSinClases(fallo)) {
                    throw fallo;
                }
                sinSujeto.add(regla.getDescription());
            }
        }

        assertThat(sinSujeto)
                .as(
                        "toda regla que hoy se quede sin clases tiene que ser una de capa web. Una"
                                + " que no lo sea es una regla que dejo de proteger algo sin que nadie lo"
                                + " decidiera, y este permiso no la cubre")
                .allSatisfy(
                        descripcion -> assertThat(descripcion).startsWith(ACOTADA_A_CONTROLADORES));
        assertThat(sinSujeto)
                .as(
                        "y tienen que ser exactamente las %d medidas: si aparece una mas, alguien"
                                + " anadio una regla de capa web que aqui nace perdonada; si hay menos,"
                                + " una dejo de estar acotada a los controladores y este permiso le sobra",
                        REGLAS_DE_CAPA_WEB)
                .hasSize(REGLAS_DE_CAPA_WEB);
    }

    private static boolean esControlador(JavaClass clase) {
        return clase.getAnnotations().stream()
                .anyMatch(a -> ANOTACIONES_DE_CONTROLADOR.contains(a.getRawType().getName()));
    }

    /**
     * Distingue «esta regla no encontro clases» de «esta regla encontro un incumplimiento».
     *
     * <p>Se compara contra el texto que ArchUnit emite porque no publica ningun tipo de excepcion
     * propio para el caso. Si algun dia cambiara esa frase, esto devolveria {@code false} y el
     * fallo se relanzaria tal cual — que es el lado seguro: se veria un rojo, no un verde.
     */
    private static boolean seQuedoSinClases(AssertionError fallo) {
        String mensaje = fallo.getMessage();
        return mensaje != null && mensaje.contains("failed to check any classes");
    }

    /**
     * Desactiva el metodo de la clase base que esta etapa no puede correr, y <b>solo</b> ese.
     *
     * <p>Se comprueba que el metodo siga existiendo con ese nombre: si la libreria lo renombra,
     * esta condicion dejaria de desactivar nada y el rojo original volveria —que es lo correcto—,
     * pero sin decir por que. La comprobacion lo dice.
     */
    static final class SinCapaWebTodavia implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext contexto) {
            Optional<Method> metodo = contexto.getTestMethod();
            if (metodo.isEmpty()) {
                exigirQueElMetodoDeLaBaseSigaExistiendo();
                return ConditionEvaluationResult.enabled("la clase entera corre");
            }
            if (!METODO_DESACTIVADO.equals(metodo.get().getName())) {
                return ConditionEvaluationResult.enabled("no es el metodo desactivado");
            }
            return ConditionEvaluationResult.disabled(
                    "ETAPA 1: este sistema tiene dominio y NO tiene capa web, y las cinco reglas"
                            + " acotadas a los controladores se quedan sin clases que revisar. La"
                            + " libreria no puede expresarlo —`sinContextosAcotadosTodavia()` exige que"
                            + " tampoco haya dominio, y aqui lo hay—, asi que las veinte reglas las"
                            + " aplica `lasVeinteReglasCorrenYLasQueNoTienenSujetoSeDicen`, en esta misma"
                            + " clase, perdonando SOLO las que no tienen sujeto y contandolas");
        }

        private static void exigirQueElMetodoDeLaBaseSigaExistiendo() {
            boolean existe =
                    List.of(ArquitecturaTestBase.class.getDeclaredMethods()).stream()
                            .anyMatch(m -> METODO_DESACTIVADO.equals(m.getName()));
            assertThat(existe)
                    .as(
                            "`ArquitecturaTestBase` ya no declara «%s»: esta condicion no esta"
                                    + " desactivando nada y el permiso de la etapa 1 se quedo sin efecto."
                                    + " O el metodo se renombro —y hay que renombrarlo aqui— o la libreria"
                                    + " ya sabe dar el permiso por ambito, y entonces esta clase sobra",
                            METODO_DESACTIVADO)
                    .isTrue();
        }
    }
}
