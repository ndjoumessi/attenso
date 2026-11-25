import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/** Le source sans commentaires : ces gardes portent sur ce que le code FAIT. */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .filter((l) => !/^\s*(\/\/|\*)/.test(l))
  .join('\n');

/**
 * Le corps d'un composant : de son `function Nom(` à la fonction de premier
 * niveau suivante. Une fenêtre BORNÉE, pas un balayage du fichier : le setter
 * d'une autre vue ne doit pas satisfaire la garde de celle-ci.
 */
function corpsDe(fichier, nom) {
  const source = sansCommentaires(lire(fichier));
  const debut = source.search(new RegExp(`(?:export )?function ${nom}\\(`));
  assert.notEqual(debut, -1, `${nom} introuvable dans ${fichier}`);
  const reste = source.slice(debut + 1);
  const fin = reste.search(/\n(?:export )?function /);
  return fin === -1 ? reste : reste.slice(0, fin);
}

/**
 * Le tableau CÂBLE ce que la barre déclare. Les gardes de contenu (navigation,
 * Barre, ui/) lisent chaque pièce isolément ; aucune ne voyait qu'on pouvait
 * retirer la barre, son abonnement ou son clic en laissant tout vert.
 */
describe('le tableau câble la navigation', () => {
  const tableau = sansCommentaires(lire('front/src/accueil.jsx'));

  it('monte la barre latérale', () => {
    assert.match(tableau, /<Barre\b[^>]*\bsurChangement=\{allerA\}/,
      'la barre n\'est plus rendue, ou son clic ne mène plus à `allerA`');
  });

  it('monte le déclencheur du tiroir sous le seuil mobile', () => {
    assert.match(tableau, /<OuvreLaBarre\s*\/>/,
      'sous 900 px, plus aucun accès au menu');
  });

  it('allerA écrit le fragment', () => {
    assert.match(tableau, /const allerA = \(cle\) => \{ location\.hash = cle; \};/,
      '`allerA` n\'écrit plus le fragment : cliquer ne change rien');
  });

  it('relit le fragment à chaque hashchange, et se désabonne', () => {
    assert.match(tableau,
      /const relire = \(\) => setVue\(vuePour\(location\.hash\)\);\s*addEventListener\('hashchange', relire\);\s*return \(\) => removeEventListener\('hashchange', relire\);/,
      'le tableau ne suit plus le fragment : la barre est morte');
  });

  it('la barre transmet le clic à surChangement', () => {
    const corps = corpsDe('front/src/accueil/Barre.jsx', 'Barre');
    assert.match(corps, /surChangement\(cle\);/,
      'la barre ne remonte plus la vue demandée');
    assert.match(corps, /compteur=\{vue\.cle === 'file' \? enFile : null\}/,
      'le badge de la file n\'est plus alimenté');
  });
});

/**
 * Le chemin de données de chaque vue : l'appel peuple SON état. Sans ça la vue
 * reste à « — » ou vide pour toujours, tests verts.
 */
describe('chaque vue range la réponse dans son état', () => {
  const TABLE = [
    ['File : les lignes sont rendues', 'front/src/accueil/File.jsx', 'File',
      /\{vue\.lignes\.map\(\(donnees\) =>/],
    ['Mesure', 'front/src/accueil/Mesure.jsx', 'Mesure',
      /\.then\(\(recue\) => \{\s*setMesure\(recue\);/],
    ['Exploitation : tableau de bord', 'front/src/accueil/Exploitation.jsx', 'Exploitation',
      /\.then\(\(recu\) => \{ setBord\(recu\);/],
    ['Exploitation : facturation', 'front/src/accueil/Exploitation.jsx', 'Exploitation',
      /\.then\(\(recue\) => \{ setFacturation\(recue\);/],
    ['Gestes : motifs', 'front/src/accueil/Gestes.jsx', 'Gestes',
      /\.then\(\(recus\) => \{ setMotifs\(recus\);/],
    ['Comptes : l\'équipe est lue au montage', 'front/src/accueil/Exploitation.jsx', 'Comptes',
      /useEffect\(\(\) => \{ relireEquipe\(\); \}, \[jeton\]\);/],
    ['Comptes : la réponse peuple l\'équipe', 'front/src/accueil/Exploitation.jsx', 'Comptes',
      /setEquipe\(await appel\('\/api\/equipe'/],
    ['Agenda', 'front/src/accueil/Sections.jsx', 'Agenda',
      /\.then\(\(recus\) => \{ setRdvs\(recus\);/],
    ['Carnets', 'front/src/accueil/Sections.jsx', 'Carnets',
      /\.then\(\(recus\) => \{ setAnimaux\(recus\);/],
  ];

  for (const [nom, fichier, composant, motif] of TABLE) {
    it(nom, () => {
      assert.match(corpsDe(fichier, composant), motif,
        `${nom} : la réponse n'atteint plus son état, la vue reste vide ou à « — »`);
    });
  }
});

/**
 * Trois états, pas deux, pour Mesure et Carnets : l'échec est LOCAL. Le bandeau
 * partagé du haut de page est effacé par `rafraichir` à chaque succès, donc à
 * chaque événement SSE : une erreur poussée là disparaît avant d'être lue.
 */
describe('Mesure et Carnets disent leur échec eux-mêmes', () => {
  const CAS = [
    ['Mesure', 'front/src/accueil/Mesure.jsx', /\.then\(\(recue\) => \{\s*setMesure\(recue\);\s*setEchec\(false\);/,
      "La mesure n'a pas pu être chargée"],
    ['Carnets', 'front/src/accueil/Sections.jsx',
      /\.then\(\(recus\) => \{ setAnimaux\(recus\); setEchec\(false\); \}\)\s*\.catch\(\(\) => setEchec\(true\)\)/,
      "Les carnets n'ont pas pu être chargés"],
  ];

  for (const [composant, fichier, succes, message] of CAS) {
    it(`${composant} : la remise à zéro est liée au succès, le catch pose l'échec`, () => {
      const corps = corpsDe(fichier, composant);
      assert.match(corps, succes, 'le succès ne remet pas l\'échec à zéro dans la même expression');
      assert.match(corps, /\.catch\(\(\) => setEchec\(true\)\)/, 'le catch ne pose pas l\'échec');
    });

    it(`${composant} : l'échec se dit, avec le gabarit « n'a pas pu être … Réessayez »`, () => {
      assert.match(corpsDe(fichier, composant),
        new RegExp(`\\{echec && \\(\\s*<div className="mt-4">\\s*<Erreur>\\s*${message}\\. Réessayez dans un instant\\.\\s*</Erreur>`),
        'l\'échec du chargement ne se dit pas');
    });
  }
});
