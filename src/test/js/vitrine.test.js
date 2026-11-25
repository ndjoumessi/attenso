import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');

/** Résolu depuis ce fichier, pas depuis le répertoire courant : `node --test`
 *  et `mvnw verify` ne partent pas du même endroit. */
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const VITRINE = 'front/src/vitrine.jsx';

describe("le héros de la page d'accueil", () => {
  /**
   * Le défaut que cette page avait n'était ni sa couleur ni son texte : elle
   * DÉCRIVAIT l'écran que l'auxiliaire aura sous les doigts sans jamais le
   * MONTRER. Une clinique qui hésite veut le voir.
   *
   * La fenêtre est bornée au corps de `Heros` : un aperçu rendu plus bas dans
   * la page satisferait un balayage du fichier entier tout en laissant
   * l'ouverture inchangée, c'est-à-dire en laissant le défaut intact.
   */
  it('montre le produit, il ne fait pas que le décrire', () => {
    const source = sansCommentaires(lire(VITRINE));
    const debut = source.indexOf('function Heros(');
    const fin = source.indexOf('function Cout(');
    assert.ok(debut > 0 && fin > debut, 'le héros est introuvable');
    assert.match(source.slice(debut, fin), /<ApercuFile\s*\/>/,
      "le héros ne rend pas l'aperçu de la file");
  });

  /**
   * Doublon volontaire et borné de `frontiere.test.js`, qui balaye toutes les
   * entrées : celui-ci dit POURQUOI au prochain qui ouvrira ce fichier. La
   * vitrine est une page client ; les quatorze composants installés pour le
   * tableau ne doivent pas y entrer, sous peine d'alourdir la page scannée au
   * comptoir.
   */
  it("n'atteint aucun composant du tableau", () => {
    assert.doesNotMatch(lire(VITRINE), /from\s+['"][^'"]*\/ui\//);
  });
});
