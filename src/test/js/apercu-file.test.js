import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');

/**
 * Le source d'un fichier du dépôt.
 *
 * <p>Résolu depuis l'emplacement de ce test et non depuis le répertoire
 * courant : `node --test src/test/js/…` et `mvnw verify` ne partent pas du
 * même endroit, et un chemin relatif nu rendrait le test vert ou rouge selon
 * qui le lance.
 */
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/** Sans les commentaires : une propriété citée en prose n'est pas tenue. */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const APERCU = 'front/src/composants/apercu-file.jsx';

describe("l'aperçu de la file", () => {
  /**
   * L'aperçu est un DESSIN, pas une capture : rien ne l'empêche donc
   * d'afficher des chiffres flatteurs qui ne correspondent à aucune clinique.
   * Une page qui refuse d'inventer des témoignages — la section « Où en est
   * Attenso » le dit en toutes lettres — ne peut pas inventer une file.
   * Ceux-ci sont ceux de la clinique de démonstration.
   */
  it('montre les chiffres de la clinique de démonstration, pas des chiffres inventés', () => {
    const source = sansCommentaires(lire(APERCU));
    for (const vrai of ['17 min', 'Chat stressé en caisse', 'Personne âgée']) {
      assert.ok(source.includes(vrai), `« ${vrai} » absent de l'aperçu`);
    }
  });

  /**
   * Un lecteur d'écran qui lirait ces lignes les annoncerait comme de vrais
   * patients en attente, sur une page commerciale. La figure est donc masquée
   * de l'arbre d'accessibilité, et c'est le texte autour d'elle qui porte le
   * sens.
   */
  it("est décoratif : il ne s'annonce pas comme une vraie file", () => {
    const source = sansCommentaires(lire(APERCU));
    assert.match(source, /aria-hidden="true"/);
  });
});
