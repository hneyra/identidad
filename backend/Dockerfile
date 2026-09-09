# syntax=docker/dockerfile:1
#
# Dos imagenes del mismo arbol de fuentes, y esa es la decision principal de este
# archivo:
#
#   `aplicacion`  el artefacto unico de ADR-0003, desplegado en los perfiles web y
#                 batch. Se conecta como kamayuk_app: sin DDL, sin BYPASSRLS, sin ser
#                 propietaria de ninguna tabla y sin DELETE (ARQ-03 §4).
#   `migrador`    el proceso que aplica el esquema como kamayuk_owner y termina.
#
# Estan separadas porque las credenciales son distintas y no deben convivir en el
# mismo contenedor: las de kamayuk_owner existen durante la migracion y desaparecen
# con ella. Meterlas en la imagen de la aplicacion —aunque solo fuera «para el
# arranque»— le daria a un proceso de larga vida, expuesto en HTTP, la capacidad
# de hacer DDL sobre el padron de todas las municipalidades.
#
# La compilacion, en cambio, es una sola: las dos salen del mismo `construccion`,
# asi que el codigo que migra en produccion es exactamente el que CI verifico.

# El contexto de compilacion es la RAIZ del repositorio, no `backend/`. En el monolito eso era por
# el catalogo de opciones; aqui ya no —ver el comentario del `COPY` que se fue—, y se conserva
# porque el `.dockerignore` que acota el contexto vive en la raiz y desde `backend/` no se aplica.
#
# QUIEN CONSTRUYE ESTA IMAGEN TIENE QUE HACERLO DESDE LA RAIZ:
#   docker build -f backend/Dockerfile --target aplicacion -t ghcr.io/hneyra/kamayuk-identidad .
#   docker build -f backend/Dockerfile --target migrador  -t ghcr.io/hneyra/kamayuk-identidad-migrador .
#
# En `rentas` el motivo original sigue vigente y conviene tenerlo escrito, porque es la cicatriz
# que dejo: el catalogo de las 134 opciones (NEG-03) vive en su `docs/` y su build lo copia a los
# recursos de `kamayuk-rentas-seguridad`. Con el contexto en `backend/` ese archivo no existia
# dentro de la imagen, la tarea que lo copia se saltaba con NO-SOURCE, y el jar salia sin catalogo
# —compilando en verde—. El sintoma aparecio mucho despues: la aplicacion arrancaba y servia
# peticiones, pero la implantacion no encontraba ninguna opcion que sembrar, asi que nadie podia
# entrar a ninguna pantalla.
#
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS construccion

WORKDIR /origen/backend

# Primero lo que casi nunca cambia, para que la capa de dependencias se reutilice
# entre compilaciones. `--version` descarga la distribucion de Gradle y nada mas.
COPY backend/gradlew backend/gradle.properties backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle/ gradle/
RUN ./gradlew --version --no-daemon

COPY backend/buildSrc/ buildSrc/
COPY backend/config/ config/

# NO se trae nada de fuera de `backend/`, y es lo que hace que esta imagen se pueda construir.
#
# El `Dockerfile` del monolito copia `docs/10-negocio/catalogo-de-opciones.md` a los recursos de su
# modulo de seguridad: alli ese archivo es la fuente de las 134 opciones que siembra la implantacion
# (RF-122). En `identidad` NO EXISTE —se quedo en `rentas` con el resto del catalogo del manual—, asi
# que copiarlo fallaria con «not found» antes de compilar una sola clase, que es exactamente lo que le
# pasaba a `catastro` hasta C-7.
#
# Su parte del catalogo la declara `kamayuk.identidad.seguridad.dominio.CatalogoDelSistema`, en codigo.
# En los otros cuatro hay ademas una prueba que la compara contra los `@RequiereAcceso` del propio
# repositorio; aqui esa comparacion todavia no tiene sujeto —la etapa 1 no trae capa web— y
# `CatalogoDelSistemaTest` lo dice y lo sujeta, de modo que el primer controlador la pone roja.

COPY backend/ ./

# Sin pruebas: las bloqueantes necesitan Docker y ya corrieron en CI. Una imagen
# que las repitiera dentro del build las volveria a ejecutar en cada despliegue,
# con un motor distinto al verificado.
# `-Pkamayuk.sinLibreriasComunes` (C-7, punto 5). Sin ella el build se para en el `require` de
# `settings.gradle.kts`: el composite build de `comun-verificaciones` vive en el clon hermano
# `infrastructure/`, que esta FUERA del contexto de esta imagen y no se puede meter dentro —un
# `.dockerignore` no puede describir un contexto que es el directorio padre—. Era el hueco 9.4 de
# P5A, heredado de P3, y llevaba ahi sin que nada lo midiera porque ningun CI construye todavia
# esta imagen.
#
# Se puede porque `comun-verificaciones` es `testImplementation` y solo de
# `kamayuk-identidad-aplicacion`: aqui no corre ni una prueba. Y no abre una puerta, porque el
# `build.gradle.kts` de la raiz **hace fallar toda tarea de prueba** mientras la propiedad este
# puesta: o esta la libreria, o no hay verificacion.
RUN ./gradlew --no-daemon -Pkamayuk.sinLibreriasComunes \
    :kamayuk-identidad-aplicacion:bootJar :kamayuk-identidad-esquema:installDist

# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS aplicacion

# Usuario sin privilegios y sin shell. Un proceso que solo tiene que escuchar en
# un puerto no necesita ser root dentro del contenedor.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin kamayuk

# curl entra por una sola razon: la sonda de vida del orquestador se ejecuta
# DENTRO del contenedor, y la imagen base no trae ningun cliente HTTP. La
# alternativa —comprobar solo que el puerto acepta conexiones— daria por sana una
# aplicacion que escucha y no llega a la base, que es justo el fallo que se quiere
# detectar antes de dar el despliegue por bueno.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# `identidad.jar` en `/opt/kamayuk/identidad.jar`: cada sistema produce el jar con SU
# nombre y lo deja bajo el directorio del producto (R-A/B, etapa A punto 3).
#
# Antes de R-A/B los cuatro no decian lo mismo: `rentas`, `catastro` y `caja` producian
# `sgtm.jar` —el nombre heredado del monolito— y `normativa` su `normativa.jar`, y ese
# desajuste ya costo que la imagen de `normativa` NO SE PUDIERA CONSTRUIR (C-17, arreglo
# B): `docker build` compilaba entero —Gradle en verde, el jar producido— y se caia en el
# ultimo `COPY` con «stat .../sgtm.jar: file does not exist». Desde el cluster el sintoma
# es `ImagePullBackOff`, que se lee como «el registro aun no la publico».
#
# Lo que ata las dos mitades son dos guardas de `infrastructure`, no este comentario: el
# `COPY` contra el `archiveFileName` del modulo (C-17), y el `ENTRYPOINT` contra el
# destino del `COPY` (R-A/B) — un destino que no cuadre con el `ENTRYPOINT` construye la
# imagen sin protestar y muere al arrancar con «Unable to access jarfile».
COPY --from=construccion /origen/backend/kamayuk-identidad-aplicacion/build/libs/identidad.jar /opt/kamayuk/identidad.jar
USER 10001
EXPOSE 8080

# Sin CMD con argumentos: la configuracion entra por variables de entorno
# (KAMAYUK_DB_URL, KAMAYUK_DB_USUARIO, KAMAYUK_DB_CLAVE) y por SPRING_PROFILES_ACTIVE,
# que es lo unico que distingue el perfil web del batch.
ENTRYPOINT ["java", "-jar", "/opt/kamayuk/identidad.jar"]

# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS migrador

RUN useradd --system --uid 10002 --shell /usr/sbin/nologin migrador
COPY --from=construccion /origen/backend/kamayuk-identidad-esquema/build/install/migrar /opt/migrador
USER 10002

# Corre y termina. El orquestador espera a que salga con exito antes de arrancar
# la aplicacion: un esquema a medias con la aplicacion ya sirviendo peticiones es
# el estado que este orden existe para impedir.
ENTRYPOINT ["/opt/migrador/bin/migrar"]
