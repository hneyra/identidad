package kamayuk.identidad.verificaciones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kamayuk.comun.verificaciones.ConfiguracionDeLasVerificaciones;

/**
 * Lo que `identidad` declara de si mismo a las barreras de {@code comun-verificaciones}.
 *
 * <p>La descubre {@link java.util.ServiceLoader}: el descriptor esta en {@code
 * src/test/resources/META-INF/services/}. Si se borra, las barreras <b>no corren en silencio</b> —
 * fallan nombrando lo que falta, que es lo que este mecanismo compra frente a pasar la
 * configuracion por constructor.
 *
 * <p>Desde P5B este repositorio SI tiene negocio —el contexto acotado `parametros` y la libreria de
 * reglas—, asi que la exencion {@code sinContextosAcotadosTodavia()} que P3 dejo puesta se retiro.
 * Caducaba sola: las barreras exigian que en efecto no hubiera NADA en {@code ..dominio..}, y la
 * primera clase que llego las habria puesto en rojo pidiendo justo eso.
 */
public final class ConfiguracionDeIdentidad implements ConfiguracionDeLasVerificaciones {

    /**
     * El reparto por modulo Gradle (GOB-05 §1).
     *
     * <p>Los cinco que no son contextos acotados —{@code dominio-compartido}, {@code esquema},
     * {@code plataforma}, {@code seguridad} y {@code aplicacion}— van a {@link #SISTEMA_REPLICADO}:
     * no estan a ningun lado de la frontera, asi que no pueden cruzarla. {@code
     * kamayuk-identidad-esquema} entra ahi por el mismo motivo y por uno mas: sus migraciones crean
     * las trece tablas comunes, y ADR-0032 §1 dice que no se reparten sino que se rehacen como un
     * baseline por sistema.
     *
     * <p>El unico que es de este sistema es {@code kamayuk-identidad-nucleo}, y <b>hoy esta
     * vacio</b>. Se declara igual: el reparto se consulta con {@code getOrDefault(modulo,
     * SISTEMA_REPLICADO)} y «replicado» significa «no esta a ningun lado de la frontera», asi que
     * una clave que falta no da un cruce — <b>deja de revisarse</b>, en verde (la leccion de R-N).
     * Con el declarado, la primera consulta que la etapa 2 escriba en `nucleo` ya nace mirada por
     * la regla 11. Que ningun modulo del disco se quede fuera lo comprueba {@link
     * #modulosDelReparto()}.
     */
    private static final Map<String, String> SISTEMA_DEL_MODULO =
            Map.ofEntries(
                    Map.entry("kamayuk-identidad-nucleo", "identidad"),
                    Map.entry("kamayuk-identidad-dominio-compartido", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-identidad-esquema", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-identidad-plataforma", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-identidad-seguridad", SISTEMA_REPLICADO),
                    Map.entry("kamayuk-identidad-aplicacion", SISTEMA_REPLICADO));

    /**
     * El reparto de tablas de GOB-05 §2, ENTERO y no solo el de este sistema.
     *
     * <p>La regla {@code NINGUN_SQL_CRUZA_LA_FRONTERA_DE_SISTEMA} necesita saber de quien es la
     * tabla ajena para poder decir a que frontera pertenece el cruce; con solo las propias, una
     * consulta a {@code predio} desde {@code caja} seria «una tabla que nadie repartio» y pasaria
     * sin ruido.
     *
     * <p>Las transversales y las de seguridad se replican en los CINCO (§2.5 y §2.6), y por eso van
     * con {@link #SISTEMA_REPLICADO}: leerlas nunca es cruzar nada. Eso incluye las ocho de este
     * sistema, y conviene decir por que no se mueven a {@link #DE_IDENTIDAD}: la copia local se lee
     * legitimamente en los cinco —es lo que hace que el guardia de cada uno autorice sin un viaje
     * de red (D-N5)— y marcarlas como de `identidad` pondria en rojo el {@code
     * ComprobadorDeAccesoJdbc} de los otros cuatro. Lo que la etapa 2 tiene que vigilar es la
     * ESCRITURA, y eso no lo distingue una tabla en el reparto.
     */
    private static final Set<String> DE_RENTAS =
            Set.of(
                    "acta_fiscalizacion",
                    "acto_coactivo",
                    "anuncio",
                    "anuncio_correlativo",
                    "anuncio_movimiento",
                    "beneficio",
                    "certificado",
                    "certificado_correlativo",
                    "ciiu",
                    "codigo_infraccion",
                    "constancia_libre",
                    "contacto",
                    "contribuyente",
                    "convenio",
                    "convenio_correlativo",
                    "convenio_cuota",
                    "convenio_deuda",
                    "convenio_movimiento",
                    "corrida_predial",
                    "corrida_predial_observado",
                    "costa_obligacion",
                    "costa_procesal",
                    "cuenta_corriente_asiento",
                    "cuenta_corriente_asiento_2026",
                    "cuenta_corriente_asiento_2027",
                    "declaracion_jurada",
                    "descargo",
                    "determinacion",
                    "determinacion_2026",
                    "determinacion_2027",
                    "determinacion_arbitrio",
                    "determinacion_arbitrio_2026",
                    "determinacion_arbitrio_2027",
                    "determinacion_predio_detalle",
                    "determinacion_predio_detalle_2026",
                    "determinacion_predio_detalle_2027",
                    "dj_correlativo",
                    "domicilio",
                    "edificacion_correlativo",
                    "edificacion_estructura",
                    "edificacion_movimiento",
                    "edificacion_profesional",
                    "edificacion_proyecto",
                    "edificacion_requisito",
                    "edificacion_terreno",
                    "edificacion_vigencia",
                    "espectaculo",
                    "expediente_coactivo",
                    "expediente_correlativo",
                    "expediente_movimiento",
                    "expediente_valor",
                    "internamiento",
                    "internamiento_movimiento",
                    "licencia_correlativo",
                    "licencia_duplicado",
                    "licencia_edificacion",
                    "licencia_funcionamiento",
                    "licencia_giro",
                    "licencia_movimiento",
                    "liquidacion_correlativo",
                    "liquidacion_costas",
                    "liquidacion_costas_correlativo",
                    "liquidacion_detalle",
                    "liquidacion_fiscalizacion",
                    "liquidacion_movimiento",
                    "notificacion",
                    "notificacion_administrativa",
                    "papeleta",
                    "papeleta_cambio_numero",
                    "papeleta_masivo",
                    "papeleta_masivo_item",
                    "prescripcion",
                    "prescripcion_ejercicio",
                    "prescripcion_hecho",
                    "programa_fiscalizacion",
                    "programa_muestra",
                    "resolucion_determinacion",
                    "resolucion_gerencia",
                    "responsable_solidario",
                    "saldo_proyectado",
                    "transferencia",
                    "valor",
                    "valor_correlativo",
                    "valor_detalle",
                    "valor_masivo",
                    "valor_masivo_item",
                    "valor_movimiento",
                    "vehiculo");

    private static final Set<String> DE_CATASTRO =
            Set.of(
                    "actividad_economica",
                    "arancel",
                    "bien_comun",
                    "colindante_rural",
                    "construccion",
                    "ficha_catastral",
                    // V6: el frente del predio. Se nombra aunque este sistema no la tenga —y por
                    // eso
                    // mismo—: sin la entrada, el reparto la da por «replicada» y el escaner de la
                    // regla 11 DEJA DE MIRAR un cruce contra ella, en verde (la leccion de R-N).
                    "frente_predio",
                    "inquilino",
                    "manzana",
                    "otra_instalacion",
                    "participacion_comun",
                    "predio",
                    "sector",
                    "tierra_rural",
                    "titularidad",
                    "via",
                    // Las CATORCE que `catastro` ha creado desde T-0 y que aqui faltaban. Se
                    // nombran
                    // aunque este sistema no las tenga —y por eso mismo—: el reparto se consulta
                    // con `getOrDefault(tabla, SISTEMA_REPLICADO)` y «replicado» significa «no esta
                    // a ningun lado de la frontera», asi que una tabla que FALTA no da un cruce:
                    // DEJA DE REVISARSE, en verde (la leccion de R-N, y lo que el censo de
                    // `catastro#7` midio). Nombrar de mas no cuesta nada —ningun archivo de este
                    // repositorio las menciona— y es lo que hace que el cruce, si llega, se vea.
                    //
                    // V5 el buzon de salida; V7 (#4) la zonificacion; V8 (#5) la gestion del
                    // riesgo; V9 (#6, ADR-0035) el hallazgo catastral; V10 (#7) la derivacion de
                    // frentes. `acta` es la de CATASTRO —el acta del hallazgo—: la tributaria de
                    // `rentas` se llama `acta_fiscalizacion` y sigue siendo suya.
                    "acta",
                    "campania",
                    "candidato",
                    "catastro_evento",
                    "evidencia",
                    "faja_marginal",
                    "frente_derivacion",
                    "habilitacion_urbana",
                    "hallazgo",
                    "itse",
                    "parametro_urbanistico",
                    "seccion_via",
                    "zona_riesgo",
                    "zonificacion");

    private static final Set<String> DE_NORMATIVA =
            Set.of(
                    "conjunto_parametro_detalle",
                    "conjunto_parametros",
                    "depreciacion",
                    "parametro_tributario",
                    "valor_referencial_vehiculo",
                    "valor_unitario_edificacion");

    private static final Set<String> DE_CAJA =
            Set.of(
                    "area",
                    "caja",
                    "cierre_caja",
                    "cierre_turno",
                    "cierre_turno_detalle",
                    "recibo",
                    "recibo_correlativo",
                    "recibo_detalle",
                    "recibo_movimiento",
                    "tasa");

    /**
     * Las tablas propias de `identidad`: <b>una</b> desde la etapa 2, y es la que su javadoc
     * anticipaba.
     *
     * <p>Las ocho de la autorizacion —{@code municipalidad}, {@code usuario}, {@code grupo}, {@code
     * miembro}, {@code modulo_sistema}, {@code acceso}, {@code permiso} y {@code sesion}— <b>siguen
     * en {@link #REPLICADAS} a proposito</b>, aunque este sistema sea su dueño (ADR-0039): estan en
     * los cinco baselines (ADR-0032) y los cinco las leen para autorizar. Marcarlas de `identidad`
     * significaria «leerlas desde otro sistema es cruzar la frontera», y leerlas es exactamente lo
     * que los otros cuatro tienen que hacer — pondria en rojo el {@code ComprobadorDeAccesoJdbc} de
     * los cuatro. Lo que la etapa 2 vigila es la ESCRITURA, y eso no lo distingue una tabla en el
     * reparto: lo hace el escaner de AC-5, que vive en {@code comun-verificaciones}.
     *
     * <p><b>{@code identidad_evento} si es de aqui</b>, y es la primera. Lo que este sitio pedia
     * decidir —«que le pasa a los cuatro lectores»— tiene respuesta: no la leen. El buzon se sirve
     * por HTTP en la etapa 3, asi que un {@code JOIN} contra el desde otro sistema no es una
     * lectura legitima que haya que permitir, es la que hay que ver. Declararla aqui es lo que hace
     * que se vea: sin la entrada, el reparto la da por «replicada» —{@code getOrDefault(tabla,
     * SISTEMA_REPLICADO)}— y el escaner de la regla 11 <b>deja de mirarla</b>, en verde (la leccion
     * de R-N).
     *
     * <p><b>Y desde la etapa 3, {@code identidad_evento_acuse}</b>, por el mismo motivo y con una
     * vuelta de tuerca: no solo ningun otro sistema la lee, es que <b>ninguno puede saber lo que
     * dice</b> — lo que hay ahi es lo que cada uno de los cuatro ya aplico, y se pregunta pidiendo
     * el buzon, no leyendo la tabla. Un {@code JOIN} contra ella desde otro sistema es siempre el
     * defecto.
     */
    private static final Set<String> DE_IDENTIDAD =
            Set.of("identidad_evento", "identidad_evento_acuse");

    private static final Set<String> REPLICADAS =
            Set.of(
                    "acceso",
                    "auditoria",
                    "auditoria_2026",
                    "auditoria_2027",
                    "documento_emitido",
                    "grupo",
                    "miembro",
                    "modulo_sistema",
                    "municipalidad",
                    "permiso",
                    "respaldo",
                    "sesion",
                    "usuario");

    @Override
    public String paqueteRaiz() {
        return "kamayuk.identidad";
    }

    @Override
    public String sistema() {
        return "identidad";
    }

    @Override
    public String raizDeLaApi() {
        return kamayuk.identidad.web.Api.RAIZ;
    }

    @Override
    public String sistemaDelArchivo(String rutaRelativa) {
        String normalizada = rutaRelativa.replace('\\', '/');
        int barra = normalizada.indexOf('/');
        String modulo = barra < 0 ? normalizada : normalizada.substring(0, barra);
        return SISTEMA_DEL_MODULO.getOrDefault(modulo, SISTEMA_REPLICADO);
    }

    @Override
    public Set<String> modulosDelReparto() {
        return SISTEMA_DEL_MODULO.keySet();
    }

    @Override
    public Map<String, String> sistemaDeCadaTabla() {
        Map<String, String> reparto = new HashMap<>();
        DE_RENTAS.forEach(t -> reparto.put(t, "rentas"));
        DE_CATASTRO.forEach(t -> reparto.put(t, "catastro"));
        DE_NORMATIVA.forEach(t -> reparto.put(t, "normativa"));
        DE_CAJA.forEach(t -> reparto.put(t, "caja"));
        DE_IDENTIDAD.forEach(t -> reparto.put(t, "identidad"));
        REPLICADAS.forEach(t -> reparto.put(t, SISTEMA_REPLICADO));
        return Map.copyOf(reparto);
    }

    /**
     * Vacia, y tiene que estarlo: sin codigo no puede haber ningun cruce que consentir.
     *
     * <p>{@code FronteraDeSistemaTest} lo comprueba. Cuando la etapa 2 traiga las once escrituras,
     * el cruce que aparezca entra aqui con su issue y no sin el: una excepcion sin dueño no es una
     * excepcion, es un olvido con permiso.
     */
    @Override
    public List<CruceConsentido> crucesConsentidos() {
        return List.of();
    }

    /**
     * RNF-051: de aqui no se borra. Las trece tablas de este esquema.
     *
     * <p>No es una lista larga por costumbre. Lo dice el propio {@code SembradorDeLaCopiaLocal}
     * cuando explica por que solo agrega: «los permisos que cuelgan de un acceso retirado son
     * constancia de quien pudo hacer que, y eso no se borra». Lo mismo vale para el usuario que se
     * da de baja —{@code habilitado = false}, no {@code DELETE}—, para la pertenencia a un grupo
     * (que lleva {@code fecha_baja} y {@code usuario_baja} justo para eso) y para la sesion, que se
     * cierra poniendo su {@code fin}. Un {@code DELETE} en cualquiera de las trece borra el rastro
     * de quien pudo hacer que, que es el dato que este sistema existe para guardar.
     *
     * <p>Son <b>dos guardas independientes</b> y basta una para parar la escritura: ninguna de las
     * trece concede {@code DELETE} a ningun rol en el baseline. Pero solo el escaner dice
     * <i>cual</i> —el privilegio y la politica dan el mismo {@code 42501} y el sintoma no los
     * distingue (#435)—, y ademas el escaner muerde en el build y no en el motor: un {@code DELETE
     * FROM permiso} escrito en {@code src/main} rompe antes de llegar a ninguna base.
     */
    @Override
    public Set<String> tablasProtegidas() {
        return Set.of(
                "acceso",
                "auditoria",
                "documento_emitido",
                "grupo",
                // El buzon de salida (`V2`). Un evento borrado es un hecho que los otros cuatro
                // no recibiran nunca y del que no queda rastro de que se emitio: la copia del
                // vecino se queda desatrasada y nada dice por que.
                "identidad_evento",
                // Y su acuse (`V3`). Borrar un acuse es volver a servirle a un consumidor un
                // evento que ya aplico: la entrega es al menos una vez y el receptor deduplica,
                // asi que no rompe nada visible — y por eso mismo nadie se enteraria de que el
                // registro de lo entregado deja de ser cierto.
                "identidad_evento_acuse",
                "miembro",
                "modulo_sistema",
                "municipalidad",
                "permiso",
                "respaldo",
                "sesion",
                "usuario");
    }

    /**
     * Ademas de no borrarse, no se actualiza: <b>tres</b>.
     *
     * <p>{@code auditoria}, por ADR-0008 —quien puede modificarla puede borrar su rastro—.
     *
     * <p><b>Y desde la etapa 2, {@code identidad_evento}</b>, por un motivo propio: no tiene una
     * sola columna que cambie despues de escribirse. Es un OUTBOX y no lleva {@code estado} —tiene
     * CUATRO consumidores y un solo estado no puede decir «entregado a `caja` y no a `rentas`»; el
     * acuse por consumidor es de la etapa 3 y va en otra tabla—, asi que un {@code UPDATE} sobre
     * ella solo puede ser una cosa: reescribir un hecho ya publicado. El {@code GRANT} de {@code
     * V2} tampoco se lo da, y son dos guardas independientes: el motor lo niega con un 42501 que no
     * dice cual de las dos fue, y el escaner lo niega en el build nombrando archivo y linea (#435).
     *
     * <p><b>Y desde la etapa 3, {@code identidad_evento_acuse}</b>, que es la otra mitad de lo
     * mismo: un acuse o esta o no esta, y ninguna de sus cuatro columnas cambia despues de
     * escribirse. Un {@code UPDATE} ahi solo podria ser mover un acuse de un consumidor a otro, que
     * es exactamente lo que la clave primaria existe para impedir.
     *
     * <p><b>Las otras doce NO estan, y no es un olvido.</b> Las siete de la copia local reciben un
     * {@code UPDATE} legitimo y es el acto que este sistema existe para ejecutar: dar de baja a un
     * usuario es {@code habilitado = false}, cerrar una sesion es escribir su {@code fin}, quitar a
     * alguien de un grupo es su {@code fecha_baja}, y retirar una opcion es {@code activo = false}.
     * Meterlas aqui pondria el build en rojo por la etapa 2 entera. {@code documento_emitido}
     * tampoco: su {@code UPDATE} incrementa las reimpresiones, y lo que impide que cambie cualquier
     * otra cosa es su disparador, no esta lista. {@code municipalidad} y {@code respaldo} no las
     * escribe la aplicacion —no tienen ni el {@code GRANT}—, asi que declararlas aqui seria vigilar
     * con un escaner de fuentes algo que el motor ya niega, y por un camino que nadie recorre.
     */
    @Override
    public Set<String> tablasInmutables() {
        return Set.of("auditoria", "identidad_evento", "identidad_evento_acuse");
    }

    /**
     * La unica escritura transaccional de este sistema que no recibe una {@code Observacion}.
     *
     * <p><b>El acuse del buzon</b> (etapa 3). La regla 10 existe porque «el que» lo reconstruye
     * cualquier sistema y «el por que» solo lo sabe quien cambio el dato, en el momento de
     * cambiarlo; aqui no hay ningun dato del padron que cambie y no hay ninguna persona que lo
     * cambie: lo que se escribe es un <b>acuse de recibo</b> —«el sistema {@code caja} ya aplico
     * estos eventos»— y quien lo manda es un proceso por lotes con una cuenta de servicio, sin
     * ninguna peticion de usuario detras.
     *
     * <p>Exigir una observacion aqui produciria exactamente lo que el javadoc de la regla advierte:
     * una cadena fija escrita por el cliente en cada vuelta, que no dice nada y que ademas seria lo
     * primero que alguien copiaria al escribir la siguiente escritura sin usuario.
     *
     * <p>Y no se queda sin rastro, que es lo que la regla protege: lo que un acuse deja escrito
     * —quien acuso, que evento y cuando— <b>es</b> el registro completo del acto, en las cuatro
     * columnas de {@code identidad_evento_acuse}. No hay un «por que» que dar: el motivo de acusar
     * es haber aplicado.
     *
     * <p><b>#27: censo cerrado.</b> Una entrada que no case con ningun metodo del arbol exime a
     * quien nazca manana con ese nombre, asi que {@code SujetosDeLaConfiguracion} la pone en rojo.
     */
    @Override
    public Set<String> escriturasSinUsuarioQueObserve() {
        return Set.of(
                ".nucleo.aplicacion.EntregaDeEventos.acusar("
                        + "kamayuk.identidad.nucleo.dominio.Consumidor, java.util.List)");
    }

    /** Ninguna: aqui no se compone ningun area a mano, porque no hay predios que medir (#607). */
    @Override
    public Set<String> componenElAreaAManoConMotivo() {
        return Set.of();
    }

    /**
     * Los paquetes que este sistema declara suyos.
     *
     * <p>No es una formalidad. Sin nombrarlos, «hay clases que revisar» se conforma con que haya
     * <b>algo</b>, y el dia que {@code kamayuk-identidad-aplicacion} dejara de depender de un
     * modulo —una linea del {@code build.gradle.kts}— ArchUnit no lo veria y las reglas pasarian en
     * verde sin haber mirado ese modulo.
     *
     * <p>{@code kamayuk.identidad.esquema} <b>no</b> esta, y es deliberado: el migrador no esta en
     * el classpath de este modulo, y no debe estarlo —la aplicacion no migra al arrancar (ARQ-03
     * §4)—.
     */
    @Override
    public Set<String> paquetesQueTienenQueExistir() {
        return Set.of(
                "kamayuk.identidad.compartido",
                "kamayuk.identidad.dominio",
                "kamayuk.identidad.plataforma.tenant",
                // Las tres capas del modulo de seguridad, que es lo unico propio que este
                // repositorio tiene hoy. Se nombran las tres y no el paquete raiz: sin la de
                // `infraestructura`, quitarle a `kamayuk-identidad-aplicacion` la dependencia del
                // modulo —una linea del build— dejaria a ArchUnit sin ver al
                // `ComprobadorDeAccesoJdbc`
                // y las reglas pasarian en verde sin haber mirado el unico adaptador que hay.
                "kamayuk.identidad.seguridad.dominio",
                "kamayuk.identidad.seguridad.infraestructura",
                // Las cuatro capas del contexto acotado, que la etapa 2 lleno. Se nombran las
                // cuatro y no el paquete raiz: sin la de `infraestructura.web`, quitarle a
                // `kamayuk-identidad-aplicacion` la dependencia del modulo —una linea del build—
                // dejaria a ArchUnit sin ver ni un controlador y las CINCO reglas de capa web
                // pasarian en verde sin haber mirado ninguno. Es lo que la etapa 1 anoto que
                // habria que hacer aqui.
                //
                // `kamayuk.identidad.seguridad.aplicacion` YA NO ESTA porque ese paquete ya no
                // existe: la implantacion se mudo a `nucleo.aplicacion` para poder escribir por
                // los casos de uso (AC-6), y una entrada que nombra un paquete inexistente no
                // exime ni exige nada — se queda esperando al que herede el nombre (#27).
                "kamayuk.identidad.nucleo.dominio",
                "kamayuk.identidad.nucleo.aplicacion",
                "kamayuk.identidad.nucleo.infraestructura",
                "kamayuk.identidad.nucleo.infraestructura.web");
    }

    /**
     * El minimo existe para que el escaner no pase sin revisar nada, asi que tiene que ser un
     * numero de verdad: si manana desapareciera medio repositorio, el escaner lo diria en vez de
     * quedarse en verde.
     *
     * <p><b>Remedido en la etapa 2 y redondeado a la baja</b>: {@code src/main} tiene hoy
     * <b>143</b> archivos entre {@code .java} y {@code .sql} —eran 111 en la etapa 1—, y el salto
     * es el contexto acotado: las once escrituras, sus dos repositorios, los cuatro controladores,
     * el buzon y {@code V2}. Se declara <b>130</b>. La etapa 1 declaro 100 y dijo que subiria con
     * esta; lo que no puede es bajar sin que alguien lo note.
     */
    @Override
    public int minimoDeFuentesDeProduccion() {
        return 130;
    }

    /**
     * Mismo motivo que el minimo de arriba, medido y redondeado a la baja: {@code src/test} y
     * {@code src/testFixtures} suman hoy <b>70</b> archivos —eran 59 en la etapa 1—, y los once que
     * entran son los del contexto acotado. Se declara <b>55</b>; la etapa 1 declaraba 40.
     */
    @Override
    public int minimoDePruebas() {
        return 55;
    }

    /**
     * Los dos ambitos que solo existen en {@code rentas}, declarados ausentes.
     *
     * <p>Sin esto, las dos reglas acotadas a ellos —la frontera de {@code fiscalizacion} y el panel
     * de recaudacion— correrian con {@code allowEmptyShould(true)} y nadie miraria. La declaracion
     * NO las apaga: {@code ArquitecturaTestBase} exige que el ambito declarado ausente lo este de
     * verdad, asi que el dia que aparezca una clase suya la prueba se pone roja.
     */
    @Override
    public Set<String> ambitosAusentes() {
        return Set.of("fiscalizacion", "indicadores");
    }
}
