// Ensambla el artefacto unico de `identidad` (ADR-0003: los perfiles web y batch son el mismo
// jar), y es tambien donde corren las verificaciones que necesitan ver todo el sistema a la vez:
// las reglas de ArchUnit, el escaner de fuentes, el de aserciones, la frontera de sistema y los
// limites de Spring Modulith. Ningun otro modulo tiene en su classpath a todos los demas.

plugins {
    id("kamayuk.java-base")
    id("kamayuk.pruebas-postgres")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))

    implementation(project(":kamayuk-identidad-dominio-compartido"))
    implementation(project(":kamayuk-identidad-plataforma"))

    // La copia local de seguridad: el `ComprobadorDeAcceso` que el guardia necesita y la
    // implantacion de la municipalidad. Sin este modulo el contexto NO ARRANCA —el guardia
    // pide un bean que nadie declara— y eso es lo que C-6 midio y C-7 cierra.
    implementation(project(":kamayuk-identidad-seguridad"))

    // El unico contexto acotado de este sistema (ARQ-01 §3.4), HOY VACIO. Se declara igual: sin la
    // dependencia, la primera clase que entre ahi no estaria en el classpath de este modulo y
    // ninguna de las cinco barreras la miraria — el verde silencioso que `paquetesQueTienenQueExistir()`
    // existe para impedir, aplicado a un modulo entero.
    implementation(project(":kamayuk-identidad-nucleo"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Actuator entra por la sonda de vida y las metricas (issue #156). Se exponen `health` y
    // `prometheus`, y nada mas (application.yaml, SeguridadWeb).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Las migraciones viven en kamayuk-identidad-esquema y las ejecuta el proceso de despliegue
    // como kamayuk_owner. La aplicacion NO migra al arrancar: se conecta como kamayuk_app, que no
    // tiene DDL (ARQ-03 §4).
    runtimeOnly(libs.postgresql)

    // Las barreras, compartidas con los otros cuatro repositorios (composite build; ver
    // settings.gradle.kts). Trae ArchUnit consigo como `api`, junto con JUnit y AssertJ.
    testImplementation("kamayuk.comun:comun-verificaciones")

    // La muestra de caso de uso que viola la regla 10 lleva @Transactional: sin spring-tx no
    // compilaria, y sin ella la regla no tendria como demostrarse.
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // `ArranqueDeLaAplicacionTest` levanta el contexto ENTERO contra un PostgreSQL real, que es
    // lo unico que ve un bean que falta (C-7). De ahi las dos lineas: los fixtures que provisionan
    // la base y el arranque de Spring Boot con su servidor de pruebas.
    testImplementation(testFixtures(project(":kamayuk-identidad-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    // El escaner de aserciones (#724) lee `src/test` de TODOS los modulos, y esas fuentes no estan
    // en el classpath de este. Sin declararlas como entrada, editar una prueba de otro modulo
    // dejaria esta tarea en UP-TO-DATE y una asercion que no puede fallar pasaria en verde rancio.
    // Es la leccion de #192 punto 2 aplicada al unico escaner que recorre `src/test`.
    inputs
        .files(
            rootProject.layout.projectDirectory.asFileTree.matching {
                include("*/src/test/java/**/*.java")
            })
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // NO HAY ENTRADA DE CONTRATOS, y es lo unico que este bloque tiene de menos respecto de
    // `normativa`: alli `ContratoConRentasTest` y `ContratoConCatastroTest` leen
    // `../../<consumidor>/docs/50-api/contratos-que-consume/normativa.json` y hubo que declararlos
    // como entrada, porque sin eso editar un contrato dejaba `test` en UP-TO-DATE y la prueba
    // **no corria** (medido en C-2: `BUILD SUCCESSFUL` con la tarea saltada). Aqui no hay ninguna
    // de las dos porque este sistema todavia no publica ninguna operacion, asi que no hay contrato
    // que ningun consumidor pueda comprometer. El dia que la etapa 2 publique la primera, esa
    // entrada tiene que volver con ella: es la leccion de #192 punto 2 y se pierde por omision.
}

// Nombre fijo del artefacto ejecutable. La imagen lo copia por nombre y no por comodin:
// `*.jar` casaria tambien con el `-plain.jar` que produce el plugin de java-library.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("identidad.jar")
}

// La prueba de arranque va en su PROPIA tarea, y no es una manía de organización.
//
// `verificarArquitectura` corre `:kamayuk-identidad-aplicacion:test` y no necesita motor de base de
// datos: son ArchUnit, escaneres de fuentes y limites de Modulith. `ArranqueDeLaAplicacionTest`
// si lo necesita —levanta el artefacto de verdad y su sonda de salud consulta la base—, asi que
// meterla en `test` convertiria la barrera de arquitectura en una que no se puede correr sin
// PostgreSQL. Se excluye de `test` y se declara aparte; `check` depende de las dos, de modo que
// `./gradlew build` sigue corriendo ambas.
val pruebaDeArranque = tasks.register<Test>("pruebaDeArranque") {
    group = "verification"
    description = "Levanta el artefacto en los perfiles web y batch contra PostgreSQL real (C-7)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*ArranqueDeLaAplicacionTest") }
    // Un arranque que se salta a si mismo deja el build en verde sin haber arrancado nada.
    outputs.upToDateWhen { false }
}

tasks.test {
    filter { excludeTestsMatching("*ArranqueDeLaAplicacionTest") }
}

tasks.check {
    dependsOn(pruebaDeArranque)
}
