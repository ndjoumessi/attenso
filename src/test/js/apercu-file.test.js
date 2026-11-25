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
    // Les couples, et non les valeurs seules : « 4 » ou « 0 » isolés se
    // trouvent partout dans un fichier. Et la liste couvre LES ONZE valeurs
    // dessinées, parce que la première version n'en tenait que trois — elle a
    // laissé passer une tuile inventée.
    const attendus = [
      ["['En salle', '4']", 'tuile « en salle »'],
      ["['Partis sans être vus', '0']", 'tuile des départs'],
      ["['Consultation médiane', '17 min']", 'tuile de la médiane'],
      ["numero: '7'", 'ticket appelé'],
      ["numero: '8'", 'ticket prévenu'],
      ["numero: '9'", 'ticket en attente'],
      ["'13 min'", "attente du ticket 7"],
      ["'11 min'", "attente du ticket 8"],
      ["'6 min'", "attente du ticket 9"],
      ['Chat stressé en caisse', 'motif du ticket 7'],
      ['Personne âgée', 'motif du ticket 8'],
    ];
    for (const [valeur, quoi] of attendus) {
      assert.ok(source.includes(valeur),
        `${quoi} : « ${valeur} » absent — la figure ne montre plus ce que la clinique de démonstration affiche`);
    }
  });

  /**
   * Un lecteur d'écran qui lirait ces lignes les annoncerait comme de vrais
   * patients en attente, sur une page commerciale. La figure est donc masquée
   * de l'arbre d'accessibilité, et c'est le texte autour d'elle qui porte le
   * sens.
   */
  it("masque le dessin aux lecteurs d'écran, mais pas sa légende", () => {
    const source = sansCommentaires(lire(APERCU));

    // Ancré sur le DESSIN, pas sur le fichier : posé ailleurs — sur la liste
    // par exemple — les trois tuiles redeviendraient lues comme des chiffres
    // de clinique réels, et un balayage du fichier entier n'y verrait rien.
    const figure = source.slice(source.indexOf('<figure'));
    const dessin = figure.slice(figure.indexOf('<div'), figure.indexOf('<figcaption'));
    assert.match(dessin.slice(0, 200), /aria-hidden=(?:"true"|\{true\})/,
      "le dessin n'est pas masqué aux lecteurs d'écran");

    // Et la légende DOIT rester lue : sans elle la figure n'existe pas pour un
    // lecteur d'écran, et le héros retombe sur le défaut qu'il corrige —
    // décrire le produit sans jamais le montrer.
    assert.match(source, /<figcaption/, 'la figure n\'a pas de légende');
    const legende = source.slice(source.indexOf('<figcaption'));
    assert.doesNotMatch(legende.slice(0, 160), /aria-hidden/,
      'la légende est masquée : la figure devient muette');
  });
});
