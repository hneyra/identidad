// kamayuk-identidad-plataforma no es un contexto acotado: es la infraestructura tecnica que
// lleva el contexto de tenant desde TenantContext hasta la transaccion de base de
// datos (ARQ-03 §2). Ningun contexto de negocio la llama; la usa Spring.
//
// Es la MISMA capa que `kamayuk-rentas-plataforma`, copiada en P5B a `normativa` y de ahi aqui,
// con el paquete cambiado. Con `identidad` son CINCO copias del mismo codigo en cinco
// repositorios; lo que corresponde es sacarla a `kamayuk-lib` (ADR-0038, que contesto D-23 el
// 2026-09-07) y ese repositorio hoy esta vacio. El coste de la quinta copia esta escrito en
// `kamayuk/identidad/plataforma/package-info.java`, con los tres defectos que el primer censo de
// `lo-que-los-cinco-comparten` encontro arreglados en una sola copia.

plugins {
    id("kamayuk.java-base")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api(project(":kamayuk-identidad-dominio-compartido"))

    // spring-boot-starter-jdbc trae spring-jdbc y HikariCP. El pool es parte del
    // contrato aqui: la verificacion al devolver la conexion necesita poder
    // descartarla, no solo cerrarla.
    api("org.springframework.boot:spring-boot-starter-jdbc")

    // El filtro lee el claim del token ya validado (ADR-0005).
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // La capa web comun —contrato, errores, paginacion— vive aqui, en
    // kamayuk.identidad.web, y la usan los controladores del contexto `parametros`.
    api("org.springframework.boot:spring-boot-starter-web")

    testImplementation(testFixtures(project(":kamayuk-identidad-esquema")))

    // Solo para la prueba de la cadena de identidad: verifica que /actuator/health
    // y /actuator/prometheus siguen siendo lo unico publico (issue #156).
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly(libs.postgresql)
}
