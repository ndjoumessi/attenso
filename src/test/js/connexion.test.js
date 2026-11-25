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

const CONNEXION = 'front/src/accueil/Connexion.jsx';

describe("la connexion au tableau", () => {
  /**
   * GARDE DE NON-RÉGRESSION, déclarée comme telle : la propriété est déjà
   * vraie, ce test n'a donc pas de phase rouge. Ce qu'il tient, c'est le jour
   * où quelqu'un réordonnera ces lignes en les croyant interchangeables.
   *
   * `doitOublierLeJournal` compare l'ANCIEN slug au nouveau. Inversé — l'écriture
   * d'abord —, le comparant serait le slug neuf, les deux seraient toujours
   * égaux, et le journal ne serait JAMAIS vidé en changeant de clinique : la
   * file et les gestes en attente de l'une s'afficheraient chez l'autre, sur
   * la même tablette. C'est le comportement n°1 des quatorze ; `vue-file.test.js`
   * en teste le prédicat, personne n'en testait l'appel.
   */
  it("oublie le journal AVANT de réécrire le slug, pas après", () => {
    const source = sansCommentaires(lire(CONNEXION));
    assert.match(
      source,
      /doitOublierLeJournal\(\s*localStorage\.getItem\(CLE_SLUG\)[\s\S]{0,200}?setItem\(CLE_SLUG/,
      "l'ordre lecture-puis-écriture du slug n'est plus tenu",
    );
  });

  /**
   * GARDE DE NON-RÉGRESSION. Comportement n°2 des quatorze : le mot de passe
   * quitte la mémoire de la page dès qu'il est parti, pour qu'un vidage mémoire
   * ou une extension ne le retrouve pas dans le champ.
   */
  it('vide le mot de passe de la page au succès', () => {
    const source = sansCommentaires(lire(CONNEXION));
    assert.match(source, /\.mdp\.value\s*=\s*''/);
  });

  /**
   * C'est le premier écran qu'une clinique voit après s'être inscrite, et il
   * était un formulaire nu aligné en haut à gauche. La carte centrée le met au
   * même niveau que le tableau qu'il ouvre.
   *
   * `min-h-dvh` et non `min-h-screen` : sur mobile, `100vh` inclut la barre
   * d'adresse qui se rétracte, ce qui fait sauter la mise en page au défilement.
   */
  it('est une carte centrée, pas un formulaire nu', () => {
    const source = sansCommentaires(lire(CONNEXION));
    assert.match(source, /min-h-dvh/, 'la page ne prend pas la hauteur de la vue');
    assert.doesNotMatch(source, /min-h-screen/, '100vh saute sur mobile, utiliser dvh');
    assert.match(source, /place-items-center|items-center/, 'le contenu n\'est pas centré');
  });
});
