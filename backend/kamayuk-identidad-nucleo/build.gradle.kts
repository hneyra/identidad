// El unico contexto acotado de `identidad` (ARQ-01 §3), y HOY ESTA VACIO: solo su
// `package-info.java`.
//
// El modulo se declara en la etapa 1 y no cuando llegue el codigo, y no es adelantarse: las
// barreras se aplican por modulo —`SISTEMA_DEL_MODULO` de `ConfiguracionDeIdentidad` lo reparte, y
// `modulosDelReparto()` exige que ningun modulo del disco se quede fuera—, asi que con el
// declarado la primera clase que entre ya nace con ArchUnit, el escaner de fuentes y la frontera de
// sistema mirandola. Al reves —traer doscientas clases y despues el modulo— no hay forma de
// revisarlas una a una, que es lo que P5A y P5C costaron.
//
// Un paquete con SOLO `package-info.java` no es un modulo para Spring Modulith: hace falta al
// menos un tipo. Por eso `nucleo` NO esta en la lista de `ModulosTest` todavia, y ese javadoc lo
// dice donde se lee.

plugins {
    id("kamayuk.modulo")
}
