// El unico contexto acotado de `identidad` (ARQ-01 §3), y desde la ETAPA 2 ya no esta vacio:
// aqui viven las once escrituras de administracion —usuarios, grupos, miembros y permisos—, sus
// dos repositorios, sus cuatro controladores, el buzon de salida y la implantacion.
//
// El modulo se declaro en la etapa 1 con solo su `package-info.java`, y eso es lo que hizo que
// estas clases nacieran ya vigiladas: `SISTEMA_DEL_MODULO` de `ConfiguracionDeIdentidad` lo
// reparte, asi que ArchUnit, el escaner de fuentes y la frontera de sistema las miran desde la
// primera. Al reves —traer doscientas clases y despues el modulo— no hay forma de revisarlas una
// a una, que es lo que P5A y P5C costaron.

plugins {
    id("kamayuk.modulo")
    id("kamayuk.pruebas-postgres")
}

dependencies {
    testImplementation(testFixtures(project(":kamayuk-identidad-esquema")))
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-aop")

    // SOLO EN PRUEBAS, y hace falta decir por que: `ComprobadorDeAccesoJdbc` —la implementacion
    // del puerto que el guardia consulta— vive en `kamayuk-identidad-seguridad` a proposito,
    // porque tiene que poder autorizar aunque este contexto acotado no este en el classpath (es la
    // clase que los otros cuatro sistemas copian tal cual). Aqui se necesita para poder medir la
    // etapa 4 de punta a punta: que las cuatro cuentas de servicio que la implantacion siembra
    // pasan el guardia DE VERDAD. Escribir en la prueba un comprobador propio habria medido esa
    // copia y no el que corre en produccion, que es exactamente lo que hace que una prueba de
    // autorizacion no sirva. Es una arista de PRUEBAS: `main` de este modulo no ve `seguridad`, y
    // Spring Modulith sigue verificando lo mismo.
    testImplementation(project(":kamayuk-identidad-seguridad"))

    // MockMvc para las pruebas de frontera: transporte sin servidor, que es donde se ve que lo que
    // el controlador declara es lo que contesta.
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly(libs.postgresql)
}

// AC-4: el catalogo de accesos de los CINCO sistemas, copiado al jar.
//
// Son 157 opciones —130 de `rentas`, 16 de `catastro`, 7 de aqui, 3 de `caja` y 1 de
// `normativa`; eran 160 al escribirse esto, 161 con `eventos` (etapa 3) y 157 desde que la etapa 4
// retiro de `rentas` las cuatro de administracion que ya no sirve— y esta base las siembra todas,
// porque lo que guarda es a quien se le concede cada
// opcion de todos (ADR-0039). El de `rentas` se DERIVA de su catalogo del manual con
// `docs/10-negocio/derivar-catalogo-de-rentas.mjs`; los otros cuatro estan transcritos de su
// `CatalogoDelSistema.java`. Lo que impide que las cinco copias se separen de sus originales es la
// guarda cruzada de `infrastructure`, que las compara en las dos direcciones.
//
// La comprobacion de que los archivos existen NO es defensiva, y lo aprendio `rentas` con su
// markdown: un `Copy` cuya fuente no esta se salta con NO-SOURCE y el build sigue EN VERDE,
// dejando un jar sin catalogo. Eso paso de verdad —la imagen de Docker se construia con el
// contexto en `backend/`, y el catalogo vive fuera— y el sintoma aparecio dos versiones despues:
// la aplicacion arrancaba, servia peticiones, y la implantacion no encontraba ninguna opcion que
// sembrar.
//
// Va en una tarea aparte y no en un `doFirst` del `Copy` por lo mismo: un `Copy` sin fuente se
// salta ENTERO y sus acciones no llegan a ejecutarse — el guardia se saltaria junto con lo que
// guarda.
val carpetaDelCatalogo = "../docs/10-negocio/catalogo-de-accesos"
val directorioDelCatalogo = rootProject.layout.projectDirectory.dir(carpetaDelCatalogo).asFile

val exigirCatalogo = tasks.register("exigirCatalogoDeAccesos") {
    description = "Falla si falta alguno de los cinco catalogos de accesos (AC-4)."
    val donde = directorioDelCatalogo
    val ruta = carpetaDelCatalogo
    // Los cinco por su nombre, y no `*.json`: un directorio con cuatro archivos tambien casa con
    // el comodin, y lo que hay que impedir es exactamente eso — un catalogo que falta deja a su
    // sistema sin ninguna opcion configurable, y en silencio.
    val sistemas = listOf("rentas", "catastro", "normativa", "caja", "identidad")
    outputs.upToDateWhen { false }
    doLast {
        val faltan = sistemas.filterNot { donde.resolve("$it.json").exists() }
        if (faltan.isNotEmpty()) {
            throw GradleException(
                "Faltan en $ruta los catalogos de: ${faltan.joinToString(", ")}." +
                    " Sin ellos el jar sale sin las opciones de esos sistemas: nadie puede dar" +
                    " permiso a ninguna de sus pantallas (RF-122) y la implantacion no las siembra." +
                    " El de `rentas` se genera con" +
                    " `node docs/10-negocio/derivar-catalogo-de-rentas.mjs`. Si esto ocurre dentro" +
                    " de una imagen de Docker, el contexto de compilacion no incluye docs/.",
            )
        }
    }
}

// `Sync` y no `Copy`, y esta medido por que: un `Copy` no borra del destino lo que desaparecio
// del origen, asi que apartando `catastro.json` del repositorio y neutralizando la guarda de
// arriba, las seis pruebas del catalogo unido y la de la implantacion salieron VERDES sobre la
// copia rancia que seguia en `build/` — y esa copia viaja al jar. Con `Sync`, lo que no esta en
// `docs/` no esta en el artefacto, que es la unica forma de que la guarda tenga algo que guardar.
val catalogoDeAccesos = tasks.register<Sync>("copiarCatalogoDeAccesos") {
    description = "Copia los cinco catalogos de accesos a los recursos del nucleo."
    dependsOn(exigirCatalogo)
    from(directorioDelCatalogo)
    include("*.json")
    into(layout.buildDirectory.dir("generated/recursos/catalogo-de-accesos"))
}

sourceSets {
    named("main") {
        resources.srcDir(catalogoDeAccesos.map { it.destinationDir.parentFile })
    }
}
