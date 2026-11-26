import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { balisesButton } from './aide/balises.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

/** Les fichiers écrits à la main. `ui/` est du shadcn vendorisé : pas à nous. */
function sources(dossier = 'front/src', prefixe = '') {
  const out = [];
  for (const e of readdirSync(resolve(RACINE, dossier), { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (e.name === 'ui') continue;
      out.push(...sources(`${dossier}/${e.name}`, `${prefixe}${e.name}/`));
    } else if (e.name.endsWith('.jsx')) {
      out.push([`${prefixe}${e.name}`, rendu(lire(`${dossier}/${e.name}`))]);
    }
  }
  return out;
}

/** Les constantes de classes qui portent le geste, d'où qu'elles viennent. */
function porteuses() {
  const noms = new Set(['PRESSE']);
  for (const [, s] of sources()) {
    for (const m of s.matchAll(/(?:export )?const ([A-Za-z_][A-Za-z0-9_]*) = (`|')([\s\S]*?)\2;/g)) {
      if (m[3].includes('active:scale') || /\$\{PRESSE\}/.test(m[3])) noms.add(m[1]);
    }
  }
  return [...noms];
}

const porteLeGeste = (balise, noms) => balise.includes('active:scale')
  || noms.some((n) => new RegExp(`\\b${n}\\b`).test(balise));

/**
 * Sur la TABLETTE DU COMPTOIR, `hover:` ne se déclenche jamais : Tailwind 4
 * enveloppe lui-même chaque règle `hover:` dans `@media (hover:hover)`
 * — vérifié sur le CSS construit, 13 règles sur 13. Vingt-trois des
 * vingt-six boutons écrits à la main n'avaient QUE `hover:` pour toute
 * réaction. Autrement dit, sur l'appareil où l'ASV passe sa journée, ils ne
 * répondaient rien du tout à l'appui.
 */
describe('un bouton répond quand on appuie dessus', () => {
  it('porte le geste, partout, depuis une seule source', () => {
    const noms = porteuses();
    const muets = [];
    for (const [nom, s] of sources()) {
      for (const b of balisesButton(s)) {
        if (!porteLeGeste(b, noms)) muets.push(`${nom} : ${b.slice(0, 60).replace(/\s+/g, ' ')}`);
      }
    }
    assert.deepEqual(muets, [], 'des boutons ne réagissent pas à l\'appui');
  });

  it('tient ce geste dans une seule fonction', () => {
    const src = rendu(lire('front/src/composants/formulaire.jsx'));
    assert.equal((src.match(/^export const PRESSE = /gm) ?? []).length, 1,
      'le geste d\'appui n\'a pas de source unique');
    assert.match(src, /export const PRESSE[\s\S]{0,200}active:scale-\[0\.97\]/,
      'la source du geste ne porte pas d\'enfoncement');
  });

  /**
   * LE PIÈGE, rencontré en le posant : Tailwind 4 rend `scale-[0.97]` sur la
   * propriété CSS `scale`, PAS sur `transform`. Une balise qui déclare
   * `transition-[filter,box-shadow,transform]` et un `active:scale` n'anime
   * donc rien — l'enfoncement claque au lieu de glisser. Les quatre boutons
   * concernés étaient dans ce cas, et l'ancien `active:translate-y-px` l'était
   * déjà avant moi, pour la même raison.
   *
   * <p>Vérifié au navigateur après correction : au `pointerdown`, le bouton
   * principal fait bien tourner une transition sur `scale`, 150 ms, courbe du
   * projet. Rien dans le texte ne le disait ; cette garde le dit.
   */
  it('met « scale » dans sa liste de transition, et non « transform »', () => {
    const fautifs = [];
    for (const [nom, s] of sources()) {
      for (const b of s.match(/transition-\[[a-z,-]*\]/g) ?? []) {
        if (b.includes('transform')) fautifs.push(`${nom} : ${b}`);
      }
    }
    assert.deepEqual(fautifs, [],
      '« transform » n\'anime pas `scale` : l\'enfoncement claquerait');
  });

  /**
   * Deux utilitaires `transition` sur une même balise se départagent par
   * l'ordre de la FEUILLE, pas par celui des classes : le gagnant n'est pas
   * celui qu'on croit. `PRESSE` apporte la sienne, donc il ne va que sur un
   * élément qui n'en déclare aucune.
   */
  it('ne superpose jamais deux transitions sur la même balise', () => {
    const fautifs = [];
    for (const [nom, s] of sources()) {
      for (const b of balisesButton(s)) {
        if (/\$\{PRESSE\}/.test(b) && /transition-\[/.test(b)) fautifs.push(nom);
      }
    }
    assert.deepEqual(fautifs, [], 'deux transitions se disputent la même balise');
  });
});

/**
 * `--courbe-sortie` valait cubic-bezier(0.4, 0, 1, 1) : un ease-in, à 1,8 %
 * du chemin au dixième de sa durée. C'est la courbe qui part lentement, donc
 * celle qui fait paraître une interface molle. Elle n'était utilisée nulle
 * part — mais elle portait le nom qu'on vient chercher pour animer une sortie.
 * Un jeton mort qui est un piège finit par servir.
 */
describe('le bloc Mouvement ne déclare rien de mort', () => {
  it('n\'a aucun jeton de mouvement sans emploi', () => {
    const jetons = lire('src/main/resources/static/app/jetons.css');
    const bloc = jetons.match(/--- Mouvement [\s\S]*?\*\/([\s\S]*?)\n\n/);
    assert.ok(bloc, 'le bloc Mouvement est introuvable');
    const declares = [...bloc[1].matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map((m) => m[1]);
    assert.ok(declares.length > 0, 'le bloc Mouvement ne déclare plus rien');

    const tout = [jetons, lire('front/src/styles.css'),
      ...sources().map(([, s]) => s)].join('\n');
    const morts = declares.filter((n) => !tout.includes(`var(${n})`));
    assert.deepEqual(morts, [], 'des jetons de mouvement ne servent à rien');
  });
});
