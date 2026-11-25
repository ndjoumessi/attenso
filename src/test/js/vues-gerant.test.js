import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

import { doitContinuerARelire } from '../../../front/src/accueil/refus.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/**
 * Le même source, commentaires retirés : ces gardes portent sur ce que le code
 * FAIT. Même filtre, même convention, que `navigation.test.js`.
 */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .filter((l) => !/^\s*(\/\/|\*)/.test(l))
  .join('\n');

const EXPLOITATION = 'front/src/accueil/Exploitation.jsx';

/**
 * Le source d'UNE vue de `Exploitation.jsx`, de son `export function` au
 * suivant. Le fichier en porte deux : une garde qui lirait « n'importe où dans
 * le fichier » serait satisfaite par la mauvaise.
 */
function corpsDeVue(nom) {
  const source = sansCommentaires(lire(EXPLOITATION));
  const debut = source.indexOf(`export function ${nom}(`);
  assert.notEqual(debut, -1, `${nom} introuvable`);
  const suite = source.indexOf('\nexport function ', debut + 1);
  return suite === -1 ? source.slice(debut) : source.slice(debut, suite);
}

describe('un refus de l\'API n\'est pas une panne', () => {
  it('ne relit plus après un 401 ou un 403', () => {
    assert.equal(doitContinuerARelire(401), false);
    assert.equal(doitContinuerARelire(403), false);
  });

  it('relit après un succès', () => {
    assert.equal(doitContinuerARelire(200), true);
  });

  it('relit après une panne passagère : ce n\'est pas un refus', () => {
    assert.equal(doitContinuerARelire(500), true);
    assert.equal(doitContinuerARelire(502), true);
    assert.equal(doitContinuerARelire(503), true);
    // Pas de statut du tout : le réseau est tombé, il reviendra.
    assert.equal(doitContinuerARelire(undefined), true);
  });
});

describe('les vues réservées au gérant', () => {
  it('Exploitation.jsx expose les DEUX vues, et la relecture se nettoie', () => {
    const source = sansCommentaires(lire(EXPLOITATION));
    assert.match(source, /export function Exploitation\(/);
    assert.match(source, /export function Comptes\(/);
    assert.match(source, /ReserveAuGerant/);
    // Le setInterval est légitime ; c'est son nettoyage qui doit rester.
    assert.match(corpsDeVue('Exploitation'), /setInterval\([\s\S]*clearInterval/);
  });

  it('ne lit pas le rôle dans le jeton : c\'est le refus du serveur qui décide', () => {
    const source = sansCommentaires(lire(EXPLOITATION));
    assert.ok(!/atob\(|JSON\.parse\(|split\('\.'\)/.test(source),
      'la page décode le jeton pour décider du rôle');
  });

  for (const nom of ['Exploitation', 'Comptes']) {
    it(`${nom} rend « réservé au gérant » quand le refus est retenu`, () => {
      assert.match(corpsDeVue(nom), /\{refuse && \(?\s*<ReserveAuGerant\s+quoi=/,
        'ReserveAuGerant n\'est pas rendu sur l\'état de refus');
    });
  }

  // UNE assertion par catch, qui lie ensemble la polarité, l'effet et la
  // branche d'échec : trois jetons indépendants se contournent un par un.
  it('Exploitation : un refus de la facturation est retenu, affiché, et lu avant d\'appeler', () => {
    const corps = corpsDeVue('Exploitation');
    assert.match(corps,
      /if \(facturationRefusee\.current\) return;\s*appel\('\/api\/facturation'/,
      'la relecture ne consulte pas le refus AVANT d\'appeler la facturation');
    assert.match(corps,
      /\.catch\(\((\w+)\) => \{\s*if \(!doitContinuerARelire\(\1\.statut\)\) \{\s*facturationRefusee\.current = true;[^}]*?setRefuse\(true\);\s*\} else \{\s*setEchecFacturation\(true\);/,
      'le refus ne retient pas le drapeau, n\'affiche pas, ou la polarité est inversée');
    assert.match(corps, /useRef\(false\)/);
  });

  it('Comptes : un refus est affiché, une panne est une erreur', () => {
    assert.match(corpsDeVue('Comptes'),
      /catch \((\w+)\) \{\s*if \(!doitContinuerARelire\(\1\.statut\)\) \{[^}]*?setRefuse\(true\);\s*\} else \{\s*setEchec\(true\);\s*\}/,
      'le refus n\'est pas affiché, la polarité est inversée, ou le else porte autre chose qu\'un échec');
    // La remise à zéro est liée au chemin de succès : sans elle, un échec
    // passager resterait affiché à côté d'une liste relue avec succès.
    assert.match(corpsDeVue('Comptes'),
      /setEquipe\(await[^;]*\);\s*setRefuse\(false\);\s*setEchec\(false\);/,
      'le succès de la relecture ne remet pas l\'échec à zéro');
  });

  it('Comptes ne se relit pas en boucle : un seul appel, jamais d\'intervalle', () => {
    assert.ok(!/setInterval/.test(corpsDeVue('Comptes')));
  });

  describe('trois états, pas deux', () => {
    it('Comptes : pas encore chargé, chargé, échec', () => {
      const corps = corpsDeVue('Comptes');
      assert.match(corps, /\[equipe, setEquipe\] = useState\(null\)/,
        'la liste démarre autrement que `null` : faux « vide » au chargement');
      assert.match(corps, /\{!refuse && echec && \(\s*<div[^>]*>\s*<Erreur>\s*La liste des comptes/,
        'l\'échec ne se dit pas — y compris quand une liste est déjà affichée');
    });

    it('Exploitation : la facturation démarre à `null`, et son échec se dit', () => {
      const corps = corpsDeVue('Exploitation');
      assert.match(corps, /\[facturation, setFacturation\] = useState\(null\)/);
      assert.match(corps,
        /\{!refuse && facturation === null && echecFacturation && \(\s*<div[^>]*>\s*<Erreur>\s*La facturation n'a pas pu être chargée/,
        'l\'échec de la facturation ne se dit pas, ou sa condition n\'est plus liée');
    });
  });
});

describe('le titre n\'est pas écrit deux fois', () => {
  const tableau = sansCommentaires(lire('front/src/accueil.jsx'));
  const CAS = [
    { composant: 'Mesure', fichier: 'front/src/accueil/Mesure.jsx', titre: "Mesure de l'auto-enregistrement" },
    { composant: 'Exploitation', fichier: EXPLOITATION, titre: 'Exploitation' },
    { composant: 'Comptes', fichier: EXPLOITATION, titre: 'Comptes de la clinique' },
  ];

  for (const c of CAS) {
    it(`${c.composant} : la vue passe titre={false}, la section sait se taire`, () => {
      assert.match(tableau, new RegExp(`<${c.composant}\\b[^>]*\\btitre=\\{false\\}`),
        'la vue laisse la section redoubler le h1 de l\'en-tête');
      const source = sansCommentaires(lire(c.fichier));
      const debut = source.indexOf(`export function ${c.composant}(`);
      assert.notEqual(debut, -1);
      const suite = source.indexOf('\nexport function ', debut + 1);
      const corps = suite === -1 ? source.slice(debut) : source.slice(debut, suite);
      assert.match(corps, /export function \w+\(\{[^}]*\btitre = true\b[^}]*\}\)/,
        'la section ne déclare pas `titre = true`');
      assert.match(corps, new RegExp(`\\{titre && <Titre>${c.titre.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}</Titre>\\}`),
        'le Titre n\'est pas conditionné à la prop');
    });
  }

  it('le tableau rend Exploitation et Comptes comme deux vues distinctes', () => {
    assert.match(tableau, /vue === 'exploitation'\s*&&\s*<Exploitation\b/);
    assert.match(tableau, /vue === 'comptes'\s*&&\s*<Comptes\b/);
    assert.ok(!/vue === 'exploitation' \|\| vue === 'comptes'/.test(tableau),
      'les deux entrées de barre mènent encore au même écran');
  });
});
