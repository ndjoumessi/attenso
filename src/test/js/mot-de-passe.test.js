import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');

/** Résolu depuis ce fichier, pas depuis le répertoire courant. */
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

/** Le corps d'un composant, de sa déclaration à la fonction suivante. */
function corps(source, nom) {
  const debut = source.indexOf(`function ${nom}(`);
  assert.ok(debut > 0, `composant ${nom} introuvable`);
  const suite = source.indexOf('\nfunction ', debut + 1);
  return source.slice(debut, suite === -1 ? undefined : suite);
}

describe('les gestionnaires de mots de passe', () => {
  /**
   * Un navigateur n'enregistre un mot de passe que s'il sait À QUEL COMPTE le
   * rattacher. Un formulaire qui ne porte qu'un `new-password`, sans champ
   * `username`, ne déclenche pas l'invite d'enregistrement — ou l'enregistre
   * sous une identité vide.
   *
   * Le champ peut être caché : c'est le motif que le W3C et les éditeurs de
   * navigateurs recommandent pour les formulaires de changement.
   */
  it('le changement de mot de passe porte l\'identifiant du compte', () => {
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    const mdp = corps(source, 'MotDePasse');
    assert.match(mdp, /autoComplete="username"/,
      'sans champ `username`, le navigateur ne sait pas quel compte mettre à jour');
    assert.match(mdp, /autoComplete="current-password"/);
    assert.match(mdp, /autoComplete="new-password"/);
  });

  /**
   * L'inscription déclarait `email` là où la connexion déclare `username`.
   * Ce sont deux clés différentes pour le même compte : le gestionnaire
   * enregistre sous l'une et ne remplit pas sous l'autre.
   */
  it('inscription et connexion nomment l\'identifiant de la même façon', () => {
    const inscription = sansCommentaires(lire('front/src/inscription.jsx'));
    const connexion = sansCommentaires(lire('front/src/accueil/Connexion.jsx'));
    assert.match(connexion, /autoComplete="username"/);
    assert.match(inscription, /autoComplete="username"/,
      'l\'inscription nomme l\'identifiant autrement que la connexion');
  });
});
