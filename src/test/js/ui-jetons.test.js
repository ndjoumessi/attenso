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
  const motif = /(?<![\w-])(?:inset-ring|ring-offset|bg|text|border|ring|outline|fill|stroke|decoration|caret|divide|shadow|accent|placeholder|from|via|to)-([a-z]+(?:-[a-z]+)*)(?=[\s/!"'\]:)`]|$)/g;
  return [...source.matchAll(motif)].map((m) => m[1]).filter((n) => !PAS_UNE_COULEUR.has(n));
}

/**
 * Les dossiers sous garde, et pourquoi il y en a deux.
 *
 * <p>`src/ui/` seul laissait dehors le fichier qui porte la barre de la
 * démonstration : une faute de frappe dans `accueil/Barre.jsx` rendait du texte
 * transparent avec toute la suite verte. `accueil/` est balayé RÉCURSIVEMENT,
 * parce que les tâches suivantes y ajoutent `accueil/vues/`.
 *
 * <p>`composants/` y est aussi : la branche l'a restylé (`base`, `formulaire`,
 * `qr`) et les quatorze pages l'importent ; `text-sur-fond-dou` y rendait du
 * texte transparent partout, tout vert.
 */
const SOUS_GARDE = ['src/ui', 'src/accueil', 'src/composants'];

/** Tous les .jsx d'un dossier, récursivement, en chemins relatifs à front/. */
function composants(dossier) {
  return readdirSync(join(FRONT, dossier), { withFileTypes: true }).flatMap((e) => {
    const chemin = `${dossier}/${e.name}`;
    if (e.isDirectory()) return composants(chemin);
    return e.name.endsWith('.jsx') ? [chemin] : [];
  });
}

describe('les composants du tableau n\'utilisent que des couleurs du thème', () => {
  it('chaque couleur utilisée existe parmi les --color-* de styles.css', () => {
    const declarees = couleursDeclarees();
    assert.ok(declarees.has('fond') && declarees.has('sidebar'), 'extraction des --color-* en défaut');
    const fichiers = SOUS_GARDE.flatMap(composants);
    // La garde doit COUVRIR, pas seulement passer : si l'arborescence change de
    // nom, un balayage vide serait vert et ne dirait rien.
    assert.ok(fichiers.some((f) => f.startsWith('src/ui/')), 'src/ui/ hors garde');
    assert.ok(fichiers.some((f) => f.startsWith('src/accueil/')), 'src/accueil/ hors garde');
    assert.ok(fichiers.some((f) => f.startsWith('src/composants/')), 'src/composants/ hors garde');
    const fautifs = [];
    for (const f of fichiers) {
      for (const nom of new Set(couleursUtilisees(readFileSync(join(FRONT, f), 'utf8')))) {
        if (!declarees.has(nom) && !COULEURS_TAILWIND.has(nom)) fautifs.push(`${f} : ${nom}`);
      }
    }
    assert.deepEqual(fautifs, [],
      'couleur sans --color-* : elle rendrait transparent. Mapper sur la palette du projet, pas d\'alias shadcn');
  });
});
