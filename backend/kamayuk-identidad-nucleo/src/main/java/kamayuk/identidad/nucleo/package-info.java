/**
 * El contexto acotado de <b>identidad</b>: usuarios, grupos, miembros, permisos, modulos y accesos
 * (ADR-0039).
 *
 * <h2>De donde vino este codigo, y por que su historia no esta aqui</h2>
 *
 * <p>Las once escrituras de administracion, sus dos repositorios y sus cuatro controladores se
 * copiaron de {@code rentas} en la <b>etapa 2</b> de {@code infrastructure#52}, del commit
 * <b>{@code 33f329a23b7faed9b6fc15acd4a11406df32b362}</b> de ese repositorio, donde vivian en
 * {@code backend/kamayuk-rentas-seguridad}. Se nombra el {@code sha} y no se dice «se movieron»
 * porque <b>{@code git mv} no cruza repositorios</b>: la historia de estas clases —quien escribio
 * la regla del ultimo administrador, por que {@code PermisoEfectivo} publica su origen, que midio
 * el issue que separo lo configurado de lo efectivo— se queda en el {@code git log} de {@code
 * rentas}, y ahi hay que ir a buscarla. Es lo mismo que P5A hizo con {@code sgtm}.
 *
 * <p><b>{@code rentas} conservo sus escrituras durante las etapas 2 y 3</b>, y fue deliberado
 * (AC-7): hasta que tuviera su consumidor de eventos, quitarselas lo habria dejado peor que antes,
 * y en ese intervalo hubo <b>dos sitios donde se administraba</b>. La <b>etapa 4</b> lo cerro: los
 * cuatro sistemas consumen el buzon de aqui, y {@code rentas} retiro sus once escrituras, sus dos
 * repositorios y las cuatro opciones de {@code SEGURIDAD} que las servian. Desde entonces <b>este
 * es el unico sitio donde se administra</b>.
 *
 * <h2>Las once escrituras y sus siete hechos</h2>
 *
 * <p>Alta, baja, reactivacion y vigencia de <b>grupo</b> y de <b>usuario</b> (ocho), la afiliacion
 * y la desafiliacion en una sola ruta (nueve), y las dos matrices de permisos —{@code PUT
 * /grupos/&#123;id&#125;/permisos} y {@code PUT /usuarios/&#123;id&#125;/permisos}— (once). Cada
 * una exige {@link kamayuk.identidad.dominio.Observacion} (regla 10), asienta auditoria y
 * <b>publica su hecho en el buzon dentro de la misma transaccion</b> (ADR-0028 §3): si la fila
 * esta, el evento esta.
 *
 * <h2>Lo que se quedo en {@code rentas} a proposito</h2>
 *
 * <p>La <b>sesion</b> entera —{@code GET /seguridad/sesion}, su municipalidad, su matriz de
 * permisos, el ejercicio de trabajo—, la <b>consulta de la bitacora</b>, los <b>respaldos</b> y
 * {@code PUT /usuarios/&#123;id&#125;/clave}. No son administracion: son el estado de trabajo de
 * <b>ese</b> sistema, y cada uno de los cinco tiene el suyo contra su propia copia local (D-N5).
 * Traerlos habria puesto un viaje de red en el camino de cada peticion de los otros cuatro, que es
 * exactamente lo que ADR-0039 descarto.
 *
 * <h2>La diferencia que hay que conocer antes de tocar nada: el par {@code (sistema, codigo)}</h2>
 *
 * <p>En {@code rentas} un acceso se identifica por su codigo. Aqui no, y no es un detalle de
 * esquema: esta base guarda a quien se le concede cada opcion de los <b>cinco</b> catalogos, asi
 * que {@code permisos} es una opcion de {@code identidad} <b>y otra</b> de {@code rentas}, y {@code
 * SEGURIDAD} es un modulo de tres de los cinco. Toda firma que resuelva un acceso lleva el par, y
 * la que no lo llevara resolveria contra el catalogo equivocado — que es peor que fallar.
 */
@org.jspecify.annotations.NullMarked
package kamayuk.identidad.nucleo;
