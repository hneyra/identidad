package kamayuk.identidad.nucleo.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kamayuk.identidad.nucleo.ArnesDeAdministracion;
import kamayuk.identidad.nucleo.dominio.BuzonDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Consumidor;
import kamayuk.identidad.nucleo.dominio.EventoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.HechoDeIdentidad;
import kamayuk.identidad.nucleo.dominio.Miembro;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-2: <b>un acuse no es de todos</b> ({@code V3}), contra PostgreSQL de verdad.
 *
 * <h2>Lo que esta clase existe para impedir</h2>
 *
 * <p>Que el acuse de un consumidor retire el evento para los otros tres. Es el defecto que {@code
 * V2} anticipo al negarse a poner una columna {@code estado} en el buzon, y es <b>silencioso</b>:
 * no hay error, no hay excepcion, no hay fila de mas ni de menos — simplemente {@code caja} no
 * vuelve a ver un evento que {@code rentas} ya acuso, y el sintoma aparece semanas despues, en
 * {@code caja}, como un permiso que aqui esta y alli no.
 *
 * <p>Por eso todas las afirmaciones de aqui son de la forma «para {@code X} si y para {@code Y}
 * no», y no «hay tantas filas»: la cuenta total no distingue una tabla bien llavada de una mal
 * llavada.
 *
 * <p>El pool se conecta como {@code kamayuk_app}, asi que la politica RLS y los privilegios de
 * {@code V3} se ejercen de verdad: con el dueno de las tablas, {@code FORCE ROW LEVEL SECURITY} es
 * lo unico que lo impediria y la mitad de lo que esta clase mide pasaria en verde sin ejercerse
 * (DAT-01 §0, hallazgo 1).
 */
@DisplayName("AC-2 — un acuse es de un consumidor, no de todos")
class AcusesPorConsumidorTest {

    private static ArnesDeAdministracion arnes;
    private static int siguienteUbigeo = 310100;

    private static long municipalidad;
    private static long otraMunicipalidad;

    @BeforeAll
    static void provisionar() throws SQLException, IOException {
        arnes = ArnesDeAdministracion.provisionar();
    }

    @AfterAll
    static void cerrar() {
        if (arnes != null) {
            arnes.close();
        }
    }

    /**
     * <b>Una municipalidad nueva por prueba</b>, y no una compartida.
     *
     * <p>Casi todo lo que esta clase afirma es de la forma «la cola de este consumidor es
     * EXACTAMENTE estos eventos», y eso solo se puede afirmar sobre un buzon cuyo contenido entero
     * sea el de la prueba. Con una municipalidad compartida, lo que emitiera cada prueba se sumaria
     * a la siguiente y las aserciones pasarian a depender del orden en que JUnit las ejecute — o,
     * peor, habria que aflojarlas a «contiene», que es justo lo que no distingue una cola bien
     * llavada de una mal llavada.
     *
     * <p>Y es gratis: {@code municipalidad} es la unica tabla del esquema que no es de tenant, asi
     * que una fila mas no arrastra nada.
     */
    @BeforeEach
    void unaMunicipalidadNueva() throws SQLException {
        municipalidad = arnes.crearMunicipalidad(String.valueOf(++siguienteUbigeo), "Acuses");
        otraMunicipalidad = arnes.crearMunicipalidad(String.valueOf(++siguienteUbigeo), "La otra");
        ArnesDeAdministracion.entrarComo(municipalidad, "publicador");
    }

    @AfterEach
    void limpiarContexto() {
        ArnesDeAdministracion.salir();
    }

    /**
     * El AC-2 entero, en una secuencia.
     *
     * <p>Va como una sola prueba y no como cuatro porque lo que se mide es una <b>secuencia</b>: el
     * estado de cada paso es lo que hace significativo al siguiente. Partirla obligaria a repetir
     * la siembra y a que cada trozo volviera a llegar al estado anterior, y entonces lo que se
     * afirma pasaria a depender del orden en que JUnit los ejecute.
     */
    @Test
    @DisplayName("rentas acusa uno y caja lo sigue viendo; caja lo acusa y catastro tambien")
    void unAcuseNoEsDeTodos() {
        List<UUID> emitidos = emitir(3);
        UUID primero = emitidos.get(0);

        assertThat(idsPendientesPara(Consumidor.RENTAS))
                .as("los cuatro empiezan con la cola entera: nadie ha acusado nada")
                .containsExactlyElementsOf(emitidos);
        assertThat(idsPendientesPara(Consumidor.CAJA)).containsExactlyElementsOf(emitidos);

        assertThat(acusar(Consumidor.RENTAS, List.of(primero)))
                .as("un acuse nuevo escribe una fila")
                .isEqualTo(1);

        assertThat(idsPendientesPara(Consumidor.RENTAS))
                .as("para quien acuso, el evento sale de su cola")
                .doesNotContain(primero)
                .containsExactly(emitidos.get(1), emitidos.get(2));

        assertThat(idsPendientesPara(Consumidor.CAJA))
                .as(
                        "[el defecto que V3 existe para impedir] «caja» tiene que seguir viendo el"
                                + " evento que acuso «rentas». Con el acuse en una columna del propio"
                                + " buzon —o con la clave primaria sin el consumidor dentro— el"
                                + " primero que acusa lo retira para los otros tres, sin error y sin"
                                + " que nada lo diga: el sintoma llega semanas despues, en el otro"
                                + " sistema, como un permiso que aqui esta y alli no")
                .containsExactlyElementsOf(emitidos);

        assertThat(acusar(Consumidor.CAJA, List.of(primero)))
                .as(
                        "[la otra cara del mismo defecto, y la que sale primero al medirlo] con la"
                                + " clave primaria sin «consumidor» dentro, la fila que ya hay es la"
                                + " de «rentas» y el `ON CONFLICT DO NOTHING` se traga este acuse EN"
                                + " SILENCIO: «caja» cree que acuso, su cola no se mueve, y cada"
                                + " vuelta le vuelve a servir los mismos eventos para siempre")
                .isEqualTo(1);

        assertThat(idsPendientesPara(Consumidor.CAJA)).doesNotContain(primero);
        assertThat(idsPendientesPara(Consumidor.CATASTRO))
                .as("y el tercero sigue sin enterarse de los dos acuses anteriores")
                .containsExactlyElementsOf(emitidos);
        assertThat(idsPendientesPara(Consumidor.NORMATIVA)).containsExactlyElementsOf(emitidos);
    }

    @Test
    @DisplayName("y el retraso que se publica es el de ese consumidor, no el del buzon")
    void elRetrasoEsDeCadaUno() {
        List<UUID> emitidos = emitir(4);
        acusar(Consumidor.NORMATIVA, emitidos.subList(0, 3));

        BuzonDeIdentidad.Lote deNormativa = pendientesPara(Consumidor.NORMATIVA, 200);
        BuzonDeIdentidad.Lote deRentas = pendientesPara(Consumidor.RENTAS, 200);

        assertThat(deNormativa.quedan())
                .as("«quedan» cuenta lo que le falta a QUIEN PREGUNTA")
                .isEqualTo(deNormativa.eventos().size());
        assertThat(deRentas.quedan())
                .as(
                        "y no es el mismo numero para los cuatro: si lo fuera, seria el tamano del"
                                + " buzon y no el retraso de nadie")
                .isGreaterThan(deNormativa.quedan());
    }

    /**
     * El limite recorta la pagina y <b>no</b> el retraso.
     *
     * <p>Son dos cosas distintas y la unica forma de verlo es pedir menos de los que hay: con
     * {@code count(*) OVER ()} —que es la manera comoda de sacar las dos en una consulta— el {@code
     * quedan} de una pagina recortada seria el de la pagina, o sea el mismo numero que ya se puede
     * contar mirando la lista. El consumidor lo usa para saber si tiene que dar otra vuelta.
     */
    @Test
    @DisplayName("el limite recorta la pagina y no el retraso")
    void elLimiteRecortaLaPaginaYNoElRetraso() {
        emitir(5);
        BuzonDeIdentidad.Lote lote = pendientesPara(Consumidor.CATASTRO, 2);

        assertThat(lote.eventos()).hasSize(2);
        assertThat(lote.quedan()).isGreaterThanOrEqualTo(5);
    }

    /**
     * Acusar dos veces no es un error: es lo que pasa cuando se pierde un acuse.
     *
     * <p>Lo que se mide es que la segunda vez escribe <b>cero</b> filas y no que «no lanza»: sin el
     * {@code ON CONFLICT DO NOTHING}, el segundo acuse revienta con la clave duplicada, y un
     * consumidor que confirmo su transaccion y no pudo acusar la volveria a aplicar en cada vuelta
     * para siempre.
     */
    @Test
    @DisplayName("acusar dos veces el mismo evento escribe cero la segunda, y no falla")
    void acusarDosVecesNoEsUnError() {
        List<UUID> emitidos = emitir(1);

        assertThat(acusar(Consumidor.RENTAS, emitidos)).isEqualTo(1);
        assertThat(acusar(Consumidor.RENTAS, emitidos))
                .as(
                        "[un acuse perdido despues de que el receptor confirmara es lo normal: la"
                                + " entrega es AL MENOS UNA VEZ] la segunda vez no escribe nada y"
                                + " tampoco falla")
                .isZero();
        assertThat(idsPendientesPara(Consumidor.RENTAS)).doesNotContainAnyElementsOf(emitidos);
    }

    /** Acusar lo que no se sirvio no es un descuido tolerable: lo acusado no se vuelve a servir. */
    @Test
    @DisplayName("acusar un evento que este buzon no sirvio falla nombrandolo")
    void acusarLoQueNoSeSirvioFallaNombrandolo() {
        UUID inventado = UUID.fromString("11111111-2222-4333-8444-555555555555");
        List<UUID> emitidos = new ArrayList<>(emitir(1));
        emitidos.add(inventado);

        Throwable loQueSalio = catchThrowable(() -> acusar(Consumidor.CAJA, emitidos));

        assertThat(loQueSalio)
                .as(
                        "[la foranea de V3 lo rechazaria igual, pero con un 23503 que nombra la"
                                + " restriccion y no el evento: quien manda doscientos no podria"
                                + " saber cual estaba mal]")
                .isInstanceOf(BuzonDeIdentidad.EventoQueNoConsta.class)
                .hasMessageContaining(inventado.toString());

        assertThat(idsPendientesPara(Consumidor.CAJA))
                .as("y no escribe nada: o se acusa todo lo que se mando, o no se acusa nada")
                .containsExactlyElementsOf(emitidos.subList(0, 1));
    }

    /**
     * Un evento de otra municipalidad, desde aqui, <b>no existe</b>.
     *
     * <p>Es la misma respuesta que un identificador inventado, y es lo correcto: la politica RLS no
     * lo deja ver, asi que decir «ese evento es de otra municipalidad» seria contestar con
     * informacion de un inquilino ajeno.
     */
    @Test
    @DisplayName("y un evento de otra municipalidad, desde aqui, tampoco consta")
    void unEventoDeOtraMunicipalidadNoConsta() {
        UUID deLaOtra = emitirEn(otraMunicipalidad);

        Throwable loQueSalio = catchThrowable(() -> acusar(Consumidor.RENTAS, List.of(deLaOtra)));

        assertThat(loQueSalio)
                .isInstanceOf(BuzonDeIdentidad.EventoQueNoConsta.class)
                .hasMessageContaining(deLaOtra.toString());
    }

    // ------------------------------------------------------------------

    private static List<UUID> emitir(int cuantos) {
        List<UUID> emitidos = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            emitidos.add(emitirUno());
        }
        return emitidos;
    }

    private static UUID emitirUno() {
        EventoDeIdentidad evento =
                arnes.transaccion()
                        .execute(
                                estado ->
                                        arnes.buzon()
                                                .emitir(
                                                        HechoDeIdentidad.deMiembro(
                                                                new Miembro(1L, 2L, true),
                                                                "Grupo",
                                                                "cuenta",
                                                                "quien")));
        return java.util.Objects.requireNonNull(evento).eventoId();
    }

    private static UUID emitirEn(long otra) {
        ArnesDeAdministracion.salir();
        ArnesDeAdministracion.entrarComo(otra, "publicador");
        try {
            return emitirUno();
        } finally {
            ArnesDeAdministracion.salir();
            ArnesDeAdministracion.entrarComo(municipalidad, "publicador");
        }
    }

    private static BuzonDeIdentidad.Lote pendientesPara(Consumidor consumidor, int limite) {
        return java.util.Objects.requireNonNull(
                arnes.transaccion()
                        .execute(estado -> arnes.buzon().pendientesPara(consumidor, limite)));
    }

    private static List<UUID> idsPendientesPara(Consumidor consumidor) {
        List<UUID> ids = new ArrayList<>();
        for (EventoDeIdentidad evento : pendientesPara(consumidor, 500).eventos()) {
            ids.add(evento.eventoId());
        }
        return ids;
    }

    private static int acusar(Consumidor consumidor, List<UUID> eventos) {
        Integer escritas =
                arnes.transaccion()
                        .execute(estado -> arnes.buzon().acusar(consumidor, List.copyOf(eventos)));
        return escritas == null ? 0 : escritas;
    }
}
