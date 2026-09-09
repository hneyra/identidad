/**
 * El contexto acotado de <b>identidad</b>: usuarios, grupos, miembros, permisos, modulos y accesos
 * (ADR-0039).
 *
 * <h2>Hoy esta vacio, y es correcto que se vea asi</h2>
 *
 * <p>La etapa 1 de {@code infrastructure#52} construye el repositorio —que existe, verifica y
 * despliega— y <b>ni una regla de negocio</b>. Las <b>once escrituras de administracion</b> —altas
 * y bajas de usuario, grupos, afiliaciones, permisos por grupo y las excepciones por usuario, con
 * sus vigencias— siguen viviendo en {@code rentas} (ADR-0030 §3) y las trae la <b>etapa 2</b>,
 * junto con sus pantallas y las seis opciones que {@code CatalogoDelSistema} ya declara.
 *
 * <p>Lo que si esta desde hoy es todo lo que hace que esas once nazcan vigiladas: el esquema con
 * sus ocho tablas y su RLS forzada, la copia local que autoriza, y las cinco barreras de {@code
 * comun-verificaciones} corriendo sobre este modulo desde su primera clase.
 *
 * <p><b>Un paquete con solo este archivo no es un modulo para Spring Modulith</b> —hace falta al
 * menos un tipo—, asi que {@code ModulosTest} no lo nombra todavia y lo dice donde se lee. La
 * primera clase que entre lo convierte en modulo, y entonces esa lista tiene que crecer.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.identidad.nucleo;
