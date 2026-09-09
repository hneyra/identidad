package kamayuk.identidad.verificaciones;

import kamayuk.comun.verificaciones.FronteraDeSistemaTestBase;

/**
 * NINGUN_SQL_CRUZA_LA_FRONTERA_DE_SISTEMA, aplicada a `identidad`.
 *
 * <p>Hoy no encuentra ningun cruce, y no porque no haya codigo —hay 111 fuentes de produccion— sino
 * porque las once escrituras de administracion todavia viven en `rentas` y lo unico que este
 * repositorio consulta son tablas replicadas. Lo que importa es que este DESDE EL PRINCIPIO: la
 * etapa 2 trae esas once, y cada consulta suya que cruce a otro sistema se pone roja al entrar, en
 * vez de descubrirse en produccion el dia que la base se parta.
 *
 * <p>Quien reparte es {@code ConfiguracionDeIdentidad.sistemaDelArchivo}, por modulo Gradle, y su
 * {@code modulosDelReparto()} exige que ningun modulo del disco se quede fuera: una clave que deja
 * de coincidir no da un cruce, DEJA DE REVISAR — y en verde (la leccion de R-N).
 */
class FronteraDeSistemaTest extends FronteraDeSistemaTestBase {}
