/* Comprueba que un PR que cierra un issue deja su fila en «Verificar antes de afirmar».

   El registro de «Verificar antes de afirmar» es la memoria del proyecto: cada issue
   deja ahi que se implemento y **como se demostro que la verificacion puede fallar**.
   Es lo que impide volver a descubrir el mismo hallazgo de RLS por tercera vez.

   Y no la comprobaba nadie. Al integrar #585 y #618 en `sgtm` la fila no se escribio y
   los dos PR pasaron todos sus checks en verde; el hueco se descubrio a mano, leyendo
   la tabla. El modo de fallo es silencioso: la fila que falta no se distingue de la que
   nadie tenia que escribir.

   ## Que exige, y que NO

   Exige que **exista** una fila que nombre el issue. No mira su contenido —que la
   mutacion descrita sea real, que las cifras cuadren— porque eso no lo puede leer una
   maquina, y es justo lo que la revision si puede.

   Y solo lo exige cuando las dos cosas son ciertas:

     1. el cuerpo del PR declara que cierra un issue (`Cierra #N`, `Closes #N`,
        `Fixes #N`, `Resuelve #N`), y
     2. el cambio toca el codigo de produccion de este repositorio.

   Un PR de solo documentacion, de solo pruebas o sin issue asociado pasa en verde. Sin
   ese contraste la guarda seria un peaje que todo el mundo aprende a esquivar — y una
   guarda esquivada no protege nada, que es de donde venimos.

   ## Uso

     node docs/00-gobierno/verificar-fila-del-registro.mjs [--base origin/main]

   El cuerpo del PR sale de `KAMAYUK_CUERPO_DEL_PR`; sin esa variable no hay nada que
   comprobar y la comprobacion pasa, porque fuera de un PR no existe el dato.

   Las tres entradas se pueden dar por archivo —`--cuerpo`, `--archivos`, `--anadido`—,
   y es lo que usa su autoprueba: sin poder alimentarlas, demostrar que muerde exigiria
   fabricar un repositorio, y una comprobacion que no se puede probar es la que este
   guion viene a impedir.
*/

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

/** Lo que hace de un cambio «codigo» a efectos de esta guarda.

    ESTA LISTA ES PROPIA DE ESTE REPOSITORIO Y NO SE COPIA. El guion vive replicado en
    los seis y lo comun es el MECANISMO —los casos de la autoprueba, `CIERRA`, «exige
    que la fila exista y no lo que diga»—; la lista la decide cada dueno con lo que su
    arbol tiene. Copiarla a ciegas es exactamente lo que produjo el hueco de `rentas`#45.

    LAS TRES NACEN AQUI CON EL REPOSITORIO, y las dos ultimas son deuda que los hermanos
    tuvieron que pagar despues:

    `backend/<modulo>/src/main/`. El codigo de produccion del backend. Se acota a
    `src/main/` a proposito: `src/test/` son pruebas, y una prueba no es codigo de
    produccion.

    `infrastructure/src/` (`rentas`#45). El descriptor de despliegue decide que corre en
    la municipalidad: los limites, los `securityContext`, las `NetworkPolicy`, las
    variables de entorno del pod y sus rutas de ingreso. C-17 midio CINCO defectos que
    vivian ahi y que solo se ven al desplegar, y `rentas` llevaba esa ruta FUERA de su
    lista: el mismo cambio salia `exit=0` alli y `exit=1` en los otros tres. Se acota a
    `src/` a proposito: `infrastructure/verificaciones/` son sus pruebas.

    `despliegue/` (`caja`#39). Es la OTRA forma de levantar este sistema, y ADR-0011 tiene
    escrito lo que cuesta que las dos se separen: «una variable nueva que entra en el
    cluster y no en el compose». `rentas` lo dejo declarado como hueco medido y no
    cerrado en su #45 —«es el mismo defecto que `caja`#39 cerro alli»—; aqui entra desde
    el primer commit para no tener que volver a medirlo.

    LO QUE NO ESTA, y es una afirmacion: este repositorio **no tiene `frontend/` ni
    `infra/`**. No hay pantalla —las de administracion viven hoy en `rentas` y su mudanza
    es otro issue (ADR-0039, etapas 2 y 3)— ni guiones de carga de datos. El dia que los
    haya, la ruta entra aqui **y su muestra en la autoprueba**, que es lo que la
    comprobacion de abajo exige.

    SE EXPORTA para que su autoprueba pueda exigir que cada patron tenga su muestra. Es
    la mitad que faltaba en los hermanos: quitar una muestra dejaba la autoprueba en «las
    7 se comportan como deben», en verde. Y se exporta en vez de copiarse alli porque una
    copia se queda vieja sola y entonces la autoprueba certifica una lista que ya no es
    esta. */
export const RUTAS_DE_CODIGO = [
  /^backend\/[^/]+\/src\/main\//,
  /^infrastructure\/src\//,
  /^despliegue\//,
  // Los cinco catalogos de accesos (etapa 2). Son DATOS y estan en `docs/`, y aun asi son codigo
  // de produccion: el build los copia al jar y la implantacion siembra de ahi las 160 opciones de
  // los cinco sistemas. Cambiar uno cambia a que pantallas se les puede dar permiso en una
  // municipalidad (RF-122), que es tanto como cambiar una linea de Java.
  //
  // Se acota a esa carpeta y no a `docs/10-negocio/` entera por lo mismo que `infrastructure/` se
  // acota a `src/`: lo que hay al lado —un guion que la deriva, un README— no lo lee nadie en
  // produccion, y una guarda que se dispara en cada PR de documentacion se acaba apagando.
  /^docs\/10-negocio\/catalogo-de-accesos\//,
];

/**
 * Donde puede estar la fila. **Son dos a proposito, y es una ventana de compatibilidad**
 * (`infrastructure`#114): el registro se muda de `CLAUDE.md` a `docs/agent/HISTORY.md` —eran
 * el 74 % de un archivo que cada sesion carga entero— y los seis repositorios no migran a la
 * vez.
 *
 * Mientras las dos esten aqui, una fila escrita en cualquiera de los dos cuenta. El dia que
 * los seis hayan migrado se retira `CLAUDE.md` **en un cambio propio**, y entonces una fila
 * en el sitio viejo deja de contar. Estrechar antes deja rojos cruzados en los que aun no
 * han migrado.
 */
const DONDE_VIVE_LA_FILA = ['docs/agent/HISTORY.md', 'CLAUDE.md'];

/** Como se declara que un PR cierra un issue. GitHub admite estas y alguna mas. */
const CIERRA = /\b(?:cierra|closes?|close|fixes?|fix|resuelve|resolves?)\s+#(\d+)/gi;

// Se ejecuta SOLO cuando se invoca como guion. Importarlo no hace nada, que es lo que
// permite a su autoprueba leer `RUTAS_DE_CODIGO` de aqui en vez de copiarla.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  principal();
}

function principal() {
  const opciones = leerOpciones(process.argv.slice(2));

  const cuerpo = opciones.cuerpo
    ? readFileSync(opciones.cuerpo, 'utf8')
    : (process.env.KAMAYUK_CUERPO_DEL_PR ?? '');

  const issues = [...cuerpo.matchAll(CIERRA)].map((coincidencia) => coincidencia[1]);
  if (issues.length === 0) {
    console.log('El PR no declara que cierre ningun issue: no hay fila que exigir.');
    process.exit(0);
  }

  const archivos = opciones.archivos
    ? lineas(readFileSync(opciones.archivos, 'utf8'))
    : lineas(git(['diff', '--name-only', `${opciones.base}...HEAD`]));

  const deCodigo = archivos.filter((ruta) => RUTAS_DE_CODIGO.some((patron) => patron.test(ruta)));
  if (deCodigo.length === 0) {
    console.log(
      `Cierra #${issues.join(', #')} y no toca codigo de produccion: la fila no se exige.`,
    );
    process.exit(0);
  }

  const anadido = opciones.anadido
    ? readFileSync(opciones.anadido, 'utf8')
    : git(['diff', `${opciones.base}...HEAD`, '--', ...DONDE_VIVE_LA_FILA])
        .split('\n')
        .filter((linea) => linea.startsWith('+') && !linea.startsWith('+++'))
        .join('\n');

  const sinFila = issues.filter((numero) => !nombra(anadido, numero));
  if (sinFila.length > 0) {
    console.error('');
    console.error(
      `FALLO: falta la fila de «Verificar antes de afirmar» en ${DONDE_VIVE_LA_FILA[0]}.`,
    );
    console.error('');
    for (const numero of sinFila) {
      console.error(
        `  · Este PR cierra #${numero} y no lo nombra ninguna linea nueva de ` +
          `${DONDE_VIVE_LA_FILA.join(' ni de ')}.`,
      );
    }
    console.error('');
    console.error('  Esa tabla es la memoria del proyecto: cada issue deja ahi que se');
    console.error('  implemento y COMO SE DEMOSTRO QUE LA VERIFICACION PUEDE FALLAR. Una fila');
    console.error('  que no se escribe es una leccion que el siguiente vuelve a descubrir');
    console.error('  ejecutando.');
    console.error('');
    console.error('  Lo que se comprueba aqui es solo que la fila EXISTA. Que diga la verdad');
    console.error('  —que la mutacion sea real y las cifras cuadren— lo lee la revision.');
    console.error('');
    console.error(`  Archivos de codigo en este cambio: ${deCodigo.length}`);
    console.error(`    ${deCodigo.slice(0, 5).join('\n    ')}`);
    process.exit(1);
  }

  console.log(`Cada issue que este PR cierra tiene su fila: #${issues.join(', #')}.`);
}

// ---------------------------------------------------------------------------

/** Si ese texto nombra al issue como tal y no como parte de otro numero. */
function nombra(texto, numero) {
  return new RegExp(`#${numero}(?![0-9])`).test(texto);
}

function lineas(texto) {
  return texto
    .split('\n')
    .map((linea) => linea.trim())
    .filter((linea) => linea.length > 0);
}

function git(argumentos) {
  return execFileSync('git', argumentos, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

function leerOpciones(argumentos) {
  const opciones = { base: 'origin/main' };
  for (let i = 0; i < argumentos.length; i += 2) {
    const nombre = argumentos[i];
    const valor = argumentos[i + 1];
    if (valor === undefined) {
      throw new Error(`Falta el valor de ${nombre}`);
    }
    if (!['--base', '--cuerpo', '--archivos', '--anadido'].includes(nombre)) {
      throw new Error(`Opcion desconocida: ${nombre}`);
    }
    opciones[nombre.slice(2)] = valor;
  }
  return opciones;
}
