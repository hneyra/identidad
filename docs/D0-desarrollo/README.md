# D0 — Desarrollo

Como montar el ambiente local de `identidad`, arrancarlo y probarlo. Escrito para quien acaba de
clonar el repositorio y quiere ver algo funcionando **hoy**.

**Lo que esta escrito aqui se ejecuto.** Donde algo no se pudo ejecutar en la maquina en que se
escribio, se dice — en vez de omitirlo, que es como una guia se vuelve falsa sin que nadie lo note.

## Lo primero, y no es un detalle

**`infrastructure` tiene que estar clonado al lado.** Dos cosas de este repositorio lo necesitan y
ninguna de las dos se puede redirigir con una variable de entorno:

- las barreras que el backend ejecuta viven en `infrastructure/librerias-backend` y se consumen
  como *composite build*; `settings.gradle.kts` lo comprueba antes y falla diciendo que `git clone`
  falta, en vez de dejar reventar a Gradle sobre un directorio que no esta;
- el contrato del descriptor es `link:../../infrastructure/infra/contrato` en el `package.json` de
  `infrastructure/`, y **eso lo resuelve yarn al instalar, contra el disco**.

```bash
cd ..                                                   # el directorio que contiene a identidad/
git clone https://github.com/hneyra/infrastructure
```

Queda asi, y las rutas de este documento cuentan con ello:

```
IdeaProjects/
├── infrastructure/     la plataforma y las barreras comunes
├── identidad/          este repositorio
├── rentas/  catastro/  normativa/  caja/     los otros cuatro sistemas
└── sgtm/               el archivo historico (opcional, pero se consulta a diario)
```

Es la misma disposicion que usa el CI, y no por comodidad: `actions/checkout` **se niega** a
escribir fuera del espacio de trabajo, asi que lo que se mueve alli es el anfitrion —este
repositorio se clona en `<espacio>/identidad` y el hermano en `<espacio>/infrastructure`— y el
`link:` resuelve exactamente igual (C-9a).

## Lo minimo para empezar

```bash
# 1 · Prerrequisitos. Docker solo hace falta para la plataforma y para el aislamiento
java -version && node --version && yarn --version

# 2 · El descriptor de despliegue. NO necesita Pulumi, ni token, ni cluster
cd infrastructure && yarn install && yarn verificar

# 3 · Las barreras de arquitectura del backend. NO necesitan Docker, ni base de datos, ni red
cd ../backend && ./gradlew verificarArquitectura

# 4 · El aislamiento multi-tenant. Requiere PostgreSQL 16
./gradlew verificarAislamiento
```

## Que comando para que tarea

| Quiero… | Comando | Donde |
|---|---|---|
| Verificar el descriptor (lint, tipos y pruebas) | `yarn verificar` | `infrastructure/` |
| Las reglas de arquitectura y los escaneres | `./gradlew verificarArquitectura` | `backend/` |
| El aislamiento multi-tenant | `./gradlew verificarAislamiento` | `backend/` |
| Que el artefacto levante en los dos perfiles | `./gradlew verificarArranque` | `backend/` |
| Todo, mas el formato | `./gradlew build` | `backend/` |
| Arreglar el formato | `./gradlew spotlessApply` | `backend/` |
| Levantar la plataforma | `docker compose -f despliegue/plataforma.compose.yaml up -d --wait` | `../infrastructure/` |
| Levantar ESTE sistema contra ella | `docker compose -f despliegue/compose.yaml up --build --wait` | aqui |
| La guarda del registro | `node docs/00-gobierno/verificar-fila-del-registro.mjs` | aqui |
| Su autoprueba, que va **antes** | `node docs/00-gobierno/verificar-las-muestras-del-registro.mjs` | aqui |

## La plataforma, y el orden

Este repositorio **no trae PostgreSQL ni Keycloak**: los usa. Los levanta el compose de la
plataforma, que vive en el clon hermano:

```bash
cp despliegue/.env.ejemplo despliegue/.env      # en `infrastructure`, y poner claves generadas
docker compose -f ../infrastructure/despliegue/plataforma.compose.yaml up -d --wait
docker compose -f despliegue/compose.yaml up --build --wait
```

El segundo encadena tres servicios —`identidad-migraciones` → `identidad-implantacion` →
`identidad-sistema`— con `depends_on: service_completed_successfully`, que es la misma
dependencia que en el cluster expresa el `initContainer` del Job de implantacion.

**El servicio del backend se llama `identidad-sistema` y no `identidad`**, y el motivo esta
escrito entero en la cabecera de `despliegue/compose.yaml`: en la red compartida
`kamayuk-plataforma` el alias `identidad` **ya lo tiene Keycloak**, y los cinco backends le piden
ahi su JWKS. Con dos contenedores registrando el mismo alias, el DNS interno de Docker reparte
entre los dos y la mitad de las peticiones de claves de firma iria a un backend de Spring: todo
token invalido, de forma intermitente.

## `verificarAislamiento` no se omite sin Docker: falla

Una prueba bloqueante que se salta a si misma deja el build en verde sin haber verificado nada. La
salida documentada es apuntar a un PostgreSQL 16 que ya exista, y **ninguna que omita la prueba**:

```bash
./gradlew verificarAislamiento \
  -Dkamayuk.pruebas.postgres.url=jdbc:postgresql://localhost:5432/postgres \
  -Dkamayuk.pruebas.postgres.usuario=postgres \
  -Dkamayuk.pruebas.postgres.clave=…
```

Tiene que ser **superusuario**, porque la prueba crea los roles del cluster. Y **PostgreSQL 16**:
es el motor que la plataforma levanta, asi que verificar contra otro no dice nada del que corre.

**El esquema de este sistema no declara ninguna extension** (AC-3 del issue #1, medido migracion
por migracion), asi que aqui la imagen `postgis` que el CI se trae no la pide el esquema: la pide
que el motor sea el mismo que el de la plataforma.

## Las dos frases que gobiernan todo lo demas

**Ejecutar la prueba vale mas que razonar sobre ella**, y **una verificacion tiene que demostrarse
capaz de fallar**: se rompe a proposito el codigo que protege, se ejecuta, y se anota el rojo
exacto que sale. Es lo que la tabla de `CLAUDE.md` §«Verificar antes de afirmar» guarda, issue a
issue, y lo que `docs/00-gobierno/verificar-fila-del-registro.mjs` exige en cada PR que cierre uno
y toque codigo de produccion.
