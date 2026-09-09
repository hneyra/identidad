// Backend de `identidad`: el quinto sistema de Kamayuk (ADR-0039), que es el dueno de usuarios,
// grupos, accesos y permisos.
//
// ETAPA 1 (infrastructure#52): el repositorio existe, verifica y despliega. CERO codigo de
// negocio: `kamayuk-identidad-nucleo` nace vacio salvo su `package-info.java`. Las once
// escrituras de administracion llegan en la etapa 2, con las pantallas que hoy viven en `rentas`.
//
// Las barreras —ArchUnit, el escaner de fuentes, el de aserciones y la frontera de sistema— viven
// en `infrastructure/librerias-backend` y las comparten los cinco repositorios. Se consumen como
// *composite build* y no como artefacto publicado, y el motivo es el modo de fallo: un jar
// publicado a mano se queda viejo sin que nada se ponga rojo, y una verificacion vieja que pasa en
// verde es lo que este proyecto lleva doscientos issues evitando. Con `includeBuild`, Gradle la
// recompila desde el fuente en cada build: no puede quedarse vieja.
//
// LO QUE CUESTA, dicho aqui y no descubierto mas tarde: este backend NO COMPILA sin tener
// `infrastructure` clonado al lado.
val libreriasComunes = file("../../infrastructure/librerias-backend")

// LA UNICA SALIDA, Y SOLO PARA CONSTRUIR EL ARTEFACTO (C-7, punto 5).
//
// La imagen construye con el contexto en la raiz de ESTE repositorio, y
// `infrastructure/librerias-backend` vive en un clon hermano: fuera del contexto, y sin forma de
// meterlo dentro —un `.dockerignore` no puede describir un contexto que es el directorio padre—.
//
// Lo que se midio antes de decidir: `comun-verificaciones` es `testImplementation` y **solo** de
// `kamayuk-identidad-aplicacion`. La imagen construye `bootJar` e `installDist` y no corre ni una
// prueba, asi que no necesita la libreria para nada — lo unico que la necesitaba era el `require`.
//
// Con la propiedad puesta el build se queda SIN las verificaciones, y para que eso no pueda
// convertirse en «verificar sin verificar» el `build.gradle.kts` de la raiz **hace fallar toda
// tarea de prueba** mientras este puesta. O sea: o esta la libreria, o no hay verificacion; nunca
// una verificacion que pasa en verde sin ella (#192).
val soloElArtefacto = providers.gradleProperty("kamayuk.sinLibreriasComunes").isPresent

require(libreriasComunes.isDirectory || soloElArtefacto) {
    "No esta ${libreriasComunes.canonicalPath}. El backend consume comun-verificaciones como" +
        " composite build, asi que `infrastructure` tiene que estar clonado al lado de" +
        " `identidad`: git clone https://github.com/hneyra/infrastructure ../../infrastructure"
}
if (!soloElArtefacto) {
    includeBuild(libreriasComunes)
}

rootProject.name = "kamayuk-identidad-backend"

// Compartido: objetos de valor y contexto de tenant. No depende de ningun contexto acotado.
include("kamayuk-identidad-dominio-compartido")

// El esquema: migraciones Flyway y la prueba de aislamiento multi-tenant.
include("kamayuk-identidad-esquema")

// Plataforma: lleva el contexto de tenant hasta la transaccion (ARQ-03 §2).
include("kamayuk-identidad-plataforma")

// El unico contexto acotado de este sistema (ARQ-01 §3.4), y HOY ESTA VACIO: solo su
// `package-info.java`. Se declara desde la etapa 1 y no cuando llegue el codigo, porque un modulo
// que aparece con doscientas clases dentro no tiene forma de revisarse; con el declarado, la
// primera clase que entre ya nace con ArchUnit, el escaner de fuentes y la frontera de sistema
// mirandola.
include("kamayuk-identidad-nucleo")

// La copia local de usuarios, grupos y permisos, y su siembra (D-N5). En la etapa 1 hace lo mismo
// que en los otros cuatro —leer para autorizar y sembrar al implantar— y NO es todavia el dueno:
// las once escrituras de administracion siguen en `rentas` (ADR-0030 §3) hasta la etapa 2, que es
// la que las trae aqui. Lo que si es de este sistema desde ya es la columna `sistema` de
// `modulo_sistema` y `acceso`: esta base guarda a quien se concede cada opcion de CUATRO
// catalogos, y dos sistemas pueden nombrar igual dos opciones distintas.
include("kamayuk-identidad-seguridad")

// Ensambla el artefacto unico y aloja las verificaciones.
include("kamayuk-identidad-aplicacion")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
