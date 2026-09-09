// La copia local de usuarios, grupos y permisos de `identidad` (D-N5, que contesta D-19).
//
// Lo que hay aqui son DOS cosas y no un contexto acotado entero: quien LEE la copia para autorizar
// —`ComprobadorDeAccesoJdbc`, la implementacion del puerto que `kamayuk-identidad-plataforma`
// declara— y quien la SIEMBRA al implantar la municipalidad. En la ETAPA 1 las once escrituras de
// administracion siguen en `rentas` (ADR-0030 §3), asi que aqui no hay ni controlador ni pantalla;
// la etapa 2 las trae, y entonces esto deja de ser una copia y pasa a ser el original (ADR-0039).
//
// El nombre del modulo no se elige: `ConfiguracionDeIdentidad` lo reparte a SISTEMA_REPLICADO,
// porque las tablas de seguridad estan replicadas en los cinco baselines (ADR-0032). Este modulo
// es el que las usa.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    testImplementation(testFixtures(project(":kamayuk-identidad-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-aop")
    testRuntimeOnly(libs.postgresql)
}
