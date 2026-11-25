import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const SUIVI = 'front/src/suivi.jsx';

/**
 * La page du client. Les trois décisions qu'elle prend — montrer la carte,
 * offrir « j'arrive », offrir la notification — vivent dans `vue-suivi.js`
 * et y sont éprouvées sur les huit statuts. Ce fichier ne garde qu'une chose,
 * celle qu'un test de module ne peut pas voir : que la page les APPELLE.
 *
 * Une décision juste dans un module que personne n'interroge est exactement
 * la forme qu'avait le défaut.
 */
describe('la page de suivi applique les décisions de vue-suivi', () => {
  it('conditionne la grande carte à doitMontrerLaCarte', () => {
    const source = sansCommentaires(lire(SUIVI));
    assert.match(source, /doitMontrerLaCarte\(etat,\s*erreur\)/,
      'la carte ne consulte plus la décision');
    // Fenêtre BORNÉE : la condition doit précéder l'étiquette de la carte.
    // Un simple « les deux chaînes sont présentes » resterait vert si la
    // condition était posée ailleurs et la carte rendue sans elle.
    assert.match(source, /doitMontrerLaCarte\([\s\S]{0,400}?Personnes avant vous/,
      "la carte s'affiche hors de la condition : un lien mort montrera « chargement… » sans fin");
  });

  it("conditionne « je suis à quelques minutes » au statut", () => {
    const source = sansCommentaires(lire(SUIVI));
    assert.match(source, /peutSignalerSonArrivee\([^)]*statut/,
      "le geste n'est plus décidé par le statut du ticket");
    assert.match(source, /peutSignalerSonArrivee\([\s\S]{0,300}?Je suis à quelques minutes/,
      'le bouton est rendu hors de sa condition');
  });

  it('conditionne la notification au statut, en plus du support du navigateur', () => {
    const source = sansCommentaires(lire(SUIVI));
    assert.match(source, /peutDemanderUneNotification\([^)]*statut/,
      "la notification n'est plus décidée par le statut du ticket");
    assert.match(source, /peutDemanderUneNotification\([\s\S]{0,300}?Me prévenir par notification/,
      'le bouton est rendu hors de sa condition');
    // Le support du navigateur reste exigé : trois conditions réelles le
    // portent (service worker, PushManager, clé configurée côté service).
    assert.match(source, /pushPossible/, 'le support du navigateur n\'est plus vérifié');
  });

  /**
   * Le bloc des gestes ne doit pas laisser une marge vide quand aucun geste
   * ne s'applique : sur un ticket clos, la page montrerait un trou entre
   * « Consultation terminée » et le rappel du numéro.
   */
  it('ne garde pas le conteneur des gestes quand aucun geste ne reste', () => {
    const source = sansCommentaires(lire(SUIVI));
    assert.match(source, /\(peutSignalerSonArrivee\([^)]*\)\s*\|\|\s*(pushPossible|peutDemanderUneNotification)|gestes\s*&&/,
      'le conteneur des gestes est rendu sans condition');
  });
});
