/**
 * Infraestructura tecnica compartida: el camino del contexto de tenant, del claim del token al
 * {@code SET LOCAL} (ARQ-03 §2). No es un contexto acotado.
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
package kamayuk.identidad.plataforma;
