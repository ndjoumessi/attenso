import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const MARQUE = 'front/src/composants/marque.jsx';
const ICONE = 'src/main/resources/static/app/icone.svg';

/**
 * Les trois traits, lus quelle que soit leur ÉCRITURE : attribut `d="…"` dans
 * le fichier SVG, chaîne d'un tableau dans le composant. Ma première version
 * n'acceptait que la première forme et déclarait le composant vide — le test
 * avait tort, pas le dessin.
 */
const traits = (source) => (source.match(/["']M[\d\s.,hvlHVLzZ-]+["']/g) ?? [])
  .map((d) => d.slice(1, -1).trim());

/**
 * L'application portait DEUX marques. L'icône installée — celle du manifeste
 * PWA — est un carré vert à trois barres blanches décroissantes : une file qui
 * raccourcit. L'en-tête de la vitrine, lui, dessinait une horloge. Le site et
 * l'icône ne se ressemblaient pas.
 *
 * <p>Et aucune des quatorze pages ne déclarait d'icône : tous les onglets
 * étaient vides. `/favicon.ico` rendait 401 — non par défaut de configuration,
 * le chemin EST autorisé, mais parce qu'aucun fichier n'existait et que la
 * requête retombait sur une route authentifiée.
 */
describe("l'application n'a qu'une marque", () => {
  it('la dessine à un seul endroit', () => {
    const source = sansCommentaires(lire(MARQUE));
    assert.match(source, /export function Marque/, 'la marque partagée n\'existe pas');
    assert.equal(traits(source).length, 3,
      'la marque n\'a pas ses trois traits décroissants');
  });

  it("l'en-tête de la vitrine l'emploie, au lieu de dessiner la sienne", () => {
    const source = sansCommentaires(lire('front/src/vitrine.jsx'))
      .replace(/^import[\s\S]*?;$/gm, '');
    assert.match(source, /<Marque\b/, "l'en-tête ne porte pas la marque partagée");
    // L'horloge d'avant : un cercle et deux aiguilles, dessinés sur place.
    assert.doesNotMatch(source, /<circle/,
      "l'en-tête redessine une marque à lui");
  });

  /**
   * Le fichier servi aux navigateurs et le composant React doivent porter la
   * MÊME géométrie. Deux dessins qui se ressemblent aujourd'hui divergent
   * demain, et c'est exactement ce qui vient d'arriver avec l'horloge.
   */
  it('le favicon et le composant dessinent la même chose', () => {
    const duComposant = traits(sansCommentaires(lire(MARQUE)));
    const duFichier = traits(lire(ICONE));
    assert.deepEqual(duFichier, duComposant,
      'le favicon et la marque de l\'en-tête ont divergé');
  });
});

describe('chaque page déclare son icône', () => {
  const PAGES = readdirSync(resolve(RACINE, 'front/app')).filter((f) => f.endsWith('.html'));

  it('les quatorze, sans exception nommée à la main', () => {
    assert.ok(PAGES.length >= 14, `seulement ${PAGES.length} pages trouvées`);
    const muettes = PAGES.filter((p) => !/rel="icon"/.test(lire(`front/app/${p}`)));
    assert.deepEqual(muettes, [],
      'ces pages laissent un onglet vide — une page ajoutée demain entre ici toute seule');
  });

  /**
   * Un SVG pour la netteté à toute taille, un PNG pour Safari d'avant 16 qui
   * ignore les favicons vectoriels. Les deux chemins sont sous `/app/`, déjà
   * public et déjà servi.
   */
  it('avec un repli pour les navigateurs sans favicon vectoriel', () => {
    for (const p of PAGES) {
      const tete = lire(`front/app/${p}`);
      assert.match(tete, /rel="icon"[^>]*image\/svg\+xml/, `${p} : pas de favicon vectoriel`);
      assert.match(tete, /rel="icon"[^>]*\.png/, `${p} : pas de repli PNG`);
    }
  });
});
