/**
 * Objetos de valor del dominio compartido: el vocabulario que los doce contextos acotados dan por
 * sabido (ARQ-01 §4 regla 6).
 *
 * <p>Es un paquete {@code dominio} de verdad, y no una bolsa de utilidades: aqui aplican las siete
 * reglas de ArchUnit sin excepcion —sin Spring, sin JPA, sin reloj, sin coma flotante— y por eso
 * este paquete es el que <b>apaga</b> {@code SIN_DOMINIO_TODAVIA}. Desde que existe, las reglas
 * acotadas a {@code ..dominio..} revisan codigo real en lugar de no encontrar nada.
 *
 * <p><b>Por que cuelga de {@code kamayuk.identidad} y no de {@code
 * kamayuk.identidad.compartido}.</b> Para Spring Modulith un subpaquete es interno a su modulo, y
 * un objeto de valor que ningun contexto puede importar no sirve de vocabulario comun. Como modulo
 * propio queda expuesto sin necesidad de anotar el paquete, que es lo que permite que este modulo
 * Gradle siga sin depender de Spring —la regla 7 en su forma mas literal: aqui no hay nada que
 * importar de un framework, ni siquiera una anotacion.
 *
 * <p>Lo que <b>no</b> vive aqui: cualquier operacion que devuelva un importe determinado. Eso es
 * regla de calculo y esta bloqueado por D-02a. {@link kamayuk.identidad.dominio.Dinero} sabe sumar
 * y restar; no sabe cuanto se debe.
 *
 * <p>{@code @NullMarked}: todo es no nulo salvo lo marcado {@code @Nullable} (ARQ-04 §4).
 *
 * <h2>Esto es la QUINTA COPIA, y no se esconde</h2>
 *
 * <p>Este paquete llego copiado de {@code normativa} —que a su vez lo copio de {@code rentas} en
 * P5B— con el paquete renombrado, y es byte a byte el mismo que el de los otros cuatro sistemas
 * salvo por el ancho de linea que Spotless recalcula cuando el nombre del paquete cambia de
 * longitud. Con `identidad` pasan a ser <b>cinco copias del mismo codigo en cinco repositorios</b>,
 * y ninguna guarda las obliga a no divergir: lo unico que hoy las mide es
 * `lo-que-los-cinco-comparten` de `infrastructure`, que las CENSA — dice cuando difieren, no lo
 * impide.
 *
 * <p>Lo que corresponde no es recortarla aqui —eso volveria a esta la unica de las cinco distinta—
 * sino sacarla a {@code kamayuk-lib}, que es lo que <b>ADR-0038</b> decidio al contestar D-23 el
 * 2026-09-07. Ese repositorio <b>hoy esta vacio</b>, asi que la quinta copia se anade sabiendo lo
 * que cuesta: los tres defectos que {@code lo-que-los-cinco-comparten} encontro en su primer censo
 * —la observacion ausente que salia 500, el 422 de orden que no dice por que campos se puede
 * ordenar, y la bitacora que escribia texto que no era JSON en una columna {@code jsonb}— se
 * arreglaron UNA vez y las otras copias no se enteraron. Con cinco, la cuenta sube.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.identidad.dominio;
