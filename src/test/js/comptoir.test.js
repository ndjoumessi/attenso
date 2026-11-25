import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/** Commentaires ET imports retirés : une assertion de POSITION qui compte
 *  l'import comme une occurrence serait verte quoi qu'il arrive. */
const corps = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '')
  .replace(/^import[\s\S]*?;$/gm, '');

const COMPTOIR = 'front/src/comptoir.jsx';

describe('le comptoir dit avant la saisie que l\'adresse est morte', () => {
  it("ne jette plus l'échec de lecture de la clinique", () => {
    const source = corps(lire(COMPTOIR));
    assert.match(source, /\.catch\(\s*\(?\s*echec/,
      "l'échec est de nouveau avalé : `.catch(() => {})`");
    assert.match(source, /adresseInactive\(\s*echec\s*\)/,
      "l'échec n'est plus diagnostiqué");
  });

  it('annonce le barrage AVANT le formulaire', () => {
    const source = corps(lire(COMPTOIR));
    const message = source.indexOf('ADRESSE_INACTIVE');
    const formulaire = source.indexOf('<form');
    assert.ok(message > 0, "le message n'est pas rendu");
    assert.ok(formulaire > 0, 'le formulaire a disparu');
    assert.ok(message < formulaire,
      'le message arrive après le formulaire : la personne aura déjà tapé son numéro');
  });

  it('empêche de saisir un numéro qui ne servira à rien', () => {
    const source = corps(lire(COMPTOIR));
    // Le CHAMP comme le BOUTON. Barrer le seul bouton laisse quelqu'un taper
    // son mobile en entier avant de découvrir qu'il n'y a pas de porte.
    const champ = source.match(/<input[\s\S]*?\/>/)?.[0] ?? '';
    const bouton = source.match(/<button type="submit"[\s\S]*?>/)?.[0] ?? '';
    assert.match(champ, /disabled=\{[^}]*inactive/, 'le champ reste saisissable');
    assert.match(bouton, /disabled=\{[^}]*inactive/, 'le bouton reste cliquable');
  });

  /**
   * Le premier rendu est PRÉ-RENDU dans le HTML : il ne doit dépendre
   * d'aucune donnée réseau, sans quoi l'hydratation diverge. L'état de
   * barrage part donc de `false`, et le pré-rendu montre le formulaire.
   */
  it('ne fait pas dépendre le premier rendu du réseau', () => {
    const source = corps(lire(COMPTOIR));
    // BORNÉ à l'état du barrage. Un simple `useState(false)` était vert
    // avant toute correction : `envoi` en est déjà un.
    assert.match(source, /\[\s*inactive\s*,\s*set\w+\s*\]\s*=\s*useState\(false\)/,
      "l'état de barrage ne part plus de `false` : l'hydratation va diverger");
  });
});
