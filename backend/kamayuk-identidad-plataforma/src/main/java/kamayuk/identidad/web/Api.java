package kamayuk.identidad.web;

/** Constantes del contrato HTTP. */
public final class Api {

    /**
     * Raiz de todas las operaciones.
     *
     * <p><b>En la etapa 1 no la usa ningun controlador</b>, porque no hay ninguno, y aun asi no es
     * una constante muerta: {@code ConfiguracionDeIdentidad.raizDeLaApi()} la lee y de ahi sale lo
     * que las barreras comparan —entre otras cosas, que ninguna ruta del portal del ciudadano se
     * sirva fuera de su prefijo—, y {@code ArranqueDeLaAplicacionTest} pide con ella para comprobar
     * que la cadena de seguridad esta montada: sin token, 401 en {@code problem+json}.
     *
     * <p>Es tambien el {@code PathPrefix} con que el ingreso enruta este sistema, asi que el valor
     * NO es libre: cambiarlo aqui sin cambiarlo en el descriptor deja la API enrutada a un sitio
     * donde no contesta nadie.
     *
     * <p>Lo que en los otros cuatro compara estas rutas con el {@code servers.url} de su {@code
     * docs/50-api/openapi/<sistema>-v1.yaml} no existe aqui todavia: sin operaciones no hay
     * contrato que publicar. Esa prueba tiene que llegar con la primera.
     */
    public static final String RAIZ = "/identidad/api/v1";

    private Api() {}
}
