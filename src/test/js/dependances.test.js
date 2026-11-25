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

/**
 * LISTE BLANCHE, volontairement étroite : ce que front/src/ peut importer.
 *
 * Pas une liste noire, et pas « ce qui est déclaré dans package.json » : un
 * `shadcn add` qui écrit `from "cn"` (le nom d'un élément de registre pris pour
 * un paquet) inscrit AUSSI `cn` en dépendance, et une garde fondée sur le
 * manifeste resterait verte. Élargir cette liste doit être une décision
 * prise ici, devant un relecteur, jamais l'effet de bord d'une installation.
 */
const AUTORISES_SRC = [
  'react', 'react-dom',
  'clsx', 'tailwind-merge', 'class-variance-authority', 'radix-ui', 'lucide-react',
];

/** prerender.mjs tourne à la construction, pas dans un navigateur. */
const AUTORISES_PRERENDER = ['react', 'react-dom', 'vite', '@vitejs/plugin-react'];

/** Le nom du paquet derrière un spécificateur nu : `radix-ui/x` -> `radix-ui`, `@a/b/c` -> `@a/b`. */
function paquet(spec) {
  const morceaux = spec.split('/');
  return spec.startsWith('@') ? morceaux.slice(0, 2).join('/') : morceaux[0];
}

describe('les imports nus sont une liste blanche, déclarés ou non', () => {
  const motif = /(?:\bfrom|\bimport)\s*\(?\s*['"]([^'"]+)['"]/g;

  function interdits(fichier, autorises) {
    return [...readFileSync(fichier, 'utf8').matchAll(motif)]
      .map((m) => m[1])
      .filter((spec) => !spec.startsWith('.') && !spec.startsWith('@/') && !spec.startsWith('node:'))
      .filter((spec) => !autorises.includes(paquet(spec)))
      .map((spec) => `${fichier.slice(FRONT.length + 1)} importe « ${spec} »`);
  }

  it('front/src/ n\'importe que les paquets de la liste blanche', () => {
    const fautifs = sources().flatMap((f) => interdits(f, AUTORISES_SRC));
    assert.deepEqual(fautifs, [], 'paquet hors liste blanche : un homonyme npm y entrerait');
  });

  it('prerender.mjs n\'importe que les paquets de sa liste blanche', () => {
    assert.deepEqual(interdits(join(FRONT, 'prerender.mjs'), AUTORISES_PRERENDER), []);
  });

  it('la liste blanche ne contient que des paquets réellement déclarés', () => {
    const pkg = JSON.parse(readFileSync(join(FRONT, 'package.json'), 'utf8'));
    const declares = Object.keys({ ...pkg.dependencies, ...pkg.devDependencies });
    const absents = [...AUTORISES_SRC, ...AUTORISES_PRERENDER].filter((n) => !declares.includes(n));
    assert.deepEqual(absents, []);
  });
});
