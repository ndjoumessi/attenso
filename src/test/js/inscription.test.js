import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const INSCRIPTION = 'front/src/inscription.jsx';

describe("la page d'inscription", () => {
  /**
   * Le défaut : sans réglage Stripe, la personne remplissait tout — nom,
   * adresse, mot de passe de douze caractères, formule, conditions — avant
   * d'apprendre en 503 que c'était fermé. La vitrine lui affirme pourtant que
   * « l'inscription prend trois minutes », et son appel à l'action principal
   * mène là.
   *
   * La page lit désormais le drapeau que le serveur sert avec les paliers, et
   * ne montre pas un formulaire qu'elle ne peut pas honorer.
   */
  it("ne montre pas de formulaire quand la souscription est fermée", () => {
    const source = sansCommentaires(lire(INSCRIPTION));
    assert.match(source, /ouverte/,
      "la page ne lit pas si la souscription est ouverte");
    // Le formulaire est conditionné, et la condition précède sa balise.
    const form = source.indexOf('<form onSubmit={soumettre}');
    assert.ok(form > 0, 'le formulaire est introuvable');
    assert.ok(source.lastIndexOf('ouverte', form) > 0,
      'rien ne conditionne le formulaire à l\'ouverture de la souscription');
  });

  /**
   * Et quand c'est fermé, la personne doit repartir avec quelque chose
   * d'actionnable. Un courriel en texte brut qu'il faut recopier n'en est pas.
   */
  it('donne un contact cliquable quand la souscription est fermée', () => {
    const source = sansCommentaires(lire(INSCRIPTION));
    assert.match(source, /href="mailto:contact@attenso\.fr"/,
      'le contact n\'est pas cliquable');
  });

  /**
   * Même cul-de-sac que `/connexion` avant sa correction : on arrive ici
   * depuis la vitrine, et rien n'en ressortait.
   */
  it("offre une sortie vers le site et vers la connexion", () => {
    const source = sansCommentaires(lire(INSCRIPTION));
    assert.ok(source.includes('href="/"'), 'pas de retour au site');
    assert.ok(source.includes('href="/connexion"'), 'pas de lien « déjà un compte »');
  });
});
