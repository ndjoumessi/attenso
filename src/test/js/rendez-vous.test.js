import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/**
 * Les lignes d'`import` sont retirées AVANT de juger : une garde qui cherche
 * `lienVivant` dans le fichier entier reste verte quand l'appel a disparu du
 * rendu, parce que l'import le nomme encore. C'est le piège qui a laissé
 * quatre gardes vertes sur du code faux le 2026-11-26.
 */
const rendu = (source) => source
  .replace(/^import[\s\S]*?;$/gm, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const PAGE = 'front/src/rendez-vous.jsx';

/**
 * Un lien de rendez-vous mort laissait la grande carte affichée au-dessus de
 * l'erreur : « Votre rendez-vous », un créneau vide tenu par son espace
 * insécable, et rien dessous — une boîte de 206 px qui ne dit rien, suivie de
 * « Ce rendez-vous n'existe pas ». Exactement le défaut corrigé sur la page de
 * suivi, qui vit dans le même prédicat partagé.
 *
 * La décision elle-même est éprouvée sur ses quatre cas dans
 * `vue-suivi.test.js`. Ce fichier ne garde que ce qu'un test de module ne peut
 * pas voir : que la page l'APPELLE, et qu'elle appelle celle-là.
 */
describe('la page de rendez-vous cache la carte quand le lien est mort', () => {
  it('conditionne la carte à lienVivant', () => {
    const source = rendu(lire(PAGE));
    assert.match(source, /lienVivant\(rdv,\s*erreur\)/,
      'la carte ne consulte pas la décision');
    // Fenêtre BORNÉE : la condition doit précéder l'étiquette de la carte.
    // « les deux chaînes sont présentes » resterait vert si la condition
    // était posée ailleurs et la carte rendue sans elle.
    assert.match(source, /lienVivant\([\s\S]{0,400}?Votre rendez-vous/,
      'la carte est rendue hors de la condition : un lien mort montrera une boîte vide');
  });

  /**
   * Le prédicat est partagé avec la page de suivi. Une copie locale dériverait
   * au premier changement — et c'est le même défaut qui avait été corrigé deux
   * fois à deux endroits.
   */
  it('emprunte la décision à vue-suivi plutôt que de la réécrire', () => {
    const source = lire(PAGE);
    assert.match(source,
      /import \{[^}]*\blienVivant\b[^}]*\} from '[^']*vue-suivi\.js'/,
      'la page ne tient pas sa décision du module partagé');
    assert.doesNotMatch(rendu(source), /function lienVivant/,
      'la page réécrit le prédicat au lieu de l\'emprunter');
  });
});
