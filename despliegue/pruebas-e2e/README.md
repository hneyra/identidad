# Ejercer la API de `identidad` contra la pila levantada

Esta carpeta es para dos audiencias:

* **el frontend y los otros modulos**, que necesitan saber que pide y que devuelve esta API sin
  leerse el backend — para eso son los `.http`;
* **el que despliega**, que necesita saber si lo que acaba de levantar *funciona* — para eso es
  `ejercer.sh`, que afirma y sale con codigo distinto de cero.

| Archivo | Que es |
|---|---|
| `00-token.http` | los **dos** tokens, y por que no son intercambiables |
| `10-seguridad.http` | las **21** operaciones de `/identidad/api/v1/seguridad` |
| `20-eventos.http` | el buzon: las 2 operaciones que consumen los cuatro satelites |
| `ejercer.sh` | los mismos ejercicios en `curl`, con **55 casos** que afirman |

Los `.http` se abren con REST Client (VS Code) o con el cliente HTTP de IntelliJ. `ejercer.sh` solo
necesita `curl`, `jq` y `bash`.

## Las direcciones, medidas y no supuestas

**No son los puertos por omision.** Los pone `infrastructure/despliegue/.env`, y en esta
instalacion son:

| Que | Donde | De donde sale |
|---|---|---|
| La API | `http://localhost:8082/identidad/api/v1` | Traefik, `KAMAYUK_PUERTO_INGRESO` |
| Keycloak | `http://localhost:8181/realms/kamayuk` | `KAMAYUK_PUERTO_IDENTIDAD` |
| El correo (Mailpit) | `http://localhost:8026` | `KAMAYUK_PUERTO_CORREO` |
| PostgreSQL | `localhost:5433` | `KAMAYUK_PUERTO_BASE` |

**El backend no publica ningun puerto**: se entra por Traefik, que encamina `PathPrefix(/identidad)`
sin quitar el prefijo. Y `/actuator/health` **no** cuelga de `/identidad`, asi que desde el anfitrion
solo se alcanza entrando al contenedor:

```bash
docker compose -f despliegue/compose.yaml exec identidad-sistema \
  curl -s localhost:8080/actuator/health
```

## Levantarlo desde cero

```bash
# 1 · La plataforma: PostgreSQL con las cinco bases, Keycloak con sus dos realms, Traefik
cd ../../../infrastructure
docker compose -f despliegue/plataforma.compose.yaml up -d --wait

# 2 · Este sistema: migraciones -> implantacion -> backend
cd ../identidad
docker compose -f despliegue/compose.yaml \
  --env-file ../infrastructure/despliegue/.env up --build --wait
```

El `--env-file` **es imprescindible** y no esta en la documentacion de ningun repositorio: los cinco
composes interpolan variables del `.env` de la plataforma, ninguno declara `env_file:`, y ninguno
tiene `.env` propio. Sin el, el `up` muere en el primer `${...:?}`. Es
[`infrastructure`#74](https://github.com/hneyra/infrastructure/issues/74).

Comprobar que la implantacion hizo su trabajo —**170 eventos y 5 cuentas** con el catalogo de hoy—:

```bash
docker compose -f ../infrastructure/despliegue/plataforma.compose.yaml exec base \
  psql -U postgres -d identidad \
  -c "SELECT tipo, count(*) FROM identidad_evento GROUP BY tipo ORDER BY tipo" \
  -c "SELECT cuenta FROM usuario ORDER BY cuenta"
```

## Las identidades: cuatro pasos, y tres son rodeos de defectos abiertos

Esto es lo que hay que hacer **hoy** para que un token sirva. Tres de los cuatro pasos existen por
defectos que ya tienen issue: se dicen para que nadie los descubra otra vez, y para que desaparezcan
de este README el dia que se cierren.

Todo desde `infrastructure/despliegue/`, con su `.env` cargado:

```bash
cd ../infrastructure/despliegue
set -a; . ./.env; set +a
export COMPOSE_FILE=plataforma.compose.yaml   # rodeo de #74
export KC_REALM=kamayuk
```

### 1 · Devolverle al realm los ambitos que el import le borro — rodeo de [#72](https://github.com/hneyra/infrastructure/issues/72)

```bash
./identidad/restaurar-ambitos-de-fabrica.sh
```

Sin esto **todo funcionario recibe 403** con un token valido: declarar `clientScopes` en el realm
versionado borra los trece ambitos de fabrica, y sin `profile` no hay `preferred_username`, que es
el claim con el que el guardia identifica la cuenta.

### 2 · Una clave conocida para el administrador

```bash
./identidad/crear-usuario.sh administrador '<una clave>' 1
```

**Sin `--reset`**: asi la clave es permanente. Con `--reset` es temporal, Keycloak exige cambiarla al
entrar, y el `grant_type=password` no sirve.

### 3 · Los cuatro clientes de servicio, para el buzon

```bash
mkdir -p /tmp/claves && chmod 700 /tmp/claves
for s in rentas catastro normativa caja; do
  openssl rand -hex 16 > /tmp/claves/$s-200105
done
CLAVES_DE_SERVICIO=/tmp/claves UBIGEO=200105 ./identidad/reconciliar-identidades.sh servicios
```

### 4 · Alinear el inquilino de las cinco cuentas — rodeo de [#73](https://github.com/hneyra/infrastructure/issues/73)

El claim `municipalidad_id` se escribe con **tres valores distintos** segun quien lo escriba: el
ubigeo (`200105`) para las cuentas de servicio, el `municipalidadId` del JSON (`9`) para los
funcionarios, y el id que la secuencia asigna al implantar (`1`) en la base. Solo el ultimo es el
que el RLS entiende, asi que hay que poner los otros a ese valor:

```bash
ID_EN_LA_BASE=$(docker compose exec -T base psql -U postgres -d identidad -tAc \
  'SELECT id FROM municipalidad LIMIT 1')

kc() { docker compose exec -T identidad /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master \
  --user "${KAMAYUK_KEYCLOAK_ADMIN:-admin}" --password "$KAMAYUK_CLAVE_KEYCLOAK"

# el funcionario
UID_ADMIN=$(kc get users -r kamayuk -q username=administrador --fields id \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
kc update "users/$UID_ADMIN" -r kamayuk -s "attributes.municipalidad_id=$ID_EN_LA_BASE"

# y las cuatro cuentas de servicio, que Keycloak NO lista en `get users`
for s in rentas catastro normativa caja; do
  CID=$(kc get clients -r kamayuk -q "clientId=kamayuk-$s-servicio-200105" --fields id \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])')
  SUID=$(kc get "clients/$CID/service-account-user" -r kamayuk \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  kc update "users/$SUID" -r kamayuk -s "attributes.municipalidad_id=$ID_EN_LA_BASE"
done
```

Si esto falta, el sintoma es **403 «La cuenta «…» no esta dada de alta en este sistema»** con la
fila delante en la tabla: el `SET LOCAL` fija otro inquilino y el RLS esconde las cinco filas.

## Correr `ejercer.sh`

```bash
cd ../../identidad/despliegue/pruebas-e2e
CLAVE_DEL_ADMINISTRADOR='<la del paso 2>' \
CLAVE_DE_SERVICIO_RENTAS="$(cat /tmp/claves/rentas-200105)" \
  ./ejercer.sh
```

Con la pila bien levantada: **55 casos, 0 fallos**, codigo de salida 0. Se puede pisar por entorno
`INGRESO`, `KEYCLOAK`, `REALM`, `UBIGEO` y `ADMINISTRADOR`.

**No omite nada en silencio.** Sin la clave de servicio, el buzon no se salta: el guion falla
diciendo que falta. Para dejarlo fuera a proposito hay que pedirlo, y entonces lo dice en el
resumen:

```bash
./ejercer.sh --sin-buzon
```

## Que se ejerce, y por que los negativos son la mitad

De los 55 casos, **16 son negativos**. No son adorno: son el contrato. Un e2e que solo prueba el
camino feliz no distingue una API que valida de una que se traga cualquier cosa.

| Se manda | Contesta |
|---|---|
| nada de token | 401 `NO_AUTENTICADO` |
| un token de persona al buzon | 403 `SIN_IDENTIDAD_DE_SERVICIO` — no `SIN_PRIVILEGIO`, porque se arregla pidiendo otro token y no concediendo un permiso |
| una escritura sin `observacion`, o con menos de 5 caracteres | 422 |
| permisos sin `sistema` | 422 enumerando los cinco |
| permisos **sin** `privilegios` | 422 — `[]` es la retirada explicita, y ausente es un olvido |
| un privilegio que no existe (`ESCRITURA`) | 422 con los siete |
| `ordenarPor` fuera de la lista blanca | 422 `ORDEN_NO_ADMITIDO` |
| un parametro de query que la operacion no declara | 422 nombrandolo |
| una vigencia invertida | 422 |
| una cuenta repetida | 409 |
| un grupo de otra municipalidad | 404 — el RLS no distingue «no existe» de «no es tuya», y eso es la propiedad, no el defecto |
| un verbo equivocado sobre una ruta que existe | 405 con `Allow` |
| retirar al ultimo administrador | 409 |
| `limite=501` en el buzon | 422 |
| un acuse que no es un UUID, o de un evento que no consta | 422 |

## Como se demostro que este e2e puede fallar

Un e2e que no puede fallar no protege nada. Medido: se cambio la etiqueta de Traefik de
`PathPrefix(/identidad)` a `PathPrefix(/identidad-otro)` en `despliegue/compose.yaml`, se recreo el
contenedor del backend, y `ejercer.sh` salio con **codigo 1 y 52 de 52 casos en rojo**, el primero
diciendo «esperaba 200 y contesto 404». Restaurado el compose y recreado el contenedor, vuelve a
**55 casos, 0 fallos**.

## Lo que este e2e NO cubre, dicho

* **El portal del ciudadano.** `SeguridadWeb` ya monta su cadena contra el realm
  `kamayuk-ciudadano`, pero no hay ni un controlador bajo `/identidad/api/v1/portal/**` todavia.
* **Los cuatro consumidores de verdad.** Lo que se ejerce es el buzon con un token de servicio; que
  los cuatro ingestores lo apliquen bien es de sus repositorios, y lo que se comprueba aqui son sus
  cuatro `contratos-que-consume/identidad.json` por reflexion, en `ContratoCon<Consumidor>Test`.
* **La ventana de inconsistencia** de la copia local: exige las cinco aplicaciones levantadas.
