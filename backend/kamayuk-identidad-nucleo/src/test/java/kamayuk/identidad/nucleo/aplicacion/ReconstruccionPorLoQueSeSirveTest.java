package kamayuk.identidad.nucleo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import kamayuk.identidad.autorizacion.Privilegio;
import kamayuk.identidad.dominio.Observacion;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.CatalogoUnido;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Grupo;
import kamayuk.identidad.nucleo.dominio.SistemasDelProducto;
import kamayuk.identidad.nucleo.dominio.Usuario;
import kamayuk.identidad.nucleo.infraestructura.CatalogoUnidoDelJar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La reconstruccion, pero <b>por el camino de la etapa 3</b>: pidiendo lo pendiente en paginas y
 * acusando cada una antes de pedir la siguiente.
 *
 * <h2>Que anade sobre {@code ReconstruccionDesdeElBuzonTest}</h2>
 *
 * <p>Aquella pregunta si los <b>siete tipos</b> bastan, y para eso lee el buzon entero de una vez
 * ({@code pendientesDesde(0, 1_000)}). Esta pregunta otra cosa: si <b>la forma en que la etapa 3
 * sirve el buzon</b> —{@code pendientesPara} y {@code acusar}, en lotes— deja al consumidor con lo
 * mismo. Son dos afirmaciones distintas y ninguna implica la otra: los siete tipos podrian bastar y
 * la entrega paginada seguir siendo incorrecta.
 *
 * <p>Y hay una tercera cosa que solo se ve aqui: el <b>orden</b>. En un buzon de hechos el orden es
 * contenido — una afiliacion que llegue antes que el alta de su grupo no se puede aplicar—, y esa
 * propiedad no la ejerce ninguna prueba que lea la tabla entera y la aplique en el orden en que
 * viene: la ejerce la que la lee por trozos, porque cada trozo se decide con un {@code ORDER BY} y
 * un {@code LIMIT}.
 *
 * <h2>Los lotes son de TRES a proposito</h2>
 *
 * <p>Ni uno —que no ejercitaria ninguna decision de orden dentro de la pagina— ni todos —que seria
 * la prueba de la etapa 2 otra vez—. Con tres y una veintena de eventos hay siete u ocho vueltas, y
 * el corte cae en mitad de las dependencias: entre el alta de un grupo y la afiliacion que lo
 * nombra, y entre el alta de un usuario y su permiso.
 *
 * <h2>Dos bases y no dos municipalidades</h2>
 *
 * <p>Lo mismo que en la etapa 2: con dos inquilinos de la misma base, un aplicador que se olvidara
 * de fijar el contexto escribiria en el del emisor y la comparacion cuadraria igual. Con dos bases
 * eso no puede pasar, y ademas es lo que hay en produccion (ADR-0032).
 */
@DisplayName("AC-1 — lo que la etapa 3 SIRVE reconstruye la copia, en paginas y con acuse")
class ReconstruccionPorLoQueSeSirveTest {

    /** Cuantos por vuelta. Ver el epigrafe de la cabecera. */
    private static final int POR_VUELTA = 3;

    /**
     * Un tope que no se puede alcanzar con lo que esta prueba emite: si se alcanza, es un bucle.
     */
    private static final int VUELTAS_MAXIMAS = 100;

    private static ArnesDeAdministracion emisor;
    private static ArnesDeAdministracion copia;
    private static long municipalidadDelEmisor;
    private static long municipalidadDeLaCopia;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        emisor = ArnesDeAdministracion.provisionar();
        copia = ArnesDeAdministracion.provisionar();
        municipalidadDelEmisor = emisor.crearMunicipalidad("270101", "Emisora");
        municipalidadDeLaCopia = copia.crearMunicipalidad("270101", "Copia");

        CatalogoUnido catalogo = CatalogoUnidoDelJar.leer();
        sembrarCatalogo(emisor, municipalidadDelEmisor, catalogo);
        sembrarCatalogo(copia, municipalidadDeLaCopia, catalogo);
    }

    @AfterAll
    static void cerrar() {
        if (emisor != null) {
            emisor.close();
        }
        if (copia != null) {
            copia.close();
        }
    }

    @Test
    @DisplayName("leyendo por lotes de tres y acusando, la copia queda como el emisor")
    void laCopiaQuedaComoElEmisor() throws SQLException {
        ejercerLasEscrituras();

        List<EventoDeIdentidad> enElOrdenEnQueSeSirvieron = consumirComoLoHariaLaEtapa4();

        assertThat(enElOrdenEnQueSeSirvieron)
                .as(
                        "sin eventos servidos, las comparaciones de abajo cuadran sobre dos bases vacias")
                .isNotEmpty();

        assertThat(secuencias(enElOrdenEnQueSeSirvieron))
                .as(
                        "[el orden ES contenido: una afiliacion que llegue antes que el alta de su"
                                + " grupo no se puede aplicar] lo servido tiene que llegar en el"
                                + " orden en que se emitio, vuelta a vuelta. Ordenar por el instante"
                                + " en vez de por la secuencia no lo garantiza: el instante lo pone"
                                + " el reloj de la aplicacion y dos hechos del mismo acto lo"
                                + " comparten, asi que el desempate lo decide el planificador")
                .isSorted();

        assertThat(estadoDe(copia, USUARIOS))
                .as("tabla `usuario`: la copia no quedo como el emisor")
                .isEqualTo(estadoDe(emisor, USUARIOS));
        assertThat(estadoDe(copia, GRUPOS))
                .as("tabla `grupo`: la copia no quedo como el emisor")
                .isEqualTo(estadoDe(emisor, GRUPOS));
        assertThat(estadoDe(copia, MIEMBROS))
                .as("tabla `miembro`: la copia no quedo como el emisor")
                .isEqualTo(estadoDe(emisor, MIEMBROS));
        assertThat(estadoDe(copia, PERMISOS))
                .as("tabla `permiso`: la copia no quedo como el emisor")
                .isEqualTo(estadoDe(emisor, PERMISOS));

        // Y el contraste, que va aqui y no en una prueba aparte porque depende de que la de arriba
        // ya haya corrido: sin el, «la copia queda igual» tambien seria cierto de un consumidor que
        // no acusara nunca y volviera a aplicarlo todo en cada vuelta —los cuerpos llevan la fila
        // entera, asi que reaplicar escribe lo mismo—: la copia cuadraria y el buzon no se vaciaria
        // jamas. Lo que separa las dos cosas es exactamente esto.
        BuzonDeIdentidad.Lote laVueltaSiguiente = servirUnLote(POR_VUELTA);
        assertThat(laVueltaSiguiente.eventos())
                .as("acusar es lo que cierra la cola: la vuelta siguiente ya no trae nada")
                .isEmpty();
        assertThat(laVueltaSiguiente.quedan())
                .as("y el retraso de ese consumidor queda en cero")
                .isZero();
    }

    // ------------------------------------------------------------------

    /**
     * Lo que hara el consumidor de la etapa 4: pedir, aplicar, acusar, repetir.
     *
     * <p>El acuse va <b>despues</b> de aplicar y de confirmar, que es el reparto entero de este
     * mecanismo: un acuse perdido reentrega y el receptor deduplica; un acuse anticipado pierde el
     * evento para siempre.
     */
    private static List<EventoDeIdentidad> consumirComoLoHariaLaEtapa4() {
        List<EventoDeIdentidad> servidos = new ArrayList<>();
        for (int vuelta = 0; vuelta < VUELTAS_MAXIMAS; vuelta++) {
            BuzonDeIdentidad.Lote lote = servirUnLote(POR_VUELTA);
            if (lote.eventos().isEmpty()) {
                return servidos;
            }
            servidos.addAll(lote.eventos());
            aplicarEnLaCopia(lote.eventos());
            acusar(lote.eventos());
        }
        throw new IllegalStateException(
                "Se agotaron las "
                        + VUELTAS_MAXIMAS
                        + " vueltas y el buzon sigue sirviendo eventos: el acuse no esta cerrando"
                        + " la cola, asi que esta prueba estaria dando vueltas sobre lo mismo");
    }

    private static BuzonDeIdentidad.Lote servirUnLote(int cuantos) {
        ArnesDeAdministracion.entrarComo(municipalidadDelEmisor, "publicador");
        try {
            return java.util.Objects.requireNonNull(
                    emisor.entrega().pendientesPara(Consumidor.CAJA, cuantos));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private static void acusar(List<EventoDeIdentidad> eventos) {
        List<UUID> ids = new ArrayList<>();
        for (EventoDeIdentidad evento : eventos) {
            ids.add(evento.eventoId());
        }
        ArnesDeAdministracion.entrarComo(municipalidadDelEmisor, "publicador");
        try {
            emisor.entrega().acusar(Consumidor.CAJA, List.copyOf(ids));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private static void aplicarEnLaCopia(List<EventoDeIdentidad> eventos) {
        ArnesDeAdministracion.entrarComo(municipalidadDeLaCopia, "consumidor");
        try {
            copia.transaccion()
                    .execute(
                            estado -> {
                                new AplicadorDeReferencia(copia.jdbc()).aplicar(eventos);
                                return null;
                            });
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private static List<Long> secuencias(List<EventoDeIdentidad> eventos) {
        List<Long> secuencias = new ArrayList<>();
        for (EventoDeIdentidad evento : eventos) {
            secuencias.add(evento.secuencia());
        }
        return secuencias;
    }

    /**
     * Un juego de escrituras cuyo orden importa.
     *
     * <p>No es el de {@code ReconstruccionDesdeElBuzonTest} —alli lo que se busca es ejercer los
     * siete tipos— sino uno pensado para que las <b>dependencias crucen el corte de la pagina</b>:
     * dos grupos y dos usuarios antes de tres afiliaciones y tres matrices de permisos, con una
     * desafiliacion en medio. Con lotes de tres, ninguna afiliacion cae en la misma pagina que el
     * alta del grupo que nombra.
     */
    private void ejercerLasEscrituras() {
        ArnesDeAdministracion.entrarComo(municipalidadDelEmisor, "admin.emisor");
        try {
            AdministrarSeguridad administrar = emisor.administrar();
            AdministrarPermisos permisos = emisor.permisos();

            Grupo mesa =
                    administrar.registrarGrupo(
                            Grupo.nuevo("Mesa de Partes", "Recibe y deriva expedientes"),
                            Observacion.de("Alta del grupo de Mesa de Partes"));
            Grupo caja =
                    administrar.registrarGrupo(
                            Grupo.nuevo("Caja", null),
                            Observacion.de("Alta del grupo de la ventanilla de caja"));
            Usuario jperez =
                    administrar.registrarUsuario(
                            Usuario.nuevo("jperez", "Juan Perez", "jperez@muni.gob.pe"),
                            Observacion.de("Alta de Juan Perez segun memorando 2026-11"));
            Usuario mlopez =
                    administrar.registrarUsuario(
                            Usuario.nuevo("mlopez", "Maria Lopez", null),
                            Observacion.de("Alta de Maria Lopez sin correo declarado"));

            administrar.afiliar(
                    exigirId(mesa),
                    exigirId(jperez),
                    Observacion.de("Se incorpora a Mesa de Partes"));
            administrar.afiliar(
                    exigirId(caja), exigirId(jperez), Observacion.de("Refuerza la ventanilla"));
            administrar.afiliar(
                    exigirId(mesa),
                    exigirId(mlopez),
                    Observacion.de("Se incorpora a Mesa de Partes"));
            administrar.desafiliar(
                    exigirId(caja),
                    exigirId(jperez),
                    Observacion.de("Sale de caja por rotacion de areas"));

            // `permisos` de `identidad` primero: mientras no este otorgado no hay ningun
            // administrador y la guarda del ultimo administrador rechazaria lo siguiente con un 409
            // que hablaria de lo contrario de lo que pasa. Es el mismo orden que la implantacion.
            permisos.fijarParaGrupo(
                    exigirId(mesa),
                    SistemasDelProducto.IDENTIDAD,
                    "permisos",
                    EnumSet.allOf(Privilegio.class),
                    Observacion.de("Mesa de Partes administra los permisos de la municipalidad"));
            permisos.fijarParaGrupo(
                    exigirId(caja),
                    "caja",
                    "caja_tributaria",
                    EnumSet.of(Privilegio.LECTURA),
                    Observacion.de("La ventanilla solo consulta la caja tributaria"));
            permisos.fijarParaUsuario(
                    exigirId(mlopez),
                    "rentas",
                    "contribuyentes",
                    EnumSet.noneOf(Privilegio.class),
                    Observacion.de(
                            "Se le niega expresamente el padron mientras dure la investigacion"));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    private static long exigirId(Grupo grupo) {
        return java.util.Objects.requireNonNull(grupo.id());
    }

    private static long exigirId(Usuario usuario) {
        return java.util.Objects.requireNonNull(usuario.id());
    }

    private static void sembrarCatalogo(
            ArnesDeAdministracion arnes, long municipalidad, CatalogoUnido catalogo) {
        ArnesDeAdministracion.entrarComo(municipalidad, "despliegue");
        try {
            arnes.sembrador()
                    .sembrar(catalogo, Observacion.de("Siembra del catalogo unido de la prueba"));
        } finally {
            ArnesDeAdministracion.salir();
        }
    }

    // ------------------------------------------------------------------ el estado comparable
    //
    // Las mismas cuatro consultas que `ReconstruccionDesdeElBuzonTest`, y por los mismos motivos:
    // sin identificadores internos —cada base tiene sus secuencias (ADR-0032)— y leidas con la
    // conexion de superusuario, para que una fuga no se vea como una ausencia.

    private static final String USUARIOS =
            "SELECT cuenta || '|' || nombre || '|' || coalesce(correo, '') || '|' || habilitado"
                    + " || '|' || coalesce(vigencia_desde::text, '') || '|'"
                    + " || coalesce(vigencia_hasta::text, '')"
                    + " FROM usuario ORDER BY cuenta";

    private static final String GRUPOS =
            "SELECT nombre || '|' || coalesce(descripcion, '') || '|' || habilitado"
                    + " || '|' || coalesce(vigencia_desde::text, '') || '|'"
                    + " || coalesce(vigencia_hasta::text, '')"
                    + " FROM grupo ORDER BY nombre";

    private static final String MIEMBROS =
            "SELECT g.nombre || '|' || u.cuenta || '|' || m.activo || '|' || m.usuario_alta"
                    + " || '|' || coalesce(m.usuario_baja, '')"
                    + " || '|' || (m.fecha_baja IS NOT NULL)"
                    + " FROM miembro m JOIN grupo g ON g.id = m.grupo_id"
                    + " JOIN usuario u ON u.id = m.usuario_id ORDER BY 1";

    private static final String PERMISOS =
            "SELECT coalesce(g.nombre, u.cuenta) || '|' || (p.grupo_id IS NOT NULL)"
                    + " || '|' || a.sistema || '|' || a.codigo || '|' || p.usuario_registro"
                    + " || '|' || p.ejecucion || p.lectura || p.registro || p.modificacion"
                    + " || p.eliminacion || p.impresion || p.especial"
                    + " FROM permiso p JOIN acceso a ON a.id = p.acceso_id"
                    + " LEFT JOIN grupo g ON g.id = p.grupo_id"
                    + " LEFT JOIN usuario u ON u.id = p.usuario_id ORDER BY 1";

    private static List<String> estadoDe(ArnesDeAdministracion arnes, String sql)
            throws SQLException {
        return arnes.filas(sql);
    }
}
