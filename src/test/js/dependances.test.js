import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, it } from 'node:test';

const FRONT = resolve(import.meta.dirname, '../../../front');
const SRC = join(FRONT, 'src');

/** Tous les .js et .jsx sous front/src/, récursivement. */
function sources(dossier = SRC) {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return sources(chemin);
    return /\.jsx?$/.test(e.name) ? [chemin] : [];
  });
}

/** Le nom du paquet derrière un spécificateur nu : `radix-ui/x` -> `radix-ui`, `@a/b/c` -> `@a/b`. */
function paquet(spec) {
  const morceaux = spec.split('/');
  return spec.startsWith('@') ? morceaux.slice(0, 2).join('/') : morceaux[0];
}

describe('les imports nus de front/src/ sont déclarés dans front/package.json', () => {
  it('aucun paquet importé sans être déclaré', () => {
    const pkg = JSON.parse(readFileSync(join(FRONT, 'package.json'), 'utf8'));
    const declares = new Set([
      ...Object.keys(pkg.dependencies ?? {}),
      ...Object.keys(pkg.devDependencies ?? {}),
    ]);
    const motif = /(?:\bfrom|\bimport)\s*\(?\s*['"]([^'"]+)['"]/g;
    const fautifs = [];
    for (const fichier of sources()) {
      for (const [, spec] of readFileSync(fichier, 'utf8').matchAll(motif)) {
        if (spec.startsWith('.') || spec.startsWith('@/') || spec.startsWith('node:')) continue;
        if (!declares.has(paquet(spec))) fautifs.push(`${fichier.slice(FRONT.length + 1)} importe « ${spec} »`);
      }
    }
    // Le cas d'origine : `shadcn add` écrit `from "cn"` pour un utilitaire local, et
    // un paquet npm homonyme installé un jour entrerait dans le bundle du personnel.
    assert.deepEqual(fautifs, [], 'import de paquet non déclaré : un homonyme npm y entrerait');
  });
});
