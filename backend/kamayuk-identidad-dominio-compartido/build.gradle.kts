// Objetos de valor y contexto de tenant. No depende de ningun contexto acotado,
// ni de Spring: lo usan todos, incluida la capa `dominio`, que debe poder
// probarse sin levantar el contexto (ARQ-04 §1).
//
// QUINTA COPIA. Es byte a byte el mismo modulo que en los otros cuatro sistemas, y lo que
// corresponde es sacarlo a `kamayuk-lib` (ADR-0038); ese repositorio hoy esta vacio. Lo que cuesta
// esta escrito en `kamayuk/identidad/dominio/package-info.java` y no en un issue: `Observacion`
// llega aqui con el `Objects.requireNonNull` que `rentas`#30 midio que salia como 500 con
// incidencia, porque el arreglo se hizo en una copia y las otras no se enteraron.

plugins {
    id("kamayuk.pruebas")
}
