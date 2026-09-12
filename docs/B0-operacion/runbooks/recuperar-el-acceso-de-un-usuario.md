# Runbook — Recuperar el acceso de un usuario

| Campo | Valor |
|---|---|
| Cuándo | Un funcionario no puede entrar, o entra y recibe **403**. Le puede faltar una de **tres** cosas —la clave, la ficha o el permiso—, y las tres se reportan igual y se arreglan en tres sitios distintos |
| Qué cubre | Separar los tres casos con una medida cada uno, y devolver el acceso: la **clave** en Keycloak, la **ficha** y los **permisos** por la API de `identidad`. Más el cuarto caso —el claim `municipalidad_id` desalineado— que es el peor de leer |
| Qué **no** cubre | «Nadie puede entrar» → [Keycloak no responde](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/keycloak-no-responde.md). Dar de alta a alguien **nuevo** en el emisor → [Abrir la consola de administración de Keycloak](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/abrir-la-consola-de-keycloak.md) §4 |
| Estado del ensayo | **Diagnóstico ensayado entero contra el `prod` real** (`vmd206041`, 2026-09-12): los cuatro mensajes de error, la lectura de la ficha, los grupos, los permisos y el RLS. **Ningún remedio se ensayó** —todos escriben— y **un caso no se pudo producir** sin crear una cuenta. Ver «Estado del ensayo» |

> **Este documento vivía en el repositorio archivo `sgtm` y estaba pre-renombrado**: mandaba
> a `kubectl -n sgtm-<amb>`, a `kc get users -r sgtm`, al cliente `sgtm-backoffice` y al
> `Secret` `sgtm-<amb>-smtp`. Ninguno de esos nombres existe. Se trajo y se remidió entero
> ([#100](https://github.com/hneyra/infrastructure/issues/100), sub-issue
> [#107](https://github.com/hneyra/infrastructure/issues/107)).
>
> **Y este es el único de los runbooks del archivo que cambia de repositorio, por una razón
> que es el trabajo entero.** Desde
> [ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md)
> —y desde su etapa 5— **`identidad` es el único sitio del producto donde se escribe la
> autorización**: en `rentas`, `catastro`, `normativa` y `caja` las tablas `usuario`,
> `grupo`, `miembro` y `permiso` las escribe **sólo** su consumidor del buzón, con lo que
> este sistema publica. Un runbook que mande a tocar la tabla de `rentas` para devolverle el
> acceso a alguien produce exactamente lo que la **regla 12** existe para impedir: dos
> sistemas escribiendo la misma tabla de permisos en dos bases no dan un error, dan **dos
> respuestas** a «quién puede hacer esto» — y la de `rentas` la pisa el siguiente evento del
> buzón, así que el acceso «devuelto» desaparece solo, sin que nada se ponga rojo.
>
> El documento original tampoco cubría lo que hoy hace falta: se escribió cuando lo único
> que podía faltarle a alguien era la **clave**, porque había un solo sistema y una sola
> base. Hoy faltan tres cosas distintas en tres sitios distintos.

## Síntoma

Una de estas cuatro, y **las cuatro se reportan igual**: «no puedo entrar» o «me sale un
error en todo».

1. La pantalla de acceso no lo deja pasar, o un guion recibe `invalid_grant`.
2. Entra al sistema y **toda** petición contesta 403.
3. Entra y sólo **algunas** pantallas contestan 403.
4. Entra, contesta 403 «no está dada de alta en este sistema»… **y su fila está en la
   tabla**, delante de quien mira.

## Los tres sitios donde puede faltar algo, y por qué no se confunden

Una persona necesita **tres** cosas, y viven en tres sitios. Ninguno de los tres sabe del
otro, y ese es el motivo por el que un solo síntoma tiene tres causas:

| Qué | Dónde vive | Qué lo une | Quién lo escribe |
|---|---|---|---|
| La **identidad** (puede probar quién es) | Keycloak, realm `kamayuk` | `username` | `reconciliar-identidades.sh`, desde `municipalidades/<ubigeo>.json` (ADR-0012) |
| La **ficha** (este sistema lo conoce) | Tabla `usuario` de la base **`identidad`** | `usuario.cuenta` = `preferred_username` del token | **La API de `identidad`**, bajo `/identidad/api/v1/seguridad` |
| El **permiso** (puede hacer algo) | Tablas `grupo`, `miembro`, `permiso` de la misma base | `acceso.(sistema, codigo)` | La misma API |

**Aquí no se guarda ninguna contraseña** (ADR-0005, ADR-0039 §«No toca»): este sistema no
autentica, valida tokens. Y la copia que cada satélite consulta para autorizar **no se
escribe a mano en ninguna parte**: sale del buzón de este sistema
([ADR-0028](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0028-el-tenant-no-cruza-por-http.md)
§3).

## Precondiciones

1. **`kubectl` al ambiente**, con permiso de lectura sobre `Secret` y de `exec` sobre los
   `Deployment` de la plataforma. Desde fuera del nodo va por el túnel SSH al API — en la
   medición del 2026-09-12 eso era `kubectl --server=https://127.0.0.1:6446 …`, y el puerto
   local lo decide el túnel, no este documento.
2. **La `cuenta` de la persona**: su `username` en el realm, que es el mismo `usuario.cuenta`
   de la ficha. Si esas dos cadenas no son idénticas, ya está el fallo.
3. **Para los pasos 4 y 5, un token de una persona con privilegios de administración.** En
   `prod` conseguirlo no es trivial hoy, y por qué está en «Si no sale bien».

### Dos namespaces que se llaman casi igual, y confundirlos cuesta media hora

**En la plataforma `identidad` significa Keycloak**, así que este sistema se despliega con
otro nombre. Medido el 2026-09-12 en `prod`:

| Pieza | Namespace | Objeto |
|---|---|---|
| **Keycloak** — el emisor | `kamayuk-<amb>` | `deploy/kamayuk-<amb>-identidad`, ruta `/keycloak` |
| **El motor** — las cinco bases | `kamayuk-<amb>` | `deploy/kamayuk-<amb>-postgres`, contenedor `postgres` |
| **El sistema `identidad`** — esta API | `kamayuk-identidad-<amb>` | `deploy/kamayuk-identidad-web`, ruta `/identidad` |

`kubectl -n kamayuk-prod get deploy kamayuk-identidad-web` contesta `NotFound`, y el mensaje
no dice que el namespace sea otro.

## Pasos

### 1. Separar los casos, que es todo el trabajo

Fija las variables una vez:

```bash
NS=kamayuk-<amb>                    # la plataforma: Keycloak y el motor
DOMINIO=<dominio-publico>           # en prod: vmd206041.contaboserver.net
CUENTA=<la-cuenta-de-la-persona>
```

**a · ¿Existe la identidad, y en qué estado?** Se lee de la base de Keycloak, que es una
lectura y no necesita sesión de administración:

```bash
kubectl -n $NS exec deploy/$NS-postgres -c postgres -- psql -U postgres -d keycloak -c "
  select u.username, u.enabled, u.email_verified,
         (select string_agg(ra.required_action, ',')
            from user_required_action ra where ra.user_id = u.id) as acciones_pendientes,
         (select count(*) from credential c where c.user_id = u.id) as credenciales,
         (select a.value from user_attribute a
            where a.user_id = u.id and a.name = 'municipalidad_id') as municipalidad_id
    from user_entity u join realm r on r.id = u.realm_id
   where r.name = 'kamayuk' and u.username = '$CUENTA';"
```

La misma consulta **sin** el filtro de `username`, medida en `prod` el 2026-09-12 — son las
cinco cuentas que hay:

```
                     username                      | enabled | acciones_pendientes | credenciales | municipalidad_id
---------------------------------------------------+---------+---------------------+--------------+------------------
 administrador                                     | t       | UPDATE_PASSWORD     |            1 | 1
 service-account-kamayuk-caja-servicio-200105      | t       |                     |            0 | 1
 service-account-kamayuk-catastro-servicio-200105  | t       |                     |            0 | 1
 service-account-kamayuk-normativa-servicio-200105 | t       |                     |            0 | 1
 service-account-kamayuk-rentas-servicio-200105    | t       |                     |            0 | 1
```

**b · ¿Existe la ficha, y qué se le concedió?** Las cuatro tablas tienen RLS con
`FORCE`, así que **hay que fijar el inquilino en la misma sesión** — el `-c` de `psql` abre
una sesión por invocación, de modo que el `SET` y la consulta van en el **mismo** `-c`:

```bash
ID_MUNI=$(kubectl -n $NS exec deploy/$NS-postgres -c postgres -- \
  psql -U kamayuk_owner -d identidad -tAc "select id from municipalidad where ubigeo = '<ubigeo>';")

kubectl -n $NS exec deploy/$NS-postgres -c postgres -- psql -U kamayuk_owner -d identidad -c "
  SET app.municipalidad_id = '$ID_MUNI';
  select u.id, u.cuenta, u.habilitado, u.vigencia_desde, u.vigencia_hasta
    from usuario u where u.cuenta = '$CUENTA';"

kubectl -n $NS exec deploy/$NS-postgres -c postgres -- psql -U kamayuk_owner -d identidad -c "
  SET app.municipalidad_id = '$ID_MUNI';
  select g.nombre, m.activo as afiliado, g.habilitado as grupo_activo
    from miembro m join grupo g on g.id = m.grupo_id
                   join usuario u on u.id = m.usuario_id
   where u.cuenta = '$CUENTA';"
```

La primera consulta —la de `municipalidad`— **no** lleva `SET`, y no es un descuido: esa
tabla es el registro de inquilinos y su política es `USING (true)`, medido. Las otras
trece sí.

**Olvidar el `SET` no devuelve cero filas: revienta.** Medido:
`ERROR: unrecognized configuration parameter "app.municipalidad_id"`. Es la forma buena de
fallar —la política lo exige y no lo supone (hallazgo 1 de
[los cinco hallazgos de RLS](../../40-datos/hallazgos-de-rls.md))—, y conviene conocerla
porque el error **no menciona el RLS ni la tabla**.

**c · ¿Qué contesta el sistema de verdad?** Con el token de la persona, contra la API:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  "https://$DOMINIO/identidad/api/v1/seguridad/usuarios"
```

### 2. El árbol de decisión, con el literal exacto de cada rama

Estos cuerpos están **medidos contra `prod` el 2026-09-12**, no transcritos del código:

| Lo que se ve | Qué pasa de verdad | Va al paso |
|---|---|---|
| `{"error":"invalid_grant","error_description":"Account is not fully set up"}` | **La clave coincidió.** Lo que falta es que la persona la cambie: tiene `UPDATE_PASSWORD` pendiente | **3** |
| `{"error":"invalid_grant","error_description":"Invalid user credentials"}` | La clave **no** es esa, **o** la cuenta no existe en el realm. Keycloak no los distingue a propósito | **3** |
| `401` `NO_AUTENTICADO` — «La peticion no trae un token valido» | No hay token, o caducó, o es de otro emisor | — |
| `403` `SIN_PRIVILEGIO` — «La cuenta «…» **no esta dada de alta en este sistema**…» | El token es válido y **no hay fila en `usuario`** | **4** |
| `403` `SIN_PRIVILEGIO` — «**No tiene el privilegio** LECTURA sobre usuarios» | Hay ficha; falta el permiso | **5** |
| `403` `SIN_MUNICIPALIDAD` — «El token no identifica una municipalidad» | Al token le falta el claim: el atributo no está, o el `scope` no lo trae | **6** |

**La primera fila es la que este runbook existe para decir, y es la que el documento
original tenía al revés.** `invalid_grant` es el mismo `error` en los dos primeros casos:
lo único que los separa es el `error_description`. Un operador que lea sólo `invalid_grant`
concluye «clave incorrecta» y **rota la clave** — y rotarla no arregla nada, porque la clave
ya era buena: lo que estorba es la acción pendiente. Peor: al rotarla con `--temporary`
vuelve a poner `UPDATE_PASSWORD`, o sea que **reproduce el síntoma que venía a quitar**.

> **Las dos primeras filas se midieron una contra otra, con la misma cuenta.** Con la clave
> real de `administrador` —leída del `Secret`, nunca impresa— la respuesta fue
> `Account is not fully set up`; con una cadena cualquiera en su lugar,
> `Invalid user credentials`. Y con una cuenta que no existe, **también**
> `Invalid user credentials`: el mensaje no delata qué usuarios hay, que es correcto.

### 3. Caso A — la clave: está pendiente de cambio, o no la tiene

La identidad la escribe `reconciliar-identidades.sh`, y **crea sin credenciales y con
`UPDATE_PASSWORD` pendiente** (ADR-0012): la persona elige su clave con un enlace de un solo
uso. Con esa forma de nacer, el `grant_type=password` **no funciona hasta que la persona
entra una vez por el navegador**, y eso no es una avería.

**En un ambiente sin relay hay una excepción, y es la que explica lo que se ve en `prod`.**
Con `SIN_CORREO=1` y una clave en `KC_CLAVE_INICIAL` —el `Secret`
`kamayuk-<amb>-keycloak/clave-del-administrador`—, ese guion le fija esa clave **temporal**
en vez de dejarlo sin nada, para que el ambiente no nazca con su administrador inalcanzable.
Es [`infrastructure`#77](https://github.com/hneyra/infrastructure/issues/77), y por eso la
cuenta `administrador` de `prod` aparece con `credenciales = 1` **y** `UPDATE_PASSWORD`
pendiente: tiene clave y no ha entrado todavía.

**El camino normal es el navegador, y en `prod` es el único que funciona hoy.** La persona
entra por la interfaz (`https://<dominio>/rentas/` — medido, **200**), Keycloak le exige la
clave nueva en el mismo formulario de acceso y a partir de ahí entra. No hace falta tocar
nada.

Si además hay que darle una clave inicial porque no tiene ninguna —`credenciales` en 0 y sin
acción pendiente—, eso **escribe en Keycloak** y se hace con la consola o con `kcadm`, no
desde aquí: está en
[Abrir la consola de administración de Keycloak](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/abrir-la-consola-de-keycloak.md).
Dos avisos medidos antes de ir:

- **`--temporary` no es un detalle de gusto.** Con la clave temporal el `password grant` deja
  de funcionar y contesta lo de la fila 1. Para una **persona** es lo correcto; para un arnés
  o un guion, la clave va permanente.
- **El reenvío del enlace por correo —que el documento original daba como «el camino
  normal»— hoy no existe en `prod`.** Medido: el `Job` del realm corre con `SIN_CORREO=1`, y
  el realm `kamayuk` **no tiene ni un atributo `smtp*`**. `execute-actions-email` no tiene a
  dónde entregar. `resetPasswordAllowed` está en `true`, así que el enlace «¿Olvidó su
  contraseña?» **aparece en la pantalla y no entrega nada**, que es la peor combinación de
  las tres.

### 4. Caso B — la ficha: la cuenta no está dada de alta en este sistema

El literal que produce el guardia:

```
403  codigo: SIN_PRIVILEGIO
"La cuenta «jperez» no esta dada de alta en este sistema. No es que le falte un privilegio:
 no tiene ninguna ficha aqui, y el alta no se hace desde este sistema — la administracion de
 usuarios, grupos y permisos vive en rentas."
```

> **La segunda mitad de ese mensaje es FALSA desde la etapa 4 de ADR-0039, y hay que saberlo
> antes de seguirla.** La administración **vive aquí**. El texto está en `GuardiaDeAcceso`,
> que es una pieza de `plataforma` que los cinco sistemas comparten byte a byte, así que
> arreglarlo en una copia sumaría una divergencia: se corrige en la pieza compartida
> (`kamayuk-lib`,
> [ADR-0038](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0038-el-corte-entre-el-producto-y-el-suelo.md)).
> Queda dicho en el javadoc de `EventosController`.

**Se da de alta por la API, y no por SQL.** Un `INSERT` a mano deja la fila aquí y **no emite
el evento**, así que los cuatro satélites no se enteran: la persona entra a `identidad` y
sigue con 403 en `rentas`, `catastro`, `normativa` y `caja` — y nada se pone rojo. La
emisión ocurre **dentro de la misma transacción** que la escritura, y eso sólo pasa si se
escribe por el caso de uso.

```bash
curl -s -X POST "https://$DOMINIO/identidad/api/v1/seguridad/usuarios" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "cuenta": "'"$CUENTA"'",
        "nombre": "Jorge Perez",
        "correo": "jperez@catacaos.gob.pe",
        "observacion": "Se repone la ficha: entraba y recibia 403 en todo"
      }'
```

- **`cuenta` tiene que ser idéntica al `username` del realm.** Es lo único que une las dos
  mitades. Una ficha cuyo nombre no coincide es una fila que ningún token nombra.
- **No hay campo de clave, y es deliberado**: aquí no se guarda ninguna.
- **La `observacion` es obligatoria y de 5 caracteres como mínimo** (regla 10): sin ella es
  **422**. No es burocracia — es lo que hace que la auditoría pueda decir *por qué* alguien
  tuvo un acceso, que es lo único que no se puede reconstruir después.
- Cuenta repetida → **409**.

Y afiliarlo a su grupo, que es de donde salen casi todos los permisos:

```bash
curl -s -X POST "https://$DOMINIO/identidad/api/v1/seguridad/grupos/<idGrupo>/miembros" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"usuarioId": <idUsuario>, "activo": true, "observacion": "Se reincorpora a la ventanilla"}'
```

**No hay `DELETE`**: `activo: false` desafilia y **deja la fila** (regla 4). Un acceso no se
borra, se revoca — si se borrara, la auditoría no podría decir quién lo tuvo.

### 5. Caso C — el permiso: tiene ficha y le falta el privilegio

```
403  codigo: SIN_PRIVILEGIO
"No tiene el privilegio LECTURA sobre usuarios"
```

Primero se mira **qué tiene de verdad**, que no es lo mismo que qué se le configuró:

```bash
# Lo EFECTIVO: lo que puede hacer, con `origen` diciendo si viene de un grupo o de una excepcion
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://$DOMINIO/identidad/api/v1/seguridad/usuarios/<idUsuario>/permisos"

# Lo CONFIGURADO sobre el usuario, con `surtenEfectoHoy`
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://$DOMINIO/identidad/api/v1/seguridad/usuarios/<idUsuario>/permisos/configurados"
```

**`surtenEfectoHoy` explica un 403 que si no parece una avería**: un permiso configurado con
una vigencia que ya pasó está ahí, se ve en la pantalla, y no autoriza.

Concederlo — lo normal es **al grupo**, no a la persona:

```bash
curl -s -X PUT "https://$DOMINIO/identidad/api/v1/seguridad/grupos/<idGrupo>/permisos" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "niveles": [
          { "sistema": "identidad", "acceso": "usuarios", "privilegios": ["LECTURA"] },
          { "sistema": "rentas",    "acceso": "accesos",  "privilegios": ["LECTURA","IMPRESION"] }
        ],
        "observacion": "La ventanilla consulta usuarios y las opciones de rentas"
      }'
```

Tres cosas que no se adivinan y que la API obliga a acertar:

- **`sistema` es obligatorio y no tiene valor por omisión** (422 enumerando los cinco). En
  esta base conviven los catálogos de los cinco y dos sistemas pueden nombrar igual dos
  opciones distintas: un `sistema` supuesto sería dar permiso sobre la opción de otro.
- **Es un `PUT`: reemplaza lo que hubiera para ese par.** `privilegios: []` es la **retirada
  explícita**; `privilegios` **ausente** es **422**. La diferencia es a propósito: quitarle
  todo a alguien tiene que ser algo que alguien escribió, no algo que se le olvidó mandar.
- **La guarda del último administrador cuenta por el par `(sistema, acceso)`**: retirar el
  último que puede administrar da **409**, y tener los siete privilegios sobre
  `rentas:permisos` **no** convierte a nadie en administrador de `identidad`.

Las opciones válidas salen del catálogo, no de la memoria:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://$DOMINIO/identidad/api/v1/seguridad/accesos?sistema=identidad&tamano=500&ordenarPor=codigo"
```

Las 21 operaciones, con su cuerpo y su código de error, están en
[`despliegue/pruebas-e2e/10-seguridad.http`](../../../despliegue/pruebas-e2e/10-seguridad.http).

### 6. El cuarto caso: el claim `municipalidad_id` no vale lo que la base asignó

**Es el peor de leer, porque el sistema contesta «la cuenta no está dada de alta» con la fila
delante.** No es un fallo del alta: el `SET LOCAL` fija el inquilino con lo que trae el
token, el RLS esconde todas las filas del inquilino de verdad, y el guardia concluye —bien,
desde donde mira— que esa cuenta no existe. Es
[`infrastructure`#73](https://github.com/hneyra/infrastructure/issues/73).

Se mide comparando dos números:

```bash
# 1) lo que la base asigno
kubectl -n $NS exec deploy/$NS-postgres -c postgres -- \
  psql -U kamayuk_owner -d identidad -c 'select id, ubigeo, nombre from municipalidad order by ubigeo;'

# 2) lo que el token lleva
printf %s "$TOKEN" | cut -d. -f2 \
  | python3 -c 'import sys,base64,json; d=json.loads(base64.urlsafe_b64decode(sys.stdin.read()+"===")); print({k:d.get(k) for k in ("preferred_username","municipalidad_id","azp","iss")})'
```

**Tienen que ser el mismo número.** El síntoma con el mismo dato, medido el 2026-09-12 sobre
la misma tabla de `prod`:

```
SET app.municipalidad_id='1';       select count(*) from usuario;   ->  5
SET app.municipalidad_id='200105';  select count(*) from usuario;   ->  0
```

Cinco filas y cero filas, sin que nada cambie en la tabla. El `200105` es el **ubigeo**, y
era uno de los tres valores con que ese claim se escribía.

**Hoy en `prod` están alineados**, medido: `municipalidad.id = 1` y las cinco cuentas del
realm llevan `municipalidad_id = 1`. La salida 1 de #73 cerró la mitad de este sistema —el
`id` se **declara** y ya no lo asigna la secuencia—; la otra mitad es de `infrastructure`, y
lo que la repara cuando se desalinea **no vive aquí**: es
`reconciliar-identidades.sh`. No se arregla con un `UPDATE` a `usuario`, porque ese `id` es
el inquilino del que cuelga el RLS de las **catorce** tablas de esta base —medidas—:
cambiarlo deja huérfana cada fila **y sin un solo error**, porque la base haría exactamente
lo que se le pide.

## Cómo se comprueba que terminó bien

**No basta con que el comando saliera sin error, y no basta con mirar la pantalla.** Son
tres, y la tercera es la que este sistema añade.

**1 · La persona consigue un token, y el token trae lo que hace falta.**

```bash
printf %s "$TOKEN" | cut -d. -f2 \
  | python3 -c 'import sys,base64,json; d=json.loads(base64.urlsafe_b64decode(sys.stdin.read()+"===")); print(d.get("preferred_username"), d.get("municipalidad_id"))'
```

Tiene que imprimir su cuenta y el `id` de su municipalidad. `None` en el segundo es
`SIN_MUNICIPALIDAD` esperando a pasar.

**2 · La API contesta 200 donde antes contestaba 403.** Contra la operación concreta que
fallaba, no contra cualquiera:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  "https://$DOMINIO/identidad/api/v1/seguridad/usuarios"
```

**3 · El cambio salió por el buzón, o los otros cuatro sistemas no se han enterado.** Ésta
es la comprobación que no existía cuando había un solo sistema, y la única que distingue
«arreglado aquí» de «arreglado en el producto»:

```bash
kubectl -n $NS exec deploy/$NS-postgres -c postgres -- psql -U kamayuk_owner -d identidad -c "
  SET app.municipalidad_id = '$ID_MUNI';
  select tipo, sujeto_id, creado_en from identidad_evento order by id desc limit 5;"

kubectl -n $NS exec deploy/$NS-postgres -c postgres -- psql -U kamayuk_owner -d identidad -c "
  SET app.municipalidad_id = '$ID_MUNI';
  select a.consumidor, count(*) as acusados,
         (select count(*) from identidad_evento) - count(*) as le_quedan
    from identidad_evento_acuse a group by a.consumidor order by a.consumidor;"
```

El primero tiene que traer el `USUARIO_DADO_DE_ALTA`, `MIEMBRO_AFILIADO` o `PERMISO_FIJADO`
que se acaba de escribir. El segundo dice si los cuatro lo están aplicando. Medido en `prod`
el 2026-09-12, con el buzón corriendo:

```
 consumidor | acusados | le_quedan
------------+----------+-----------
 caja       |      333 |         0
 catastro   |      333 |         0
 normativa  |      333 |         0
 rentas     |      329 |         4
```

Un consumidor cuyo `le_quedan` **no baja** entre dos medidas es la avería que hereda este
diseño: el evento está escrito, nadie lo aplica, y la copia de ese sistema se queda vieja
**sin que nada se ponga rojo** (ADR-0039 §«Lo que cuesta», punto 4). Eso no se arregla en
este runbook.

**Y hay una ventana, aunque todo esté bien.** La copia local es eventualmente consistente:
entre conceder aquí y que el permiso valga en los cuatro pasa un rato, medido y escrito en
[`identidad-4-la-ventana-de-la-copia-local.md`](https://github.com/hneyra/infrastructure/blob/main/docs/00-gobierno/identidad-4-la-ventana-de-la-copia-local.md)
— **~2 min 36 s típico, ~5 min 10 s en el peor caso**. Quien reporta «sigue sin funcionar»
treinta segundos después no está viendo un fallo.

## Si no sale bien

### No hay manera de conseguir un token de una persona para usar la API

**Es el estado de `prod` hoy, y es lo primero con lo que se choca.** Medido el 2026-09-12
contra el realm `kamayuk`:

| Cliente | Qué contesta a `grant_type=password` | Por qué |
|---|---|---|
| `kamayuk-backoffice` | `{"error":"unauthorized_client","error_description":"Client not allowed for direct access grants"}` | Es el del navegador: código de autorización con PKCE, y `directAccessGrants` **desactivado a propósito** |
| `kamayuk-verificacion` | `{"error":"invalid_client","error_description":"Invalid client or Invalid client credentials"}` | **No existe en el realm de `prod`**, aunque `despliegue/identidad/realm-kamayuk.json` lo declara: el realm se importó antes y `--import-realm` sólo importa la primera vez |
| `admin-cli` | **llega a evaluar la clave** — medido: con `administrador` contesta `Account is not fully set up`, no `unauthorized_client` | Es el cliente de fábrica que Keycloak deja en todos los realms, público y con `directAccessGrants` |

Así que hay **dos** caminos, y ninguno es «rotar la clave»:

1. **El navegador** (el bueno para una persona): entrar por la interfaz y, si hace falta el
   token para un `curl`, tomarlo de la sesión. El endpoint de autorización **exige PKCE** —
   medido: una URL hecha a mano sin `code_challenge_method` vuelve con
   `error=invalid_request&error_description=Missing+parameter:+code_challenge_method`—, así
   que no se construye a mano.
2. **`admin-cli` para diagnosticar**, con la clave ya permanente. Este backend valida
   **emisor** y no audiencia (`JwtValidators.createDefaultWithIssuer`), así que un token
   emitido por ese cliente en el realm `kamayuk` lo acepta. **Es para comprobar, no para
   trabajar**: el cliente propio de los arneses es `kamayuk-verificacion`, y que no esté en
   `prod` es lo que habría que reponer.

### `kcadm` dice `Invalid user credentials [invalid_grant]` con la clave del `Secret`

Medido el 2026-09-12: **la clave del `admin` del realm `master` ya no es la que guarda
`kamayuk-prod-keycloak/clave-administrador`**. La credencial del `admin` en la base de
Keycloak está fechada el **2026-09-12 08:17 UTC**, muy posterior al `Secret`
(2026-09-11 20:44) y al primer arranque del pod (21:45) — y en el mismo realm hay un segundo
administrador creado a las 08:29. O sea: se reescribió **fuera del despliegue**, y
**`KC_BOOTSTRAP_ADMIN_PASSWORD` sólo se aplica en el primer arranque contra una base
vacía**, así que el `Secret` no vuelve a alinearla nunca.

El síntoma es idéntico a «me equivoqué de clave», y hay tres en ese `Secret` que se llaman
casi igual: la tabla que las separa está en
[Abrir la consola de administración de Keycloak](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/abrir-la-consola-de-keycloak.md)
§1. Lo que **no** depende de esa clave, y por eso este runbook diagnostica sin ella, son las
lecturas de las dos bases.

### Le diste de alta la ficha y sigue con 403 en `rentas` (o en `catastro`, o…)

Dos causas, y se separan mirando el buzón (§«Cómo se comprueba» punto 3):

- **el evento no está**: la fila se escribió con SQL directo en vez de por la API, así que
  nadie la publicó. Se repara escribiéndola otra vez **por la API**;
- **el evento está y `le_quedan` no baja**: el consumidor de ese sistema no corre. Es un
  problema de ese despliegue, no de aquí.

### Le concediste el permiso y sigue con 403 en la misma pantalla

Por orden de frecuencia: el `sistema` del `PUT` no era el de la pantalla que falla (el mismo
código existe en dos catálogos); la vigencia del permiso, del grupo o del usuario no está
corriendo (`surtenEfectoHoy`); el `PUT` reemplazó lo que había para ese par y se llevó por
delante privilegios que no se querían tocar; o todavía no pasó la ventana de la copia local.

### `ERROR: unrecognized configuration parameter "app.municipalidad_id"`

Falta el `SET app.municipalidad_id` **en la misma sesión** que la consulta. Cada `-c` de
`psql` es una sesión distinta, así que van juntos en un solo `-c`. No se sustituye por
`SET SESSION`, nunca (regla 3): eso sobrevive al retorno de la conexión al pool y contamina
la petición de otra municipalidad.

## Lo que NO se hace, y por qué

- **No se escribe `usuario`, `grupo`, `miembro` ni `permiso` con SQL, ni aquí ni —sobre
  todo— en los otros cuatro sistemas.** En los satélites es la **regla 12**: su única fuente
  es el consumidor del buzón, y lo que se escriba a mano lo pisa el siguiente evento, así
  que el acceso «devuelto» desaparece solo. Aquí, un `INSERT` directo salta el caso de uso y
  con él la observación, la auditoría y **la emisión del evento**, que viaja en la misma
  transacción: la fila queda y los cuatro satélites no se enteran.
- **No se borra nada** (regla 4). Se da de baja, se desafilia o se revoca. Un acceso
  borrado es una auditoría que ya no puede decir quién lo tuvo.
- **No se guarda ninguna clave en esta base.** Si el remedio pasa por una contraseña, el
  sitio es Keycloak.
- **No se arregla el mensaje del guardia desde este repositorio** (§4): es de una pieza
  compartida byte a byte por los cinco.

## Estado del ensayo

**Diagnóstico ensayado entero contra `prod`** (`vmd206041`, 2026-09-12), sólo lecturas:

- los cuerpos de error del emisor, pedidos de verdad: `Account is not fully set up` con la
  clave real del `Secret` (leída y nunca impresa), `Invalid user credentials` con una clave
  cualquiera y con una cuenta inexistente, `unauthorized_client` por `kamayuk-backoffice`,
  `invalid_client` por `kamayuk-verificacion`;
- **401 `NO_AUTENTICADO`** sin token y **403 `SIN_PRIVILEGIO` «No tiene el privilegio LECTURA
  sobre usuarios»** con un token real —el de la cuenta de servicio de `rentas`, que tiene
  ficha y sólo el acceso `eventos`— contra `/seguridad/usuarios`;
- las lecturas de las dos bases: las cinco cuentas del realm con sus acciones pendientes, las
  cinco fichas, los dos grupos, los 162 permisos, el catálogo, los 333 eventos y los acuses
  por consumidor;
- el **RLS**: 5 filas con el inquilino bueno, **0** con el ubigeo, y el error exacto sin
  fijarlo;
- que la consola de Keycloak sigue dando **404** desde internet y la interfaz **200**.

**No ensayado, con su motivo:**

- **Ningún remedio.** Los cuatro escriben —la clave en Keycloak, la ficha, la afiliación y
  el permiso por la API— y este trabajo era de sólo lectura. Lo que sí está ejercido en CI,
  contra la pila levantada desde cero, son las 21 operaciones con sus 55 casos:
  [`despliegue/pruebas-e2e/`](../../../despliegue/pruebas-e2e/README.md).
- **El caso B —«la cuenta no está dada de alta»— no se pudo producir.** Las cinco cuentas del
  realm de `prod` tienen ficha, y fabricar una que no la tenga es crear un usuario. El
  literal del mensaje está tomado de `GuardiaDeAcceso` (`kamayuk-identidad-plataforma`), y
  está medido en el registro de la etapa 4, donde ese 403 es justo el defecto que se cerró.
- **El caso del claim desalineado no se pudo producir por HTTP**: exigiría un token con otro
  `municipalidad_id`, o sea tocar el atributo. Lo que sí se midió es su **mecanismo entero**,
  con el `SET` del inquilino sobre la tabla real — que es donde ocurre.
- **No se consiguió ningún token de una PERSONA, y por eso las llamadas de los pasos 4 y 5
  no se ejercieron con uno.** En `prod` no lo hay hoy: `administrador` tiene la clave
  pendiente de cambio y el realm no trae `kamayuk-verificacion`. Lo que sí se ejerció contra
  la API real es el token de una **cuenta de servicio**, que recorre exactamente la misma
  cadena —emisor, `SET LOCAL`, RLS y guardia— y produce los dos 403 de la tabla.
- **No se ejerció ninguna pantalla.** Medido: hoy **no hay interfaz de administración** de
  este sistema; la sección «Usuarios y permisos» del árbol de `rentas` no tiene pantallas
  detrás. Todo lo de este runbook es por API.

**Dos observaciones medidas que no son de este runbook y conviene no redescubrir:** el
`prod` del 2026-09-12 corre `ghcr.io/hneyra/kamayuk-identidad:226ec6ff…`, cuyo catálogo unido
trae **161** opciones con `rentas: 134` — la cifra anterior a la etapa 4, que en el
repositorio es **157** con `rentas: 130`; y `rentas` llevaba **4 eventos sin acusar** en el
momento de la medida, con los otros tres consumidores al día.

## Documentos relacionados

[ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md)
(por qué esto vive aquí y no en `rentas`) ·
[ADR-0012](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0012-usuarios-y-grupos-declarativos.md)
(el alta declarativa y el enlace por correo) ·
[ADR-0005](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0005-identidad-y-acceso.md)
(el claim `municipalidad_id`) ·
[ADR-0002](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0002-estrategia-multi-tenant.md)
y [los cinco hallazgos de RLS](../../40-datos/hallazgos-de-rls.md) (el aislamiento) ·
[ADR-0028](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0028-el-tenant-no-cruza-por-http.md)
§3 (el buzón) ·
[ADR-0013](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0013-permisos-de-la-sesion.md)
de `rentas` (la matriz se vuelve a pedir en cada renovación) ·
[Abrir la consola de administración de Keycloak](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/abrir-la-consola-de-keycloak.md) ·
[Keycloak no responde](https://github.com/hneyra/infrastructure/blob/main/docs/B0-operacion/runbooks/keycloak-no-responde.md) ·
[Las 21 operaciones de la API](../../../despliegue/pruebas-e2e/10-seguridad.http) y
[los dos tokens](../../../despliegue/pruebas-e2e/00-token.http) ·
[D0 — Desarrollo](../../D0-desarrollo/README.md)
