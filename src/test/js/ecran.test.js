import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const ECRAN = 'front/src/ecran.jsx';

/**
 * TROUVÉ EN REGARDANT L'ÉCRAN, pas en lisant du texte — et c'est le point de
 * ce fichier.
 *
 * <p>Le grand nombre est dimensionné en `clamp(5rem, 22vw, 18rem)` pour rester
 * lisible à quatre mètres. Quand personne n'est appelé, `vue-ecran.js` y
 * mettait `RIEN`, c'est-à-dire un cadratin — mesuré en production à
 * **281,6 px, graisse 700, blanc**, soit une boîte de 228 × 282 px.
 *
 * <p>À cette taille un tiret n'est plus de la ponctuation : c'est une barre
 * blanche pleine au milieu d'un écran noir. Du fond d'une salle d'attente,
 * ça ne se lit pas « aucun numéro appelé », ça se lit « écran cassé ».
 *
 * <p>Aucune garde du projet ne pouvait l'attraper : elles lisent du texte, et
 * le texte était JUSTE. `vue-ecran.js` n'est pas en cause — son raisonnement
 * est bon et documenté. C'est le rendu qui trahissait l'intention.
 */
describe("l'écran ne met jamais un tiret en typographie d'affichage", () => {
  /** La classe du grand nombre, telle qu'elle est écrite dans la page. */
  const AFFICHAGE = /text-\[clamp\([^\]]*\)\]/;

  it('ne rend le grand nombre que lorsqu\'il y a un numéro', () => {
    const source = sansCommentaires(lire(ECRAN));
    assert.match(source, AFFICHAGE, 'la typographie d\'affichage a disparu');
    // Fenêtre BORNÉE, de la condition jusqu'à la classe : exiger seulement
    // que les deux existent serait vert si la condition gardait autre chose.
    assert.match(source, /aUnNumero\s*&&\s*\([\s\S]{0,200}?text-\[clamp/,
      'le grand nombre est rendu sans condition : le tiret y repassera');
  });

  /**
   * Les DEUX chemins qui y menaient. Le second — la page encore en
   * chargement — produisait exactement la même barre, et je ne l'avais pas
   * vu en lisant l'écran : il ne dure qu'un instant.
   */
  it('ferme les deux chemins qui amenaient un tiret dans cette typographie', () => {
    const source = sansCommentaires(lire(ECRAN));
    const grand = source.match(/text-\[clamp[\s\S]{0,260}?<\/p>/)?.[0] ?? '';
    assert.ok(grand, 'le bloc du grand nombre est introuvable');
    assert.doesNotMatch(grand, /'—'|"—"|RIEN/,
      'un tiret littéral revient dans la typographie d\'affichage');
    assert.doesNotMatch(grand, /vue\s*\?[\s\S]*?:/,
      'le repli de chargement remet un tiret dans la typographie d\'affichage');
  });

  /**
   * La décision de `vue-ecran.js` reste intacte : ne pas annoncer le vide
   * quand des gens attendent. Sans grand nombre, c'est la légende qui mène —
   * « consultation en cours » —, et elle doit grandir, sinon l'écran n'a plus
   * aucun point d'accroche à quatre mètres.
   */
  it('fait mener la légende quand aucun numéro ne s\'affiche', () => {
    const source = sansCommentaires(lire(ECRAN));
    assert.match(source, /legende\(vue\)/, 'la légende a disparu');
    assert.match(source, /aUnNumero\s*\?/,
      'la légende ne change pas de taille selon qu\'un numéro s\'affiche ou non');
  });
});
