package kamayuk.identidad.nucleo.dominio;

/**
 * Los hechos que este sistema publica en su buzon de salida (AC-2, AC-3).
 *
 * <h2>Que hace suficiente a esta lista, y como se mide</h2>
 *
 * <p>La pregunta no es «¿estan todos los actos?» sino «¿un consumidor que solo vea estos eventos
 * puede reconstruir {@code usuario}, {@code grupo}, {@code miembro} y {@code permiso} tal como
 * quedaron?». Eso no se razona: se ejecuta. {@code ReconstruccionDesdeElBuzonTest} ejerce las once
 * escrituras, aplica los eventos en orden sobre un esquema vacio y compara las cuatro tablas fila a
 * fila. Un tipo que dejara de emitirse deja la copia distinta y la prueba lo dice <b>nombrando la
 * tabla</b>.
 *
 * <h2>Siete y no once: los tipos son HECHOS, no metodos</h2>
 *
 * <p>Las once escrituras producen siete tipos porque varias dejan la fila en el mismo estado
 * observable: la baja, la reactivacion y el cambio de vigencia de un usuario son todas {@code
 * USUARIO_MODIFICADO}, porque lo que el consumidor necesita saber es <b>como quedo la fila</b>, no
 * por que puerta se llego. Un tipo por metodo obligaria al consumidor a implementar la semantica de
 * cada acto —y a acertar—, que es justo lo que {@code PermisoEfectivo} evita del otro lado.
 *
 * <p><b>Por que motivo el cuerpo lleva la fila ENTERA y no el delta</b>: un delta obliga a que el
 * consumidor tenga el estado anterior, o sea a que no se haya perdido ni un evento nunca. Con la
 * fila entera, un consumidor que empieza de cero a mitad de la historia converge igual, y uno que
 * recibe un evento dos veces escribe lo mismo dos veces. Es la misma decision que {@code
 * catastro_evento.cuerpo} (C-8) y que {@code pago_evento.cuerpo}.
 */
public enum TipoDeEventoDeIdentidad {

    /** Nace una cuenta. El cuerpo es la fila entera de {@code usuario}. */
    USUARIO_DADO_DE_ALTA,

    /**
     * La cuenta cambia de estado: baja, reactivacion o vigencia.
     *
     * <p>Los tres son el mismo hecho —«esta fila quedo asi»— y por eso no son tres tipos. Quien
     * quiera saber cual de los tres fue lo tiene en la auditoria, que es donde vive el por que.
     */
    USUARIO_MODIFICADO,

    /** Nace un grupo. El cuerpo es la fila entera de {@code grupo}. */
    GRUPO_DADO_DE_ALTA,

    /** El grupo cambia de estado: baja, reactivacion o vigencia. Mismo criterio que la cuenta. */
    GRUPO_MODIFICADO,

    /** Alguien entra a un grupo, o vuelve a entrar. */
    MIEMBRO_AFILIADO,

    /**
     * Alguien sale de un grupo.
     *
     * <p>Es un tipo propio y no un {@code MIEMBRO_AFILIADO} con {@code activo: false}, aunque el
     * cuerpo lo diga igual: un consumidor que filtrara por tipo para saber quien entro se llevaria
     * tambien a los que salieron, y el sintoma seria un permiso que sigue puesto.
     */
    MIEMBRO_DESAFILIADO,

    /**
     * Queda fijada la matriz de un sujeto sobre una opcion.
     *
     * <p><b>Se emite tambien cuando se retira todo</b>, con los siete privilegios en falso. Es la
     * unica forma de distinguir «se le nego expresamente» de «nunca lo tuvo», y en la excepcion de
     * usuario esa diferencia decide: una fila vacia sustituye a lo que el grupo da (ver {@link
     * PermisoEfectivo}). Un consumidor que solo recibiera los otorgamientos dejaria puesto para
     * siempre el privilegio que alguien acaba de quitar.
     */
    PERMISO_FIJADO
}
