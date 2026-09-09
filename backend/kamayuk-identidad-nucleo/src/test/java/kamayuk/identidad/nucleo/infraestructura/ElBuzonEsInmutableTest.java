package kamayuk.identidad.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.HechoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.TipoDeEventoDeIdentidad;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que {@code V2} decide, ejercido contra el motor: el buzon es <b>inmutable</b> y su {@code
 * CHECK} cruzado esta escrito para que un octavo tipo <b>no entre</b>.
 *
 * <h2>Por que el CHECK se prueba anadiendo un tipo, y no con los siete de hoy</h2>
 *
 * <p>Los siete exigen lo mismo —{@code sujeto_id IS NOT NULL}—, asi que sobre ellos la forma
 * positiva con {@code ELSE false} y un {@code NOT NULL} a secas son indistinguibles: las dos
 * rechazan lo mismo. La diferencia esta en el <b>octavo</b>, y por eso esta prueba lo crea: anade
 * un tipo al {@code tipo_ck} —que es exactamente lo que hara quien publique un hecho nuevo— y
 * comprueba que sin decidir su forma <b>no se puede insertar</b>.
 *
 * <p>Es la leccion de {@code V10} de {@code catastro}: alli los dos CHECK cruzados se escribieron
 * en negativo, con tres tipos eran correctos y con seis resultaron FALSOS —obligaban a un tipo
 * nuevo a nombrar un predio que no era suyo—. Con {@code ELSE false} el tipo nuevo falla
 * ruidosamente en su primer {@code INSERT} en vez de colarse con la forma del vecino.
 */
@DisplayName("V2 — el buzon es inmutable, y el octavo tipo no entra por descuido")
class ElBuzonEsInmutableTest {

    private static ArnesDeAdministracion arnes;
    private static long municipalidad;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
        municipalidad = arnes.crearMunicipalidad("300101", "Buzon");
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    @BeforeEach
    void fijarContexto() {
        ArnesDeAdministracion.entrarComo(municipalidad, "publicador");
    }

    @AfterEach
    void limpiarContexto() {
        ArnesDeAdministracion.salir();
    }

    /**
     * Un tipo inventado no entra, y <b>lo para el CHECK cruzado antes que la lista de tipos</b>.
     *
     * <p>Medido, y no es lo que se esperaba: con el sujeto puesto y un tipo que no esta en {@code
     * tipo_ck}, PostgreSQL nombra {@code identidad_evento_sujeto_ck}. Es el {@code ELSE false}
     * haciendo su trabajo —el {@code CASE} no reconoce el tipo, cae en la rama de descarte y
     * rechaza—, y el orden en que el motor evalua dos {@code CHECK} de la misma fila no esta
     * garantizado. Por eso la afirmacion es que <b>alguno de los dos</b> lo para, y no cual:
     * anclarla a uno la volveria dependiente de un detalle del planificador.
     */
    @Test
    @DisplayName("un tipo que el CHECK no conoce no se puede insertar")
    void unTipoDesconocidoNoEntra() {
        assertThatThrownBy(() -> insertarCrudo("TIPO_INVENTADO", 1L))
                .hasMessageContaining("violates check constraint")
                .hasMessageContaining("identidad_evento");
    }

    @Test
    @DisplayName("y un OCTAVO tipo admitido sin decidir su sujeto tampoco (ELSE false)")
    void unOctavoTipoSinDecidirSuSujetoNoEntra() throws SQLException {
        admitirUnTipoMas("HECHO_SIN_FORMA");
        try {
            // `catchThrowable` y no `assertThatThrownBy`: cuando NO se lanza nada, AssertJ revienta
            // antes de aplicar el `.as(...)` y el rojo se queda en «Expecting code to raise a
            // throwable», que no dice que se rompio ni lo que cuesta. Medido con la forma negativa
            // puesta. Es la leccion de `rentas`#40.
            Throwable loQueSalio = catchThrowable(() -> insertarCrudo("HECHO_SIN_FORMA", null));

            assertThat(loQueSalio)
                    .as(
                            "con el CHECK escrito en NEGATIVO esto entraria: la clausula habla de los"
                                    + " siete que conoce y calla sobre el octavo, asi que un hecho sin"
                                    + " sujeto se colaria con la forma del vecino y el consumidor"
                                    + " recibiria un evento del que no puede decir de quien habla")
                    .isNotNull()
                    .hasMessageContaining("identidad_evento_sujeto_ck");
        } finally {
            devolverElTipoCk();
        }
    }

    @Test
    @DisplayName("un cuerpo que no es un objeto tampoco")
    void unCuerpoQueNoEsObjetoNoEntra() {
        assertThatThrownBy(
                        () ->
                                arnes.transaccion()
                                        .execute(
                                                estado ->
                                                        arnes.jdbc()
                                                                .sql(
                                                                        "INSERT INTO"
                                                                                + " identidad_evento"
                                                                                + " (municipalidad_id,"
                                                                                + " evento_id, tipo,"
                                                                                + " sujeto_id, cuerpo,"
                                                                                + " huella, creado_en)"
                                                                                + " VALUES"
                                                                                + " (current_setting('app.municipalidad_id')::bigint,"
                                                                                + " gen_random_uuid(),"
                                                                                + " 'USUARIO_MODIFICADO',"
                                                                                + " 1, 'null'::jsonb,"
                                                                                + " repeat('a', 64),"
                                                                                + " now())")
                                                                .update()))
                .as(
                        "«null» es un jsonb valido, y un evento cuyo contenido no se puede aplicar a"
                                + " ninguna fila no es un evento")
                .hasMessageContaining("identidad_evento_cuerpo_ck");
    }

    @Test
    @DisplayName("kamayuk_app no puede actualizar ni borrar un evento ya publicado")
    void laAplicacionNoPuedeReescribirUnEvento() {
        long secuencia =
                arnes.transaccion()
                        .execute(
                                estado ->
                                        arnes.buzon()
                                                .emitir(
                                                        HechoDeIdentidad.deMiembro(
                                                                new kamayuk.identidad.nucleo.dominio
                                                                        .Miembro(1L, 2L, true),
                                                                "Grupo",
                                                                "cuenta",
                                                                "quien"))
                                                .secuencia());

        assertThatThrownBy(
                        () ->
                                arnes.transaccion()
                                        .execute(
                                                estado ->
                                                        arnes.jdbc()
                                                                .sql(
                                                                        "UPDATE identidad_evento SET"
                                                                                + " huella = repeat('b',"
                                                                                + " 64) WHERE id = :id")
                                                                .param("id", secuencia)
                                                                .update()))
                .as(
                        "el buzon no lleva `estado` —tiene CUATRO consumidores— asi que no hay una"
                                + " sola columna que cambie despues de escribirse: un UPDATE aqui"
                                + " solo puede ser reescribir un hecho ya publicado")
                // `hasStackTraceContaining` y no `hasMessageContaining`: el 42501 de PostgreSQL cae
                // en la clase 42 y Spring lo traduce a `BadSqlGrammarException`, cuyo mensaje solo
                // trae el SQL. El «permission denied for table» esta en la causa, y es lo unico que
                // distingue «no tiene el privilegio» de «la sentencia esta mal escrita».
                .hasStackTraceContaining("permission denied");

        assertThatThrownBy(
                        () ->
                                arnes.transaccion()
                                        .execute(
                                                estado ->
                                                        arnes.jdbc()
                                                                .sql(
                                                                        "DELETE FROM"
                                                                                + " identidad_evento"
                                                                                + " WHERE id = :id")
                                                                .param("id", secuencia)
                                                                .update()))
                .as("y un evento borrado es un hecho que los otros cuatro no recibiran nunca")
                .hasStackTraceContaining("permission denied");
    }

    @Test
    @DisplayName("y el mismo evento_id dos veces se rechaza: es la idempotencia del receptor")
    void elMismoEventoDosVecesSeRechaza() {
        UUID repetido = UUID.randomUUID();
        arnes.transaccion().execute(estado -> insertarCrudoConId(repetido, "GRUPO_MODIFICADO", 1L));

        assertThatThrownBy(
                        () ->
                                arnes.transaccion()
                                        .execute(
                                                estado ->
                                                        insertarCrudoConId(
                                                                repetido, "GRUPO_MODIFICADO", 1L)))
                .hasMessageContaining("identidad_evento_uq");
    }

    // ------------------------------------------------------------------

    private void insertarCrudo(String tipo, Long sujeto) {
        arnes.transaccion().execute(estado -> insertarCrudoConId(UUID.randomUUID(), tipo, sujeto));
    }

    private int insertarCrudoConId(UUID eventoId, String tipo, Long sujeto) {
        return arnes.jdbc()
                .sql(
                        "INSERT INTO identidad_evento (municipalidad_id, evento_id, tipo, sujeto_id,"
                                + " cuerpo, huella, creado_en) VALUES"
                                + " (current_setting('app.municipalidad_id')::bigint, :evento,"
                                + " :tipo, :sujeto, '{}'::jsonb, repeat('a', 64), now())")
                .param("evento", eventoId)
                .param("tipo", tipo)
                .param("sujeto", sujeto)
                .update();
    }

    /**
     * Admite un tipo mas en el {@code tipo_ck}, como haria la migracion que publique un hecho
     * nuevo, y <b>sin tocar el CHECK cruzado</b>: es exactamente el descuido que se mide.
     */
    private static void admitirUnTipoMas(String tipo) throws SQLException {
        ejecutarComoDueno(
                "ALTER TABLE identidad_evento DROP CONSTRAINT identidad_evento_tipo_ck",
                "ALTER TABLE identidad_evento ADD CONSTRAINT identidad_evento_tipo_ck"
                        + " CHECK (tipo IN ('USUARIO_DADO_DE_ALTA', 'USUARIO_MODIFICADO',"
                        + " 'GRUPO_DADO_DE_ALTA', 'GRUPO_MODIFICADO', 'MIEMBRO_AFILIADO',"
                        + " 'MIEMBRO_DESAFILIADO', 'PERMISO_FIJADO', '"
                        + tipo
                        + "'))");
    }

    /**
     * Y lo deshace: las otras pruebas de esta clase tienen que ver el esquema de {@code V2}.
     *
     * <p>Borra antes cualquier fila del tipo inventado, y no es limpieza de cortesia: si el CHECK
     * cruzado <b>dejo pasar</b> la fila —que es exactamente lo que se esta midiendo— devolver el
     * {@code tipo_ck} falla con «is violated by some row», la excepcion sube desde el {@code
     * finally} y <b>tapa la afirmacion</b>. Medido con la forma negativa puesta: el unico rojo era
     * el del {@code ALTER TABLE}, y el mensaje que explica el defecto no llegaba a imprimirse.
     */
    private static void devolverElTipoCk() throws SQLException {
        ejecutarComoDueno(
                "DELETE FROM identidad_evento WHERE tipo NOT IN ('USUARIO_DADO_DE_ALTA',"
                        + " 'USUARIO_MODIFICADO', 'GRUPO_DADO_DE_ALTA', 'GRUPO_MODIFICADO',"
                        + " 'MIEMBRO_AFILIADO', 'MIEMBRO_DESAFILIADO', 'PERMISO_FIJADO')",
                "ALTER TABLE identidad_evento DROP CONSTRAINT identidad_evento_tipo_ck",
                "ALTER TABLE identidad_evento ADD CONSTRAINT identidad_evento_tipo_ck"
                        + " CHECK (tipo IN ('"
                        + String.join(
                                "', '",
                                java.util.Arrays.stream(TipoDeEventoDeIdentidad.values())
                                        .map(Enum::name)
                                        .toList())
                        + "'))");
    }

    private static void ejecutarComoDueno(String... sentencias) throws SQLException {
        try (Connection admin = arnes.base().conexionAdmin();
                Statement sentencia = admin.createStatement()) {
            for (String sql : sentencias) {
                sentencia.execute(sql);
            }
        }
    }
}
