import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

/** Imports retirés EN PLUS des commentaires : une garde qui cherche
 *  « BandeLegale » dans le fichier trouve la ligne d'import et reste verte
 *  quand le composant n'est plus rendu. Mutation mesurée, et c'est la
 *  deuxième fois dans la même journée que ce piège se referme. */
const rendu = (source) => sansCommentaires(source)
  .replace(/^import[\s\S]*?;$/gm, '');

const CHROME = 'front/src/composants/chrome.jsx';

/** Toutes les pages et tous les composants, sans exception nommée à la main :
 *  un fichier ajouté demain entre dans la garde sans qu'on y pense. */
const TOUS = ['front/src', 'front/src/accueil', 'front/src/composants']
  .flatMap((dossier) => readdirSync(resolve(RACINE, dossier))
    .filter((f) => f.endsWith('.jsx'))
    .map((f) => `${dossier}/${f}`));

/**
 * La bande légale était écrite TROIS fois — vitrine, pages légales,
 * connexion — et le retour au site QUATRE. Une seule copie portait les
 * classes qui donnent 44 px, celle corrigée le matin même : mesuré en
 * production, le pied de la vitrine rendait cinq cibles à 17 px et la
 * coquille légale autant.
 *
 * Corriger les copies une par une, c'est ce qui a produit le deuxième
 * défaut le même jour. La garde porte donc sur l'UNICITÉ, pas sur les
 * classes de chaque page : tant qu'il n'y a qu'un endroit, il n'y a qu'un
 * endroit à mesurer.
 */
describe('la coquille partagée des pages publiques', () => {
  it('est le seul endroit qui écrit la bande légale', () => {
    const coupables = TOUS.filter((chemin) => chemin !== CHROME
      && /mentions-legales/.test(sansCommentaires(lire(chemin))));
    assert.deepEqual(coupables, [],
      'la bande légale est réécrite ailleurs : une copie finira sous 44 px');
  });

  it('est le seul endroit qui écrit un retour en flèche', () => {
    const coupables = TOUS.filter((chemin) => chemin !== CHROME
      && /←/.test(sansCommentaires(lire(chemin))));
    assert.deepEqual(coupables, [], 'un retour en flèche est écrit hors de la coquille');
  });

  /**
   * `cible-min` règle une hauteur MINIMALE, qui n'a AUCUN effet sur un
   * élément en ligne. Les deux classes sont donc exigées ensemble : l'une
   * sans l'autre ne fait rien, et c'est précisément l'erreur qui a donné
   * 17 px alors que la bonne classe était posée.
   */
  it('donne à chacun de ses liens les deux classes qui, ensemble, font 44 px', () => {
    // Les constantes de classe sont RÉSOLUES avant de juger. Ma première
    // version exigeait les classes en littéral dans la balise ; elle était
    // rouge sur un composant correct, parce qu'un des liens passe par une
    // constante. C'est le test qui avait tort, et c'est dit plutôt que fait
    // en silence.
    const brut = sansCommentaires(lire(CHROME));
    const constantes = [...brut.matchAll(/const ([A-Z_]+) = ((?:'[^']*'\s*\+?\s*)+);/g)]
      .map(([, nom, valeur]) => [nom, valeur.match(/'[^']*'/g).join('').replaceAll("'", '')]);
    const source = constantes.reduce(
      (texte, [nom, valeur]) => texte.replaceAll(`{${nom}}`, `"${valeur}"`), brut,
    );
    assert.ok(constantes.length > 0, 'plus aucune constante de classe à résoudre');
    const liens = source.match(/<a[\s\S]*?>/g) ?? [];
    assert.ok(liens.length >= 2, 'la coquille ne rend plus de liens');
    for (const lien of liens) {
      assert.match(lien, /cible-min/, `lien sans plancher tactile : ${lien.slice(0, 50)}`);
      assert.match(lien, /inline-flex|inline-block/,
        `« cible-min » est sans effet en ligne : ${lien.slice(0, 50)}`);
    }
  });

  /** Les trois destinations légales, et le retour, restent joignables. */
  it('mène bien aux trois pages légales', () => {
    const source = sansCommentaires(lire(CHROME));
    for (const chemin of ['/mentions-legales', '/cgv', '/confidentialite']) {
      assert.ok(source.includes(`'${chemin}'`) || source.includes(`"${chemin}"`),
        `${chemin} manque`);
    }
  });

  it('est réellement employée par la vitrine et les pages légales', () => {
    for (const chemin of ['front/src/vitrine.jsx', 'front/src/composants/legal.jsx']) {
      assert.match(rendu(lire(chemin)), /<BandeLegale[\s/>]/,
        `${chemin} n'emploie pas la bande partagée`);
    }
  });
});
