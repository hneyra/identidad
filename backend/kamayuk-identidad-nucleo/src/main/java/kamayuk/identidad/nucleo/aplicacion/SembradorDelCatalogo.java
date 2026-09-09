package kamayuk.identidad.nucleo.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import kamayuk.identidad.auditoria.Auditoria;
import kamayuk.identidad.auditoria.Operacion;
import kamayuk.identidad.auditoria.RegistroDeAuditoria;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.persistencia.RepositorioJdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra <b>el catalogo</b>: {@code modulo_sistema} y {@code acceso} de los cinco sistemas.
 *
 * <h2>Que dejo de hacer en la etapa 2, y por que</h2>
 *
 * <p>Se llamaba {@code SembradorDeLaCopiaLocal} y escribia ademas el grupo de administracion, el
 * primer administrador, su afiliacion y sus permisos, <b>con SQL directo</b>. Eso era correcto
 * mientras las once escrituras de administracion vivian en {@code rentas} y aqui no habia caso de
 * uso al que llamar; desde que estan aqui, no lo es: un administrador escrito por este camino <b>no
 * emite ningun evento</b>, asi que los otros cuatro sistemas no reciben la unica cuenta que puede
 * entrar el primer dia y el arranque en frio de la etapa 5 no funciona. Peor: seria la unica
 * escritura del sistema que no deja auditoria con su observacion por el mismo camino que las demas
 * (regla 10).
 *
 * <p>Asi que se quedo con lo que <b>no</b> tiene caso de uso —el catalogo se siembra, no se
 * administra: no hay pantalla de alta de opciones y no debe haberla (RF-122: las opciones las trae
 * el catalogo de cada sistema)— y las cuatro escrituras de personas las hace {@link
 * ImplantarMunicipalidad} llamando a {@code AdministrarSeguridad} y {@code AdministrarPermisos}.
 *
 * <h2>La columna {@code sistema}, y lo que pasa sin ella</h2>
 *
 * <p>Escribe las dos tablas con el sistema de cada opcion, porque el esquema las llavea por {@code
 * (municipalidad_id, sistema, codigo)}: aqui conviven los catalogos de los cinco y dos sistemas
 * pueden nombrar igual dos opciones distintas —{@code permisos} lo hacen {@code identidad} y {@code
 * rentas}, y {@code SEGURIDAD} es un modulo de tres—. Sin la columna, sembrar el catalogo del
 * segundo chocaria con el UNIQUE del primero y el {@code ON CONFLICT ... DO NOTHING} de abajo lo
 * dejaria pasar <b>en silencio</b>: la opcion no se crearia, nadie podria darle permiso, y el
 * sintoma es una pantalla a la que no se le puede dar acceso.
 *
 * <p><b>Idempotente y solo agrega.</b> Se puede ejecutar en cada despliegue: lo que ya existe se
 * queda como esta —con los permisos que alguien haya configurado despues— y lo que falta se crea.
 * Lo que <b>no</b> hace es borrar: los permisos que cuelgan de un acceso retirado son constancia de
 * quien pudo hacer que, y eso no se borra (RNF-051, regla 4).
 */
@Service
public class SembradorDelCatalogo extends RepositorioJdbc {

    private final Auditoria auditoria;
    private final Clock reloj;

    public SembradorDelCatalogo(JdbcClient jdbc, Auditoria auditoria, Clock reloj) {
        super(jdbc);
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * Deja el catalogo de los cinco sembrado para la municipalidad del contexto.
     *
     * <p><b>Una sola transaccion para todo</b>, y por dos motivos distintos. El primero es de
     * negocio: una municipalidad con la mitad del catalogo sembrado es peor que ninguna, porque
     * parece lista y hay pantallas a las que nadie puede dar permiso. El segundo es tecnico y se
     * paga en cuanto se olvida: las dos tablas llevan RLS con {@code FORCE} y sus politicas leen
     * {@code app.municipalidad_id}, que el gestor de transacciones fija con {@code SET LOCAL} <b>al
     * abrir la transaccion</b>; leerlas fuera de una no devuelve vacio, revienta (DAT-01 §0, #486).
     *
     * @return cuantos accesos se crearon; 0 en un despliegue donde no cambio ningun catalogo
     */
    @Transactional
    public int sembrar(CatalogoUnido catalogo, Observacion porQue) {
        int creados = 0;
        for (CatalogoUnido.Opcion opcion : catalogo.opciones()) {
            creados += crearAccesoSiFalta(opcion, moduloId(opcion));
        }

        // Solo si se creo algo. Un despliegue que no cambia el catalogo no tiene nada que
        // asentar, y una fila de auditoria por despliegue convierte la bitacora en un registro de
        // reinicios — que es lo contrario de lo que ADR-0008 quiere que se pueda leer ahi.
        if (creados == 0) {
            return 0;
        }
        auditoria.registrar(
                RegistroDeAuditoria.enLaFechaDe(
                                LocalDate.now(reloj), "acceso", "catalogo", Operacion.ALTA, porQue)
                        .con(
                                null,
                                "{\"accesosCreados\":"
                                        + creados
                                        + ",\"opcionesDeLosCinco\":"
                                        + catalogo.opciones().size()
                                        + "}"));
        return creados;
    }

    /** Crea el modulo si falta y devuelve su identificador. */
    private long moduloId(CatalogoUnido.Opcion opcion) {
        jdbc().sql(
                        "INSERT INTO modulo_sistema (municipalidad_id, sistema, codigo, nombre)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :sistema, :codigo, :nombre)"
                                + " ON CONFLICT (municipalidad_id, sistema, codigo) DO NOTHING")
                .param("sistema", opcion.sistema())
                .param("codigo", opcion.moduloCodigo())
                .param("nombre", opcion.moduloNombre())
                .update();

        // La lectura tambien lleva el sistema: sin el, `single()` revienta —`SEGURIDAD` es el
        // codigo de un modulo de `identidad`, otro de `rentas` y otro de `normativa`, y `CATASTRO`
        // y `TESORERIA` y `CONSULTAS` estan repetidos igual—. No es hipotetico desde esta etapa:
        // es el estado normal de esta base.
        return jdbc().sql(
                        "SELECT id FROM modulo_sistema"
                                + " WHERE sistema = :sistema AND codigo = :codigo")
                .param("sistema", opcion.sistema())
                .param("codigo", opcion.moduloCodigo())
                .query(Long.class)
                .single();
    }

    private int crearAccesoSiFalta(CatalogoUnido.Opcion opcion, long moduloId) {
        return jdbc().sql(
                        "INSERT INTO acceso"
                                + " (municipalidad_id, modulo_id, sistema, tipo, codigo, nombre)"
                                + " VALUES ("
                                + MUNICIPALIDAD_ACTUAL
                                + ", :modulo, :sistema, 'OPCION_MENU', :codigo, :nombre)"
                                + " ON CONFLICT (municipalidad_id, sistema, codigo) DO NOTHING")
                .param("modulo", moduloId)
                .param("sistema", opcion.sistema())
                .param("codigo", opcion.codigo())
                .param("nombre", opcion.nombre())
                .update();
    }
}
