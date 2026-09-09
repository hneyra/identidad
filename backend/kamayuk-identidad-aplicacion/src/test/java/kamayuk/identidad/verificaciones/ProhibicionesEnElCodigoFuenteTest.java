package kamayuk.identidad.verificaciones;

import kamayuk.comun.verificaciones.ProhibicionesEnElCodigoFuenteTestBase;

/**
 * Las prohibiciones de texto de ARQ-04 §2 sobre el codigo de `identidad`: {@code SET SESSION}, el
 * {@code DELETE} sobre tabla protegida, el {@code UPDATE} sobre una inmutable y el literal numerico
 * tributario.
 *
 * <p>Recorre {@code src/main} de todos los modulos de <b>este</b> repositorio —111 archivos hoy, el
 * baseline y {@code crear-roles.sql} incluidos— y el minimo que {@link
 * ConfiguracionDeIdentidad#minimoDeFuentesDeProduccion()} declara es lo que impide que un recorrido
 * que no encuentre nada pase por bueno. Las pruebas que demuestran el escaner sobre sus muestras
 * corren ademas, y esas viajan con la libreria.
 */
class ProhibicionesEnElCodigoFuenteTest extends ProhibicionesEnElCodigoFuenteTestBase {}
