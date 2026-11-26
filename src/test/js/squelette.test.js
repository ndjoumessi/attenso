import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

const SECTIONS = 'front/src/accueil/Sections.jsx';

/**
 * `Agenda` et `Carnets` n'affichaient RIEN pendant le chargement : la liste
 * restait `null`, l'écran vide, puis tout apparaissait d'un coup. Pire qu'un
 * texte d'attente — on ne sait pas si la page travaille ou si elle a fini et
 * qu'il n'y a personne.
 *
 * <p>Et c'est exactement la distinction que ces vues font déjà par ailleurs :
 * « pas encore chargé » n'est pas « aucun ». Le squelette la rend visible.
 */
describe('une liste qui charge le montre', () => {
  it('a un squelette partagé, à un seul endroit', () => {
    const s = rendu(lire('front/src/composants/squelette.jsx'));
    assert.match(s, /export function Squelette/, 'le squelette partagé manque');
    // Le battement passe par une classe, donc par la règle globale de
    // `prefers-reduced-motion` : rien à redire côté mouvement réduit.
    assert.match(s, /animate-pulse/, 'le squelette ne signale pas qu\'il travaille');
    assert.match(s, /aria-hidden="true"/,
      'le squelette est lu à la voix comme du contenu');
  });

  it('est rendu par les deux listes, pendant le chargement seulement', () => {
    const s = rendu(lire(SECTIONS));
    const occurrences = (s.match(/<Squelette\b/g) ?? []).length;
    assert.equal(occurrences, 2, `${occurrences} liste(s) sur 2 montrent leur chargement`);
    // `null` est l'état « pas encore chargé » : c'est lui, et lui seul, qui
    // doit produire le squelette ; le tableau vide a son propre message.
    //
    // Chaque rendu est ancré sur `=== null` dans une fenêtre bornée, et non
    // sur une forme d'écriture : les deux listes expriment la condition
    // différemment — une ternaire imbriquée ici, une conjonction là — et ma
    // première assertion n'acceptait que la première.
    for (const [, avant] of s.matchAll(/([\s\S]{0,260})<Squelette\b/g)) {
      assert.match(avant, /=== null/,
        'un squelette rendu sans être conditionné à « pas encore chargé »');
    }
  });

  /**
   * MESURÉ au navigateur, pas déduit : une ligne d'agenda chargée fait 70 px,
   * le squelette en faisait 56. Il réservait donc MOINS de place qu'il n'en
   * annonçait, et la page sautait de 14 px par ligne au moment du chargement —
   * ce qu'un squelette existe précisément pour éviter. Aucune garde ne pouvait
   * le voir : elles lisent du texte, pas des pixels.
   *
   * <p>Ce qui est vérifiable en texte, et c'est ce qui est vérifié ici : la
   * hauteur vient d'UNE source, que la ligne réelle et son squelette partagent.
   * Deux hauteurs écrites séparément redivergeront, et la prochaine divergence
   * serait de nouveau invisible aux tests.
   */
  it('réserve la hauteur de la ligne qu\'il remplace, depuis une seule source', () => {
    const q = rendu(lire('front/src/composants/squelette.jsx'));
    const definitions = (q.match(/^export const LIGNE = /gm) ?? []).length;
    assert.equal(definitions, 1, 'la hauteur de ligne n\'a pas de source unique');
    assert.match(q, /function Squelette\(\{[^}]*hauteur = LIGNE/,
      'le squelette ne prend pas sa hauteur de la source commune');

    // La ligne d'agenda CHARGÉE doit tenir sa hauteur de la même constante :
    // c'est elle que le squelette remplace. Borné à la balise, pour qu'un
    // `LIGNE` posé ailleurs dans le fichier ne réponde pas à sa place.
    const s = rendu(lire(SECTIONS));
    const balises = s.match(/<li\b[\s\S]*?>/g) ?? [];
    const agenda = balises.filter((b) => /rdv\.statut/.test(b));
    assert.equal(agenda.length, 1, 'la ligne d\'agenda est introuvable');
    assert.match(agenda[0], /\$\{LIGNE\}/,
      'la ligne d\'agenda ne partage pas la hauteur de son squelette');
  });

  it("annonce l'attente à la voix, une fois par liste", () => {
    const s = rendu(lire(SECTIONS));
    assert.equal((s.match(/aria-busy=\{/g) ?? []).length, 2,
      'les listes ne disent pas à la voix qu\'elles chargent');
  });
});
