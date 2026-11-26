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
    // Repéré par sa CONDITION et non par sa position. Cette garde prenait le
    // premier `clamp` du fichier, ce qui désignait le grand nombre tant qu'il
    // était le premier à en porter un. Le jour où l'en-tête est passé en vw,
    // elle a changé de bloc sans changer de nom : ici elle a rougi, mais si
    // l'en-tête n'avait pas eu de ternaire elle serait restée verte en
    // examinant le mauvais paragraphe — un proxy de position, exactement la
    // forme de garde qui ne prouve rien.
    const grand = source.match(/aUnNumero\s*&&\s*\([\s\S]{0,400}?<\/p>/)?.[0] ?? '';
    assert.ok(grand, 'le bloc du grand nombre est introuvable');
    assert.match(grand, /text-\[clamp/,
      "le bloc trouvé n'est pas en typographie d'affichage : la garde vise à côté");
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

  // L'ordre du source : l'en-tête d'abord — il est en haut de l'écran — puis
  // l'ordre de lecture, nombre, légende, suivants, délai. Le compte est piné :
  // en ajouter une sans y penser, ou en retirer une, doit faire rougir.
  const ENTETE = 'le nom de la clinique';
  const LIGNES = ['le grand nombre', 'la légende', 'les suivants', 'le délai'];
  const TOUTES = [ENTETE, ...LIGNES];

  /** L'en-tête est le premier du source ; les quatre autres sont le corps. */
  const corps = (trouves) => trouves.slice(1);

  it('dimensionne les cinq lignes en vw', () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    assert.equal(trouves.length, TOUTES.length,
      `${trouves.length} ligne(s) en clamp, attendu ${TOUTES.length} : ${TOUTES.join(', ')}`);
    trouves.forEach((c, i) => {
      assert.match(c.prefere, /vw$/,
        `${TOUTES[i]} : la valeur préférée « ${c.prefere} » ne suit pas la dalle`);
    });
  });

  /**
   * L'en-tête est le premier du source et le dernier de l'attention : il dit
   * où l'on est, pas ce qui arrive. Une garde qui exigerait l'ordre strict
   * sur les cinq le classerait en tête parce qu'il est en haut du balisage —
   * elle garderait la POSITION et non la hiérarchie. On vérifie donc l'ordre
   * sur le corps, et séparément que l'en-tête ne domine rien.
   */
  it("garde l'ordre de lecture du mur", () => {
    const trouves = corps(clamps(sansCommentaires(lire(ECRAN))));
    for (let i = 1; i < trouves.length; i += 1) {
      assert.ok(trouves[i].plafondPx < trouves[i - 1].plafondPx,
        `${LIGNES[i]} (${trouves[i].plafondPx} px) n'est pas plus petite que `
        + `${LIGNES[i - 1]} (${trouves[i - 1].plafondPx} px)`);
    }
  });

  it("n'accorde pas à l'en-tête plus de poids qu'aux lignes d'appui", () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    const plusPetiteDuCorps = Math.min(...corps(trouves).map((c) => c.plafondPx));
    assert.ok(trouves[0].plafondPx <= plusPetiteDuCorps,
      `${ENTETE} plafonne à ${trouves[0].plafondPx} px et passe devant `
      + `la plus petite ligne d'appui (${plusPetiteDuCorps} px)`);
    // Fenêtre BORNÉE : le clamp doit porter le paragraphe qui REND le nom.
    // « un clamp existe quelque part dans le fichier » resterait vert si
    // l'en-tête gardait ses paliers et qu'un cinquième clamp vivait ailleurs.
    assert.match(sansCommentaires(lire(ECRAN)),
      /text-\[clamp\([^)]*\)\][\s\S]{0,300}?vue\.clinique/,
      "le nom de la clinique n'est pas dimensionné par un clamp");
  });

  // 48 px à 1080p → capitale de 21,3 mm → lisible à 4,26 m. En dessous, une
  // ligne repasse sous la promesse de l'écran. Sur TOUTES les lignes, et non
  // sur la dernière du source : depuis que l'en-tête est dimensionné comme
  // les autres, la plus petite n'est plus forcément la dernière écrite.
  it('tient les quatre mètres sur la plus petite ligne', () => {
    const trouves = clamps(sansCommentaires(lire(ECRAN)));
    const plusPetite = trouves.reduce((a, b) => (b.plafondPx < a.plafondPx ? b : a));
    assert.ok(plusPetite.plafondPx >= 48,
      `la plus petite ligne plafonne à ${plusPetite.plafondPx} px : capitale de `
      + `${Math.round(plusPetite.plafondPx * 0.7 * 0.634 * 10) / 10} mm, `
      + 'soit moins de quatre mètres');
  });
});
