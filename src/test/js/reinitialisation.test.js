import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const PAGE = 'front/src/reinitialisation.jsx';

describe('la page de réinitialisation', () => {
  /**
   * On arrive ici par un lien de courriel, et c'est souvent sur un lien PÉRIMÉ
   * — ils ne valent qu'une heure et qu'une fois. Le message disait « redemandez
   * -en un depuis la page de connexion » sans jamais y mener : la personne
   * devait deviner l'adresse, sur un téléphone, après un échec.
   */
  it('mène à la connexion quand le lien est périmé', () => {
    const source = sansCommentaires(lire(PAGE));
    // Une sortie dans CHACUN des deux états rendus, et non « quelque part dans
    // le fichier ». Ma première version comparait des positions dans le
    // source : le bloc de succès est écrit avant le retour principal, donc
    // elle déclarait absente une sortie qui était bien là. Elle tenait un
    // ordre d'écriture, pas une propriété.
    // Le bloc de succès contient SON PROPRE `return (` : prendre le premier
    // après `if (fait)` le découperait à vide. C'est le deuxième qu'il faut.
    const debutSucces = source.indexOf('if (fait)');
    const retourSucces = source.indexOf('return (', debutSucces);
    const debutFormulaire = source.indexOf('return (', retourSucces + 1);
    assert.ok(debutSucces > 0 && debutFormulaire > retourSucces,
      'les deux états sont introuvables');
    const etatSucces = source.slice(debutSucces, debutFormulaire);
    const etatFormulaire = source.slice(debutFormulaire);
    assert.ok(etatSucces.includes('href="/connexion"'),
      'pas de sortie après un changement réussi');
    assert.ok(etatFormulaire.includes('href="/connexion"'),
      'pas de sortie sur le formulaire, donc aucune sur l\'état de lien périmé');
  });

  /**
   * Le bouton de succès disait « Aller au tableau d'accueil ». Il mène à un
   * formulaire de connexion : la personne vient de changer son mot de passe,
   * elle n'est pas connectée. Promettre le tableau pour livrer un formulaire
   * est un petit mensonge, et c'est le dernier écran qu'elle voit.
   */
  it('ne promet pas le tableau là où elle livre une connexion', () => {
    const source = sansCommentaires(lire(PAGE));
    assert.doesNotMatch(source, /Aller au tableau d'accueil/,
      'le bouton promet un tableau et livre un formulaire de connexion');
  });
});
