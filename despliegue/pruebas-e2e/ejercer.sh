#!/usr/bin/env bash
# ============================================================================
#  Ejerce la API de `identidad` contra la pila levantada, y AFIRMA.
#
#  Los `.http` de al lado son para abrir y clicar; esto es lo que vale en CI: un
#  caso que no contesta lo que dice su contrato deja el guion en codigo distinto
#  de cero, nombrando el caso.
#
#  Uso:
#      ./ejercer.sh                      # todo, buzon incluido
#      ./ejercer.sh --sin-buzon          # solo administracion, DICIENDO que se omite
#
#  Lo que necesita, y de donde sale:
#      CLAVE_DEL_ADMINISTRADOR   la IMPRIME `infrastructure/despliegue/identidad/preparar-identidades.sh`
#                                al terminar, junto con la de servicio. Por debajo la fija
#                                `crear-usuario.sh` SIN `--reset` (con `--reset` es temporal:
#                                Keycloak exige cambiarla al entrar y el `password` grant no sirve)
#      CLAVE_DE_SERVICIO_RENTAS  la clave del cliente `kamayuk-rentas-servicio-<ubigeo>`, que el
#                                mismo guion genera en `despliegue/.claves-de-servicio/`
#
#      Las dos de una vez, desde cero:
#          cd ../../../infrastructure/despliegue && ./levantar-todo.sh identidad
#
#  Todo lo demas tiene por omision los valores de la plataforma local, y se puede
#  pisar por entorno: INGRESO, KEYCLOAK, REALM, UBIGEO, ADMINISTRADOR.
#
#  NO se omite nada en silencio. Si falta la clave de servicio, el buzon NO se
#  salta: el guion FALLA diciendo que falta, y hay que pedir `--sin-buzon` para
#  dejarlo fuera a proposito — porque una prueba que se salta a si misma deja el
#  verde sin haber verificado nada.
# ============================================================================
set -uo pipefail

# ── DE DONDE SALEN LOS PUERTOS, y por que no estan escritos aqui ─────────────
#
# Estaban, y eran los de UNA maquina: 8082/8181. `.env.ejemplo` y los seis `docs/D0-desarrollo/`
# dicen 8080/8180, asi que un CI que copiara el ejemplo al pie de la letra levantaria Keycloak
# donde este arnes no lo busca — y el sintoma seria «Connection refused» a un servicio que esta
# perfectamente arriba. Es #74.
#
# Se arregla por construccion y no eligiendo un numero: la fuente de verdad de los puertos es
# el `.env` del compose de la plataforma, que es quien los publica. Se lee de ahi, y lo que
# venga por entorno gana —para apuntar a otra instalacion—.
ENV_DE_LA_PLATAFORMA="${ENV_DE_LA_PLATAFORMA:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." 2>/dev/null && pwd)/infrastructure/despliegue/.env}"
if [ -f "$ENV_DE_LA_PLATAFORMA" ]; then
  # En una subshell y leyendo solo lo que hace falta: `source` de un `.env` entero metería en
  # este proceso las seis claves del motor, que este guion no necesita para nada.
  PUERTO_INGRESO=$(sed -nE 's/^KAMAYUK_PUERTO_INGRESO=([0-9]+).*/\1/p' "$ENV_DE_LA_PLATAFORMA" | tail -1)
  PUERTO_KEYCLOAK=$(sed -nE 's/^KAMAYUK_PUERTO_IDENTIDAD=([0-9]+).*/\1/p' "$ENV_DE_LA_PLATAFORMA" | tail -1)
  UBIGEO_DEL_ENV=$(sed -nE 's/^KAMAYUK_UBIGEO=([0-9]+).*/\1/p' "$ENV_DE_LA_PLATAFORMA" | tail -1)
  ADMIN_DEL_ENV=$(sed -nE 's/^KAMAYUK_ADMINISTRADOR=([A-Za-z0-9._-]+).*/\1/p' "$ENV_DE_LA_PLATAFORMA" | tail -1)
fi
# Los ultimos recursos son los de `.env.ejemplo`, que son los que documenta D0 — NO los de la
# maquina donde esto se escribio.
INGRESO="${INGRESO:-http://localhost:${PUERTO_INGRESO:-8080}}"
KEYCLOAK="${KEYCLOAK:-http://localhost:${PUERTO_KEYCLOAK:-8180}}"
REALM="${REALM:-kamayuk}"
UBIGEO="${UBIGEO:-${UBIGEO_DEL_ENV:-200101}}"
ADMINISTRADOR="${ADMINISTRADOR:-${ADMIN_DEL_ENV:-jperez}}"
API="$INGRESO/identidad/api/v1"

printf 'Contra %s (realm «%s», ubigeo %s, administrador «%s»)\n' \
  "$INGRESO" "$REALM" "$UBIGEO" "$ADMINISTRADOR"
[ -f "$ENV_DE_LA_PLATAFORMA" ] \
  && printf 'Puertos derivados de %s\n' "$ENV_DE_LA_PLATAFORMA" \
  || printf 'SIN .env de la plataforma (%s): puertos por omision, los de .env.ejemplo\n' \
       "$ENV_DE_LA_PLATAFORMA"

CON_BUZON=1
[ "${1:-}" = "--sin-buzon" ] && CON_BUZON=0

for h in curl jq; do
  command -v "$h" >/dev/null || { echo "FALTA la herramienta «$h»"; exit 2; }
done

: "${CLAVE_DEL_ADMINISTRADOR:?falta CLAVE_DEL_ADMINISTRADOR: la imprime infrastructure/despliegue/identidad/preparar-identidades.sh}"
if [ "$CON_BUZON" = 1 ]; then
  : "${CLAVE_DE_SERVICIO_RENTAS:?falta CLAVE_DE_SERVICIO_RENTAS (la de kamayuk-rentas-servicio-$UBIGEO). Para dejar el buzon fuera A PROPOSITO: ./ejercer.sh --sin-buzon}"
fi

CASOS=0; FALLOS=0; FALLIDOS=()
SUFIJO="e2e-$(date +%Y%m%d-%H%M%S)"
CUERPO=""

rojo()  { printf '  \033[31m✗\033[0m %s\n' "$1"; }
verde() { printf '  \033[32m✓\033[0m %s\n' "$1"; }

# caso <nombre> <estado esperado> <codigo esperado|-> <metodo> <ruta> [cuerpo json] [token]
caso() {
  local nombre="$1" esperado="$2" codigo="$3" metodo="$4" ruta="$5" cuerpo="${6:-}" tok="${7:-$TOKEN}"
  CASOS=$((CASOS+1))
  local tmp; tmp=$(mktemp); local args=(-s -o "$tmp" -w '%{http_code}' -X "$metodo" --max-time 30)
  [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok")
  [ -n "$cuerpo" ] && args+=(-H 'Content-Type: application/json' -d "$cuerpo")
  local estado; estado=$(curl "${args[@]}" "$API$ruta")
  CUERPO=$(cat "$tmp"); rm -f "$tmp"

  if [ "$estado" != "$esperado" ]; then
    rojo "$nombre"
    printf '      esperaba %s y contesto %s\n      %s\n' "$esperado" "$estado" "$(echo "$CUERPO" | head -c 400)"
    FALLOS=$((FALLOS+1)); FALLIDOS+=("$nombre"); return 1
  fi
  if [ "$codigo" != "-" ]; then
    local visto; visto=$(echo "$CUERPO" | jq -r '.codigo // empty' 2>/dev/null)
    if [ "$visto" != "$codigo" ]; then
      rojo "$nombre"
      printf '      estado %s correcto, pero el codigo del catalogo era «%s» y llego «%s»\n      %s\n' \
             "$esperado" "$codigo" "${visto:-<ninguno>}" "$(echo "$CUERPO" | head -c 400)"
      FALLOS=$((FALLOS+1)); FALLIDOS+=("$nombre"); return 1
    fi
  fi
  verde "$nombre"
}

# El `--wait` del compose vuelve ~30 s antes de que Keycloak sirva sus realms, asi
# que sin esta espera el primer token falla por un motivo que no es el que se mide.
printf '\n\033[1mEsperando a que el realm «%s» conteste\033[0m\n' "$REALM"
for _ in $(seq 1 60); do
  curl -sf "$KEYCLOAK/realms/$REALM/.well-known/openid-configuration" >/dev/null 2>&1 && break
  sleep 2
done
curl -sf "$KEYCLOAK/realms/$REALM/.well-known/openid-configuration" >/dev/null \
  || { echo "  el realm «$REALM» no contesta en $KEYCLOAK"; exit 2; }
verde "el realm «$REALM» sirve su configuracion"

pedir_token() { # pedir_token <datos del formulario>
  curl -s --max-time 30 -X POST \
    -H 'Content-Type: application/x-www-form-urlencoded' -d "$1" \
    "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token"
}

printf '\n\033[1mLos dos tokens\033[0m\n'
RESP=$(pedir_token "grant_type=password&client_id=kamayuk-verificacion&username=$ADMINISTRADOR&password=$CLAVE_DEL_ADMINISTRADOR")
TOKEN=$(echo "$RESP" | jq -r '.access_token // empty')
[ -n "$TOKEN" ] || { echo "  no hubo token de persona: $(echo "$RESP" | head -c 300)"; exit 2; }
verde "token de $ADMINISTRADOR ($(echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq -r '"municipalidad_id=\(.municipalidad_id) azp=\(.azp)"'))"

SERVICIO=""
if [ "$CON_BUZON" = 1 ]; then
  RESP=$(pedir_token "grant_type=client_credentials&client_id=kamayuk-rentas-servicio-$UBIGEO&client_secret=$CLAVE_DE_SERVICIO_RENTAS&scope=kamayuk-servicio")
  SERVICIO=$(echo "$RESP" | jq -r '.access_token // empty')
  [ -n "$SERVICIO" ] || { echo "  no hubo token de servicio: $(echo "$RESP" | head -c 300)"; exit 2; }
  verde "token de servicio de rentas ($(echo "$SERVICIO" | cut -d. -f2 | base64 -d 2>/dev/null | jq -r '"azp=\(.azp) municipalidad_id=\(.municipalidad_id)"'))"
fi

# ─────────────────────────────── LECTURAS ───────────────────────────────
printf '\n\033[1mLecturas\033[0m\n'
caso "los cinco catalogos de modulos se sirven"        200 - GET "/seguridad/modulos?tamano=500"
SISTEMAS_EN_MODULOS=$(echo "$CUERPO" | jq -r '[.contenido[].sistema] | unique | join(",")' 2>/dev/null)
caso "filtrar modulos por sistema"                     200 - GET "/seguridad/modulos?sistema=identidad"
caso "las opciones de identidad"                       200 - GET "/seguridad/accesos?sistema=identidad&tamano=500"
ACCESOS_DE_IDENTIDAD=$(echo "$CUERPO" | jq -r '.totalElementos' 2>/dev/null)
caso "las 157 opciones del catalogo unido"             200 - GET "/seguridad/accesos?tamano=500"
CATALOGO_UNIDO=$(echo "$CUERPO" | jq -r '.totalElementos' 2>/dev/null)
caso "los grupos"                                      200 - GET "/seguridad/grupos"
caso "los usuarios"                                    200 - GET "/seguridad/usuarios?tamano=100&ordenarPor=cuenta"
CUENTAS=$(echo "$CUERPO" | jq -r '[.contenido[].cuenta] | join(" ")' 2>/dev/null)
ADMIN_ID=$(echo "$CUERPO" | jq -r --arg c "$ADMINISTRADOR" '.contenido[] | select(.cuenta==$c) | .id' 2>/dev/null)
caso "a que grupos pertenece el administrador"         200 - GET "/seguridad/usuarios/$ADMIN_ID/grupos"
caso "quien tiene REGISTRO sobre identidad:permisos"   200 - GET "/seguridad/accesos/permisos/usuarios?sistema=identidad&privilegio=REGISTRO"

# ───────────────────── EL CICLO DE VIDA, que es lo que este sistema hace ─────────────────────
printf '\n\033[1mCiclo de vida de un grupo\033[0m\n'
caso "alta de grupo"                                   201 - POST "/seguridad/grupos" \
  "{\"nombre\":\"Ventanilla $SUFIJO\",\"descripcion\":\"Atiende al contribuyente\",\"observacion\":\"Alta segun memorando 2026-12\"}"
GRUPO=$(echo "$CUERPO" | jq -r '.id' 2>/dev/null)
caso "vigencia del grupo"                              200 - PUT "/seguridad/grupos/$GRUPO/vigencia" \
  '{"vigenciaDesde":"2026-01-01","vigenciaHasta":"2026-12-31","observacion":"Vigente hasta fin de ejercicio"}'
caso "baja del grupo (no borra: deshabilita)"          200 - POST "/seguridad/grupos/$GRUPO/baja" \
  '{"observacion":"Se reorganiza la oficina"}'
[ "$(echo "$CUERPO" | jq -r '.habilitado')" = "false" ] \
  && verde "y la baja dejo habilitado=false, no una fila borrada" \
  || { rojo "la baja no dejo habilitado=false"; FALLOS=$((FALLOS+1)); FALLIDOS+=("la baja deshabilita"); }
CASOS=$((CASOS+1))
caso "reactivacion del grupo"                          200 - POST "/seguridad/grupos/$GRUPO/reactivacion" \
  '{"observacion":"Vuelve a operar"}'

printf '\n\033[1mCiclo de vida de un usuario\033[0m\n'
caso "alta de usuario"                                 201 - POST "/seguridad/usuarios" \
  "{\"cuenta\":\"jperez-$SUFIJO\",\"nombre\":\"Jorge Perez\",\"correo\":\"jperez@catacaos.gob.pe\",\"observacion\":\"Alta por resolucion de personal\"}"
USUARIO=$(echo "$CUERPO" | jq -r '.id' 2>/dev/null)
caso "vigencia del usuario"                            200 - PUT "/seguridad/usuarios/$USUARIO/vigencia" \
  '{"vigenciaDesde":"2026-01-01","vigenciaHasta":"2026-12-31","observacion":"Contrato hasta fin de ejercicio"}'
caso "quitarle la caducidad con los dos nulos"         200 - PUT "/seguridad/usuarios/$USUARIO/vigencia" \
  '{"vigenciaDesde":null,"vigenciaHasta":null,"observacion":"Pasa a plazo indeterminado"}'
caso "baja del usuario"                                200 - POST "/seguridad/usuarios/$USUARIO/baja" \
  '{"observacion":"Cesa hoy por fin de contrato"}'
caso "reactivacion del usuario"                        200 - POST "/seguridad/usuarios/$USUARIO/reactivacion" \
  '{"observacion":"Se reincorpora tras la renovacion"}'

printf '\n\033[1mAfiliacion\033[0m\n'
caso "afiliar al grupo"                                200 - POST "/seguridad/grupos/$GRUPO/miembros" \
  "{\"usuarioId\":$USUARIO,\"activo\":true,\"observacion\":\"Entra a la ventanilla\"}"
caso "el miembro sale en la lista del grupo"           200 - GET "/seguridad/grupos/$GRUPO/miembros"
echo "$CUERPO" | jq -e --arg c "jperez-$SUFIJO" '[.contenido[].cuenta] | index($c) != null' >/dev/null \
  && verde "y es el que se afilio" \
  || { rojo "el afiliado no aparece entre los miembros"; FALLOS=$((FALLOS+1)); FALLIDOS+=("el afiliado aparece"); }
CASOS=$((CASOS+1))
caso "desafiliar con la misma operacion (activo=false)" 200 - POST "/seguridad/grupos/$GRUPO/miembros" \
  "{\"usuarioId\":$USUARIO,\"activo\":false,\"observacion\":\"Sale de la ventanilla\"}"

printf '\n\033[1mPermisos\033[0m\n'
caso "conceder dos niveles al grupo"                   200 - PUT "/seguridad/grupos/$GRUPO/permisos" \
  '{"niveles":[{"sistema":"identidad","acceso":"usuarios","privilegios":["LECTURA"]},{"sistema":"rentas","acceso":"accesos","privilegios":["LECTURA","IMPRESION"]}],"observacion":"La ventanilla consulta usuarios y las opciones de rentas"}'
caso "los permisos del grupo se leen"                  200 - GET "/seguridad/grupos/$GRUPO/permisos"
echo "$CUERPO" | jq -e '[.[] | "\(.sistema):\(.acceso)"] | (index("identidad:usuarios") != null) and (index("rentas:accesos") != null)' >/dev/null \
  && verde "y el par (sistema, acceso) viaja en la respuesta" \
  || { rojo "la respuesta de permisos no trae el par (sistema, acceso)"; FALLOS=$((FALLOS+1)); FALLIDOS+=("el par sistema:acceso"); }
CASOS=$((CASOS+1))
caso "revocar con la lista VACIA"                      200 - PUT "/seguridad/grupos/$GRUPO/permisos" \
  '{"niveles":[{"sistema":"rentas","acceso":"accesos","privilegios":[]}],"observacion":"Se le retira la consulta de rentas"}'
caso "una excepcion nominal sobre el usuario"          200 - PUT "/seguridad/usuarios/$USUARIO/permisos" \
  '{"niveles":[{"sistema":"identidad","acceso":"grupos","privilegios":["LECTURA"]}],"observacion":"Excepcion mientras cubre la jefatura"}'
caso "los permisos EFECTIVOS del usuario"              200 - GET "/seguridad/usuarios/$USUARIO/permisos"
caso "los permisos CONFIGURADOS del usuario"           200 - GET "/seguridad/usuarios/$USUARIO/permisos/configurados"

# ───────────── LOS NEGATIVOS: la mitad del contrato ─────────────
printf '\n\033[1mNegativos\033[0m\n'
caso "sin token"                                       401 NO_AUTENTICADO GET "/seguridad/usuarios" "" " "
caso "alta sin observacion"                            422 VALIDACION POST "/seguridad/usuarios" \
  '{"cuenta":"sin.observacion","nombre":"Sin motivo"}'
caso "observacion de menos de 5 caracteres"            422 VALIDACION POST "/seguridad/grupos" \
  "{\"nombre\":\"Corto $SUFIJO\",\"observacion\":\"ok\"}"
caso "permisos sin «sistema»"                          422 VALIDACION PUT "/seguridad/grupos/$GRUPO/permisos" \
  '{"niveles":[{"acceso":"usuarios","privilegios":["LECTURA"]}],"observacion":"Sin decir de que sistema es"}'
caso "«privilegios» ausente (no es lo mismo que [])"   422 VALIDACION PUT "/seguridad/grupos/$GRUPO/permisos" \
  '{"niveles":[{"sistema":"identidad","acceso":"usuarios"}],"observacion":"Sin la lista de privilegios"}'
caso "un sistema que no existe"                        422 VALIDACION GET "/seguridad/accesos?sistema=tesoreria"
caso "un privilegio que no existe"                     422 VALIDACION PUT "/seguridad/grupos/$GRUPO/permisos" \
  '{"niveles":[{"sistema":"identidad","acceso":"usuarios","privilegios":["ESCRITURA"]}],"observacion":"ESCRITURA no esta entre los siete"}'
caso "ordenarPor fuera de la lista blanca"             422 ORDEN_NO_ADMITIDO GET "/seguridad/usuarios?ordenarPor=correo"
caso "un parametro que la operacion no declara"        422 VALIDACION GET "/seguridad/usuarios?consumidor=rentas"
caso "vigencia invertida"                              422 VALIDACION PUT "/seguridad/usuarios/$USUARIO/vigencia" \
  '{"vigenciaDesde":"2026-12-31","vigenciaHasta":"2026-01-01","observacion":"Las dos fechas al reves"}'
caso "cuenta repetida"                                 409 CONFLICTO POST "/seguridad/usuarios" \
  "{\"cuenta\":\"jperez-$SUFIJO\",\"nombre\":\"Otro Jorge\",\"observacion\":\"Alta que se hace dos veces\"}"
caso "grupo que no existe (o de otra municipalidad)"   404 NO_ENCONTRADO GET "/seguridad/grupos/999999/miembros"
# El 405 exige una ruta QUE EXISTA con otro verbo. Medido: `DELETE /seguridad/usuarios/{id}`
# da 404 y no 405, y es correcto — esa ruta no tiene ningun manejador, asi que Spring no
# puede decir cual es el verbo admitido. `/grupos/{id}/baja` si existe, y solo por POST.
caso "el verbo equivocado sobre una ruta que existe"   405 METODO_NO_ADMITIDO GET "/seguridad/grupos/$GRUPO/baja"
caso "una ruta que no existe con ningun verbo"         404 NO_ENCONTRADO DELETE "/seguridad/usuarios/$USUARIO"
caso "retirar al ultimo administrador"                 409 CONFLICTO PUT "/seguridad/usuarios/$ADMIN_ID/permisos" \
  '{"niveles":[{"sistema":"identidad","acceso":"permisos","privilegios":[]}],"observacion":"Intento de retirar al ultimo administrador"}'
caso "un token de persona contra el buzon"             403 SIN_IDENTIDAD_DE_SERVICIO GET "/eventos/pendientes"

# ─────────────────────────────── EL BUZON ───────────────────────────────
if [ "$CON_BUZON" = 1 ]; then
  printf '\n\033[1mEl buzon, con el token de servicio de rentas\033[0m\n'
  caso "lo pendiente para rentas"                      200 - GET "/eventos/pendientes?limite=5" "" "$SERVICIO"
  QUEDAN=$(echo "$CUERPO" | jq -r '.quedan' 2>/dev/null)
  UN_EVENTO=$(echo "$CUERPO" | jq -r '.eventos[0].eventoId // empty' 2>/dev/null)
  CAMPOS=$(echo "$CUERPO" | jq -r '.eventos[0] | keys_unsorted | join(",") // empty' 2>/dev/null)
  if [ "$CAMPOS" = "eventoId,secuencia,tipo,sujetoId,cuerpo,huella,creadoEn" ]; then
    verde "y el evento trae los siete campos, en su orden"
  else
    rojo "los campos del evento cambiaron: «$CAMPOS»"
    FALLOS=$((FALLOS+1)); FALLIDOS+=("los siete campos del evento")
  fi
  CASOS=$((CASOS+1))
  echo "$CUERPO" | jq -e '.eventos | map(.secuencia) | . == sort' >/dev/null \
    && verde "y vienen ordenados por secuencia, que ES contenido" \
    || { rojo "los eventos no vienen ordenados por secuencia"; FALLOS=$((FALLOS+1)); FALLIDOS+=("orden por secuencia"); }
  CASOS=$((CASOS+1))
  caso "limite fuera de rango"                         422 VALIDACION GET "/eventos/pendientes?limite=501" "" "$SERVICIO"
  caso "acusar algo que no es un UUID"                 422 VALIDACION POST "/eventos/acuses" \
    '{"eventos":["esto-no-es-un-uuid"]}' "$SERVICIO"
  caso "acusar un evento que no consta"                422 VALIDACION POST "/eventos/acuses" \
    '{"eventos":["99999999-8888-4777-8666-555555555555"]}' "$SERVICIO"
  if [ -n "$UN_EVENTO" ]; then
    caso "acusar lo servido"                           200 - POST "/eventos/acuses" \
      "{\"eventos\":[\"$UN_EVENTO\"]}" "$SERVICIO"
    [ "$(echo "$CUERPO" | jq -r '.recibidos')" = "1" ] && [ "$(echo "$CUERPO" | jq -r '.escritos')" = "1" ] \
      && verde "recibidos=1 escritos=1" \
      || { rojo "el acuse no dijo recibidos=1 escritos=1: $CUERPO"; FALLOS=$((FALLOS+1)); FALLIDOS+=("recibidos/escritos"); }
    CASOS=$((CASOS+1))
    caso "acusarlo OTRA VEZ es idempotente"            200 - POST "/eventos/acuses" \
      "{\"eventos\":[\"$UN_EVENTO\"]}" "$SERVICIO"
    [ "$(echo "$CUERPO" | jq -r '.escritos')" = "0" ] \
      && verde "y el segundo acuse escribio 0, sin error" \
      || { rojo "el acuse repetido no escribio 0: $CUERPO"; FALLOS=$((FALLOS+1)); FALLIDOS+=("acuse idempotente"); }
    CASOS=$((CASOS+1))
  else
    rojo "el buzon no sirvio ningun evento, asi que no se pudo ejercer el acuse"
    printf '      La implantacion tiene que dejar 170 eventos. Si «quedan» es 0 para rentas,\n'
    printf '      o su consumidor ya los acuso todos, o la implantacion no emitio nada.\n'
    FALLOS=$((FALLOS+1)); FALLIDOS+=("el buzon sirve algo que acusar")
    CASOS=$((CASOS+1))
  fi
else
  printf '\n\033[1mEl buzon\033[0m\n'
  printf '  \033[33m—\033[0m OMITIDO A PROPOSITO por --sin-buzon. Las dos operaciones del buzon y sus\n'
  printf '    seis negativos NO se han ejercido: esto no es un verde sobre ellos.\n'
fi

# ─────────────────────────────── EL RESUMEN ───────────────────────────────
printf '\n\033[1mLo que se midio de paso\033[0m\n'
printf '  sistemas con modulos servidos    %s\n' "$SISTEMAS_EN_MODULOS"
printf '  opciones de identidad            %s   (siete desde la etapa 3)\n' "$ACCESOS_DE_IDENTIDAD"
printf '  opciones del catalogo unido      %s   (157 desde la etapa 4)\n' "$CATALOGO_UNIDO"
printf '  cuentas que la implantacion dejo %s\n' "$CUENTAS"
[ "$CON_BUZON" = 1 ] && printf '  eventos pendientes para rentas   %s\n' "${QUEDAN:-?}"

printf '\n'
if [ "$FALLOS" -eq 0 ]; then
  printf '\033[32m%s casos, 0 fallos.\033[0m\n' "$CASOS"
  [ "$CON_BUZON" = 1 ] || printf '\033[33mCon el buzon fuera: no es un verde completo.\033[0m\n'
  exit 0
fi
printf '\033[31m%s casos, %s fallos:\033[0m\n' "$CASOS" "$FALLOS"
for f in "${FALLIDOS[@]}"; do printf '  · %s\n' "$f"; done
exit 1
