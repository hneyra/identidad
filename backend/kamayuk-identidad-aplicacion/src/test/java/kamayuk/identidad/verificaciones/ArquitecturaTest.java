package kamayuk.identidad.verificaciones;

import kamayuk.comun.verificaciones.ArquitecturaTestBase;

/**
 * Las reglas de ARQ-04 §2 aplicadas al codigo de `identidad`.
 *
 * <p>El cuerpo esta en {@code comun-verificaciones}; lo que cambia lo declara {@link
 * ConfiguracionDeIdentidad}, que encuentra {@code ServiceLoader}.
 *
 * <p>Esta clase tiene que existir: sin ella la barrera no corre en este build.
 *
 * <h2>La extension de la etapa 1 se fue, y caduco sola como estaba escrito</h2>
 *
 * <p>Hasta la etapa 2 esta clase llevaba un {@code @ExtendWith(SinCapaWebTodavia.class)} que
 * desactivaba <b>un</b> metodo de la clase base y lo sustituia por uno propio. El motivo era que
 * este repositorio era la combinacion que {@code comun-verificaciones} no contempla —<b>tenia
 * dominio y no tenia capa web</b>—, asi que las cinco reglas acotadas a los controladores no
 * encontraban ni una clase y ArchUnit las rechazaba, correctamente: una regla que no puede fallar
 * no protege nada.
 *
 * <p>Su propio mensaje decia como se retiraba: «llego el primer controlador de este sistema, asi
 * que las reglas de capa web YA tienen a quien mirar: borra esta clase entera —el
 * {@code @ExtendWith} incluido— y deja correr la de {@code ArquitecturaTestBase}, que las aplica
 * sin ningun permiso». Con los cuatro controladores de {@code kamayuk.identidad.nucleo}, eso es lo
 * que se hace. <b>El hueco que quedaba declarado en {@code comun-verificaciones} —dar el permiso
 * por AMBITO, como se hace con {@code fiscalizacion} e {@code indicadores}— sigue abierto</b>, y
 * ahora sin sujeto en este repositorio: el siguiente sistema que nazca con dominio y sin capa web
 * se lo va a encontrar igual.
 */
class ArquitecturaTest extends ArquitecturaTestBase {}
