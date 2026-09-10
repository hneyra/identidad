#!/usr/bin/env node
// Deriva `catalogo-de-accesos/rentas.json` del catalogo del manual, que vive en el clon hermano.
//
// POR QUE SE DERIVA Y NO SE TRANSCRIBE
// ------------------------------------
// Son 130 opciones —134 hasta la etapa 4 de ADR-0039, que retiro de `rentas` las cuatro de
// administracion que ya no sirve—. Transcribirlas a mano una vez ya seria un ejercicio de copia sin
// errores; el problema es la segunda vez: `rentas` regenera su
// `docs/10-negocio/catalogo-de-opciones.md` del prototipo de interfaz, y una copia de 130 filas en
// otro repositorio se separa el primer mes. Ya paso: entre que `rentas` retiro sus cuatro y este
// guion volvio a correr, la copia de aqui dijo 134 durante una etapa entera. El
// sintoma de que se separen es exactamente el que RF-122 existe para impedir —una pantalla a la que
// nadie puede dar permiso— y no se ve mirando ninguna pantalla de aqui.
//
// Los otros cuatro catalogos SI estan transcritos, y no es incoherencia: son 16, 1, 3 y 7 opciones
// escritas a mano en un `CatalogoDelSistema.java` que cabe en una pantalla, y ese archivo ya esta
// atado a sus endpoints por `CatalogoDelSistemaTest` en su propio repositorio. Lo que ata las cinco
// copias de aqui con los cinco originales es la guarda cruzada de `infrastructure` (AC-4), que las
// compara en las dos direcciones y sale roja nombrando la opcion que sobra o falta.
//
// LO QUE ESTE GUION NO HACE, y se dice: no lee `CatalogoDeOpciones.java` de `rentas` — reimplementa
// su analisis, que son dos expresiones regulares. La copia divergiria si `rentas` cambiara el
// formato de su tabla, y lo que lo cazaria es la guarda cruzada, no este archivo.
//
//   node docs/10-negocio/derivar-catalogo-de-rentas.mjs            # escribe el JSON
//   node docs/10-negocio/derivar-catalogo-de-rentas.mjs --comprobar # falla si no cuadra

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const AQUI = dirname(fileURLToPath(import.meta.url));
const CLON_DE_RENTAS = resolve(AQUI, "../../../rentas");
const CATALOGO = resolve(CLON_DE_RENTAS, "docs/10-negocio/catalogo-de-opciones.md");
const DESTINO = resolve(AQUI, "catalogo-de-accesos/rentas.json");

// `## Catastro` abre la seccion de un modulo; `| `ficha_urbana` | Ficha catastral urbana | …` es
// una opcion. Las dos son las de `CatalogoDeOpciones.java` de `rentas`, letra por letra.
const ENCABEZADO = /^## (.+)$/gm;
const FILA = /^\| `([a-z0-9_]+)` \| ([^|]+?) \|/gm;

/** Codigo del modulo a partir de su nombre: mayusculas, sin tildes, sin puntuacion, max 30. */
function codigoDe(nombre) {
  const sinTildes = nombre.normalize("NFD").replace(/\p{M}/gu, "");
  const codigo = sinTildes
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return codigo.length > 30 ? codigo.slice(0, 30) : codigo;
}

function leerCatalogo(markdown) {
  const modulos = [];
  const cortes = [...markdown.matchAll(ENCABEZADO)];
  for (let i = 0; i < cortes.length; i++) {
    const nombre = cortes[i][1].trim();
    const desde = cortes[i].index + cortes[i][0].length;
    const hasta = i + 1 < cortes.length ? cortes[i + 1].index : markdown.length;
    const opciones = [...markdown.slice(desde, hasta).matchAll(FILA)].map((f) => ({
      codigo: f[1],
      nombre: f[2].trim(),
    }));
    if (opciones.length > 0) {
      modulos.push({ codigo: codigoDe(nombre), nombre, opciones });
    }
  }
  return { sistema: "rentas", modulos };
}

function main() {
  let markdown;
  try {
    markdown = readFileSync(CATALOGO, "utf8");
  } catch {
    console.error(
      `No esta ${CATALOGO}. Este guion lo lee del clon HERMANO de \`rentas\`, que no es opcional:` +
        " el catalogo del manual vive alli y aqui solo hay una copia derivada." +
        " Remedio: git clone https://github.com/hneyra/rentas ../rentas",
    );
    process.exit(2);
  }

  const catalogo = leerCatalogo(markdown);
  const cuantas = catalogo.modulos.reduce((n, m) => n + m.opciones.length, 0);
  if (cuantas === 0) {
    console.error(
      "El catalogo de `rentas` se leyo VACIO. Escribir cero opciones dejaria a este sistema sin" +
        " ninguna de sus opciones configurables, y en silencio: se para aqui en vez de escribirlo.",
    );
    process.exit(2);
  }

  const texto = JSON.stringify(catalogo, null, 2) + "\n";
  if (process.argv.includes("--comprobar")) {
    const enDisco = readFileSync(DESTINO, "utf8");
    if (enDisco !== texto) {
      console.error(
        `${DESTINO} no cuadra con el catalogo de \`rentas\`. Regenerarlo:` +
          " node docs/10-negocio/derivar-catalogo-de-rentas.mjs",
      );
      process.exit(1);
    }
    console.log(`El catalogo de \`rentas\` cuadra: ${cuantas} opciones en ${catalogo.modulos.length} modulos`);
    return;
  }

  writeFileSync(DESTINO, texto, "utf8");
  console.log(`Escrito ${DESTINO}: ${cuantas} opciones en ${catalogo.modulos.length} modulos`);
}

main();
