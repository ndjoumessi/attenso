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

/**
 * CE QUE LE MUR SE LAISSE LIRE À QUATRE MÈTRES.
 *
 * <p>Le grand nombre était dimensionné en vw « pour rester lisible à quatre
 * mètres quel que soit le téléviseur ». Les lignes d'appui, elles, étaient
 * figées par points de rupture : mesurées à 1920 × 1080 sur la page servie en
 * production, 36, 30 et 20 px.
 *
 * <p>L'arithmétique, pour qu'elle soit refaisable. Sur un 55 pouces en 1080p,
 * la dalle fait 685 mm de haut, donc un pixel CSS vaut 0,63 mm ; la hauteur de
 * capitale vaut environ 0,7 fois le corps. Ces trois lignes mesuraient donc
 * 16, 13 et 9 mm de capitale, là où la règle d'un panneau — capitale ≥
 * distance / 200 — en demande 20 à quatre mètres.
 *
 * <p>Pourquoi vw et non des points de rupture : à 1920 et à 1366, mesuré dans
 * le navigateur sur la page construite, les distances de lecture sont les
 * MÊMES (6,4 / 5,3 / 4,3 m). Un corps en vw est une proportion de la dalle,
 * donc indépendant de la résolution que le téléviseur annonce ; un palier
 * `sm:` ne l'est pas.
 *
 * <p>Comme la garde du tiret au-dessus, celle-ci ne voit PAS la taille de
 * rendu. Elle garde le mécanisme (vw) et le seuil (48 px sur la plus petite
 * ligne), parce que c'est au navigateur qu'on a demandé les millimètres.
 */
describe('les lignes du mur grandissent avec la dalle', () => {
  const PX_PAR_REM = 16;
  const enPx = (valeur) => (valeur.endsWith('rem')
    ? parseFloat(valeur) * PX_PAR_REM : parseFloat(valeur));

  /** Les `clamp()` de la page, dans l'ordre du source. */
  function clamps(source) {
    return [...source.matchAll(/text-\[clamp\(([^)]*)\)\]/g)].map((m) => {
      const [plancher, prefere, plafond] = m[1].split(',').map((v) => v.trim());
      return { plancher, prefere, plafond, plafondPx: enPx(plafond) };
    });
  }

  // L'ordre du source est aussi l'ordre de lecture : nombre, légende,
  // suivants, délai. Le compte est piné — en ajouter une cinquième sans y
  // penser, ou en retirer une, doit faire rougir.
  const LIGNES = ['le grand nombre', 'la légende', 'les suivants', 'le délai'];

  it('dimensionne les quatre lignes en vw', () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    assert.equal(trouves.length, LIGNES.length,
      `${trouves.length} ligne(s) en clamp, attendu ${LIGNES.length} : ${LIGNES.join(', ')}`);
    trouves.forEach((c, i) => {
      assert.match(c.prefere, /vw$/,
        `${LIGNES[i]} : la valeur préférée « ${c.prefere} » ne suit pas la dalle`);
    });
  });

  it("garde l'ordre de lecture du mur", () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    for (let i = 1; i < trouves.length; i += 1) {
      assert.ok(trouves[i].plafondPx < trouves[i - 1].plafondPx,
        `${LIGNES[i]} (${trouves[i].plafondPx} px) n'est pas plus petite que `
        + `${LIGNES[i - 1]} (${trouves[i - 1].plafondPx} px)`);
    }
  });

  // 48 px à 1080p → capitale de 21,3 mm → lisible à 4,26 m. En dessous, la
  // dernière ligne repasse sous la promesse de l'écran.
  it('tient les quatre mètres sur la plus petite ligne', () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    const derniere = trouves[trouves.length - 1];
    assert.ok(derniere.plafondPx >= 48,
      `le délai plafonne à ${derniere.plafondPx} px : capitale de `
      + `${Math.round(derniere.plafondPx * 0.7 * 0.634 * 10) / 10} mm, `
      + 'soit moins de quatre mètres');
  });
});
