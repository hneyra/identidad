/**
 * El guardia: los siete privilegios del manual, comprobados <b>en el servidor</b> (ADR-0005,
 * RF-121).
 *
 * <p>Que la interfaz oculte una opcion de menu es comodidad, no seguridad: la peticion se puede
 * hacer igual con {@code curl}. La comprobacion que cuenta es esta, y ocurre antes de que el
 * controlador reciba el control.
 *
 * <h2>Por que esta aqui y no en el contexto que administra la seguridad</h2>
 *
 * <p>Todo contexto declara su acceso con {@link kamayuk.identidad.autorizacion.RequiereAcceso}, asi
 * que la anotacion y el enum tienen que estar en un modulo que todos puedan importar. Si vivieran
 * en el contexto de seguridad, cada contexto dependeria de el —lo que ARQ-01 admite— pero aquel no
 * podria aplicar las mismas convenciones sin depender de si mismo.
 *
 * <p>De ahi el reparto: aqui el <b>contrato</b> —la anotacion, el enum y el puerto {@link
 * kamayuk.identidad.autorizacion.ComprobadorDeAcceso}—, y en el contexto de seguridad la
 * <b>implementacion</b>, que es la que sabe de {@code acceso}, {@code grupo}, {@code miembro} y
 * {@code permiso}. La capa web no conoce el modelo de autorizacion; solo sabe preguntarle.
 *
 * <p><b>En `identidad` ese reparto es temporal, y conviene saberlo antes de tocar nada.</b> Hoy la
 * implementacion vive donde en los otros cuatro: en {@code kamayuk.identidad.seguridad}, que es la
 * copia local que se lee para autorizar (D-N5). La etapa 2 trae aqui las once escrituras de
 * administracion (ADR-0039), y entonces este sistema pasa a ser el <b>dueño</b> del modelo de
 * autorizacion y no un lector mas — pero el reparto de arriba no cambia por eso: la anotacion y el
 * enum siguen teniendo que estar en un modulo que todos puedan importar, incluido el contexto que
 * administra la seguridad, que si no dependeria de si mismo.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.identidad.autorizacion;
