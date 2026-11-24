import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, it } from 'node:test';

const FRONT = resolve(import.meta.dirname, '../../../front');

/** Les couleurs que Tailwind fournit seul, sans entrée dans le thème. */
const COULEURS_TAILWIND = new Set(['white', 'black', 'transparent', 'current', 'inherit']);

/**
 * Les mots qui suivent un de ces préfixes sans être une couleur : tailles,
 * côtés, alignements, styles. Liste de Tailwind, pas du projet : elle ne bouge
 * que si Tailwind ajoute un mot, et le test dit alors lequel il ne connaît pas.
 */
const PAS_UNE_COULEUR = new Set([
  'xs', 'sm', 'md', 'lg', 'xl', 'base', 'none', 'hidden', 'auto', 'inherit',
  't', 'b', 'l', 'r', 'x', 'y', 's', 'e',
  'left', 'right', 'center', 'start', 'end', 'justify',
  'wrap', 'nowrap', 'balance', 'pretty', 'ellipsis', 'clip',
  'solid', 'dashed', 'dotted', 'double', 'collapse', 'separate', 'offset', 'inset',
]);

/** Les noms de couleur déclarés dans le thème Tailwind du projet. */
function couleursDeclarees() {
  const css = readFileSync(join(FRONT, 'src/styles.css'), 'utf8');
  return new Set([...css.matchAll(/^\s*--color-([\w-]+)\s*:/gm)].map((m) => m[1]));
}

/** Les couleurs utilisées par les classes d'un composant : bg-x, text-x/50, ring-offset-x... */
function couleursUtilisees(source) {
  const motif = /(?<![\w-])(?:ring-offset|bg|text|border|ring|outline|fill|stroke|decoration|caret|divide)-([a-z]+(?:-[a-z]+)*)(?=[\s/"'\]:)`]|$)/g;
  return [...source.matchAll(motif)].map((m) => m[1]).filter((n) => !PAS_UNE_COULEUR.has(n));
}

describe('les composants de front/src/ui/ n\'utilisent que des couleurs du thème', () => {
  it('chaque couleur utilisée existe parmi les --color-* de styles.css', () => {
    const declarees = couleursDeclarees();
    assert.ok(declarees.has('fond') && declarees.has('sidebar'), 'extraction des --color-* en défaut');
    const dossier = join(FRONT, 'src/ui');
    const fautifs = [];
    for (const f of readdirSync(dossier).filter((n) => n.endsWith('.jsx'))) {
      for (const nom of new Set(couleursUtilisees(readFileSync(join(dossier, f), 'utf8')))) {
        if (!declarees.has(nom) && !COULEURS_TAILWIND.has(nom)) fautifs.push(`${f} : ${nom}`);
      }
    }
    assert.deepEqual(fautifs, [],
      'couleur sans --color-* : elle rendrait transparent. Mapper sur la palette du projet, pas d\'alias shadcn');
  });
});
