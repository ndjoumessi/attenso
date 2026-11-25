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
    const lecture = source.indexOf('doitOublierLeJournal(');
    const ecriture = source.indexOf('setItem(CLE_SLUG');
    assert.ok(lecture > 0, 'doitOublierLeJournal n\'est plus appelé');
    assert.ok(ecriture > 0, 'le slug n\'est plus écrit');

    // Des INDICES, pas une distance. Une expression « lecture, puis écriture
    // dans les 200 caractères » prouve qu'UNE écriture suit la lecture ;
    // elle ne prouve pas qu'AUCUNE ne la précède. En ajouter une avant le
    // `if` tuait le comportement — `getItem` rendait le slug neuf, la
    // comparaison était toujours vraie — sans rien faire rougir.
    assert.ok(lecture < ecriture,
      'le slug est écrit avant d\'être lu : le journal ne sera jamais vidé '
      + 'en changeant de clinique, et la file de l\'une s\'affichera chez l\'autre');

    // Et la CONSÉQUENCE, pas seulement l'ordre : sans cet appel, la comparaison
    // est juste et ne sert à rien.
    assert.match(source, /viderJournal\(\)/, 'le journal n\'est jamais vidé');
  });

  /**
   * GARDE DE NON-RÉGRESSION. Comportement n°2 des quatorze : le mot de passe
   * quitte la mémoire de la page dès qu'il est parti, pour qu'un vidage mémoire
   * ou une extension ne le retrouve pas dans le champ.
   */
  it('vide le mot de passe de la page au succès', () => {
    const source = sansCommentaires(lire(CONNEXION));
    // Borné au CHEMIN DE SUCCÈS, entre l'appel et la remise du jeton.
    // Déplacé dans le `catch`, le vidage n'aurait plus lieu quand la connexion
    // RÉUSSIT — c'est-à-dire dans le seul cas qui compte —, et un balayage du
    // fichier n'y verrait que du feu.
    assert.match(source, /\.mdp\.value\s*=\s*['"]['"][\s\S]{0,120}?surConnexion\(/,
      'le mot de passe n\'est pas vidé sur le chemin de succès');
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

    // `place-items-center` SEUL, sans variante de point de rupture. La première
    // version acceptait `sm:items-center` par une alternative trop large — donc
    // elle autorisait le retour exact de l'erreur qu'un commit a retirée :
    // conditionner le centrage à la LARGEUR pour un risque de HAUTEUR.
    assert.match(source, /place-items-center/, 'le contenu n\'est pas centré');
    assert.doesNotMatch(source, /\b(?:sm|md|lg|xl):items-(?:center|start)/,
      'le centrage est de nouveau conditionné à la largeur');

    // Et la CARTE, pas seulement le centrage : sans ces trois classes sur un
    // même élément, le formulaire nu revient, simplement centré.
    assert.match(
      source,
      /<div className="[^"]*\bbg-surface\b[^"]*"|<div className="[\s\S]{0,200}?\bbg-surface\b[\s\S]{0,200}?"/,
      'la carte n\'a plus de surface',
    );
    for (const classe of ['border-bordure-douce', 'rounded-[var(--rayon-lg)]', 'shadow-2']) {
      assert.ok(source.includes(classe), `la carte a perdu « ${classe} »`);
    }
  });
});
