import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { balisesButton } from './aide/balises.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

const FICHIERS = [
  ...readdirSync(resolve(RACINE, 'front/src')).filter((f) => f.endsWith('.jsx'))
    .map((f) => `front/src/${f}`),
  ...readdirSync(resolve(RACINE, 'front/src/accueil')).filter((f) => f.endsWith('.jsx'))
    .map((f) => `front/src/accueil/${f}`),
  ...readdirSync(resolve(RACINE, 'front/src/composants')).filter((f) => f.endsWith('.jsx'))
    .map((f) => `front/src/composants/${f}`),
];

/**
 * `disabled` servait AUSSI d'état de chargement : sept boutons portaient
 * `disabled={envoi}` et `disabled:opacity-60`. Mesuré : en thème clair, le
 * bouton principal tombe de 7,64:1 à **2,97:1** — c'est-à-dire que
 * « Un instant… » devient presque illisible au moment précis où il demande
 * d'attendre.
 *
 * <p>WCAG 1.4.3 exempte les contrôles inactifs, donc ce n'est pas un échec AA.
 * Mais un bouton en cours d'envoi n'est pas inactif : il travaille.
 */
describe('un bouton qui travaille reste lisible', () => {
  /**
   * UNE seule propriété, sur la balise : un bouton ne peut pas être à la fois
   * « occupé » et atténué. `aria-busy` dit qu'il travaille ; une atténuation
   * écrite en dur efface alors son propre message. L'inverse est permis — un
   * bouton indisponible (hors réseau) s'atténue et n'est pas occupé.
   *
   * <p>Les deux gardes d'avant, l'une par fichier et l'autre par balise,
   * laissaient passer la mutation qui remplace `attenue(disabled)` par
   * l'atténuation en dur : la première voyait l'export `attenue` dans le
   * fichier, la seconde ne regardait plus la balise devenue sans `attenue`.
   */
  it("n'atténue jamais un bouton qui travaille", () => {
    const fautifs = [];
    for (const c of FICHIERS) {
      // `/<button[\s\S]*?>/` s'arrêtait au `>` d'un `onClick={() => x}` :
      // 10 des 27 balises de ce dépôt lui revenaient AMPUTÉES, donc une
      // propriété écrite après la flèche lui était invisible.
      for (const balise of balisesButton(rendu(lire(c)))) {
        const occupe = /aria-busy=/.test(balise);
        const attenueEnDur = /disabled:opacity/.test(balise);
        const parLaFonction = /attenue\(/.test(balise);
        if (occupe && (attenueEnDur || !parLaFonction)) {
          fautifs.push(`${c.replace('front/src/', '')} : ${balise.slice(0, 48)}`);
        }
        if (!occupe && parLaFonction) {
          fautifs.push(`${c.replace('front/src/', '')} : atténué sans aria-busy`);
        }
      }
    }
    assert.deepEqual(fautifs, [],
      'un bouton qui travaille efface son propre message de chargement');
  });

  /** L'atténuation vient d'UN endroit : sept copies divergeront. */
  it('tient son atténuation dans une seule fonction', () => {
    const src = rendu(lire('front/src/composants/formulaire.jsx'));
    assert.match(src, /export (?:const|function) attenue/,
      "l'atténuation n'a pas de source unique");
  });
});
