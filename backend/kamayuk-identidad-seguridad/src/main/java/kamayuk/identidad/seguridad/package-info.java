/**
 * La copia local de usuarios, grupos y permisos, y lo que la siembra.
 *
 * <h2>Por que este modulo existe en `identidad` y no solo en {@code rentas}</h2>
 *
 * <p>Lo decidio <b>D-N5</b> (2026-09-03): «usuarios, grupos y permisos se definen en Keycloak; cada
 * sistema guarda una copia local en tabla y su guardia la consulta». Con eso <b>D-19</b> quedo
 * contestada — el {@link kamayuk.identidad.autorizacion.ComprobadorDeAcceso} de cada sistema
 * pregunta a su propia tabla, <b>no a otro sistema por HTTP</b>.
 *
 * <p>La alternativa medida y descartada era preguntarle a {@code rentas} en cada peticion: el
 * guardia corre en un {@code preHandle}, asi que seria un viaje de red por peticion y, sobre todo,
 * {@code rentas} caido dejaria a {@code identidad} sin poder autorizar nada. Una comprobacion de
 * acceso que depende de la disponibilidad de otro despliegue no es una comprobacion de acceso: es
 * un acoplamiento con forma de politica de seguridad.
 *
 * <h2>Lo que quedo aqui despues de la etapa 2, y lo que se fue</h2>
 *
 * <p>En la etapa 1 este modulo tenia dos cosas: quien <b>lee</b> la copia para autorizar y quien la
 * <b>siembra</b> al implantar. La etapa 2 trajo las once escrituras desde {@code rentas} y con
 * ellas se llevo la siembra: {@code ImplantarMunicipalidad}, {@code SembradorDelCatalogo} y {@code
 * RegistroDeMunicipalidadesJdbc} viven ahora en {@code kamayuk.identidad.nucleo.aplicacion}, porque
 * el administrador ya no se escribe con SQL directo sino <b>llamando a los casos de uso</b> — que
 * es lo que hace que emita sus eventos (AC-6).
 *
 * <p>La mudanza fue de modulo Gradle y no solo de paquete, y hay un motivo que conviene dejar
 * escrito: los tipos de {@code nucleo.aplicacion} estan en un <b>subpaquete</b> del modulo, asi que
 * Spring Modulith no los expone y este modulo no puede llamarlos. La convencion de los cinco
 * repositorios es que un modulo solo use los tipos del paquete raiz de otro —medido: en {@code
 * rentas} no hay ni un {@code import} cruzado a un subpaquete en {@code src/main}—, y romperla con
 * una interfaz nombrada de Modulith habria metido una dependencia del framework en un contexto
 * acotado para arreglar un problema del sistema de modulos.
 *
 * <p>Aqui se queda <b>una sola clase de produccion</b>: {@code ComprobadorDeAccesoJdbc}, la
 * implementacion del puerto que el guardia pide. Es la que los otros cuatro tienen igual, y esa es
 * la razon de que no dependa del nucleo: tiene que poder autorizar aunque el contexto acotado no
 * este en el classpath.
 *
 * <p><b>Y lo que era un HUECO DECLARADO ya no lo es del todo</b>: como se sincroniza la copia
 * cuando alguien cambia un permiso. Desde la etapa 2 <b>lo escrito aqui se publica</b> en {@code
 * identidad_evento}, en la misma transaccion. Y desde la <b>etapa 4</b> el otro extremo existe: el
 * consumidor de cada uno de los cuatro lee ese buzon y lo acusa, y {@code rentas} retiro sus
 * escrituras, asi que ya no hay dos sitios donde se administre. Lo que sigue sin medirse es
 * <b>cuanto dura la ventana de inconsistencia</b>, que ADR-0039 exige medida y no supuesta: eso
 * solo se puede medir con las cinco aplicaciones levantadas, y es la etapa 5.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.identidad.seguridad;
