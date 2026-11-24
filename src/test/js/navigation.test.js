import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

import { GROUPES, VUES, vuePour } from '../../../front/src/accueil/navigation.js';

const RACINE = resolve(import.meta.dirname, '../../..');

/**
 * Le source d'un fichier du dépôt.
 *
 * <p>Résolu depuis l'emplacement de ce test et non depuis le répertoire
 * courant : `node --test src/test/js/navigation.test.js` et `mvnw verify` ne
 * partent pas du même endroit, et un chemin relatif nu rendrait le test vert
 * ou rouge selon qui le lance.
 */
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/**
 * Le même source, commentaires retirés.
 *
 * <p>Ces gardes-ci interdisent des APPELS et des attributs, pas des mots. Sans
 * ce filtre, un commentaire qui explique pourquoi `history.pushState` est
 * écarté faisait rougir la garde contre `history.pushState` — constaté deux
 * fois en écrivant cette tâche. Le code perdrait alors le droit de dire ce
 * qu'il ne fait pas, ce qui est précisément ce qu'on veut qu'il dise.
 *
 * <p>Le projet n'écrit que des blocs `/* *\/` et des lignes entières en `//` :
 * le filtre ne prétend pas analyser JavaScript, il suit cette convention.
 */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .filter((l) => !/^\s*(\/\/|\*)/.test(l))
  .join('\n');

describe('le fragment désigne la vue', () => {
  it('rend la file pour un fragment inconnu, vide ou absent', () => {
    // Un favori posé sur une vue renommée ne doit pas donner un écran blanc.
    assert.equal(vuePour('#nimportequoi'), 'file');
    assert.equal(vuePour('#'), 'file');
    assert.equal(vuePour(''), 'file');
    assert.equal(vuePour(null), 'file');
  });

  it('rend la vue nommée par le fragment', () => {
    assert.equal(vuePour('#mesure'), 'mesure');
    assert.equal(vuePour('#comptes'), 'comptes');
  });

  it('sait relire le fragment qu\'il a lui-même écrit, pour chaque vue', () => {
    // `surChangement` écrit la CLÉ dans location.hash. Si une clé ne revenait
    // pas d'un aller-retour, son entrée de barre renverrait sur la file —
    // silencieusement, et seulement pour celle-là.
    for (const v of VUES) assert.equal(vuePour(`#${v.cle}`), v.cle);
  });

  it('déclare chaque vue dans un groupe connu, la file épinglée', () => {
    assert.equal(VUES[0].cle, 'file');
    assert.equal(VUES[0].groupe, null);
    for (const v of VUES.slice(1)) {
      assert.ok(GROUPES.includes(v.groupe), `groupe inconnu : ${v.groupe}`);
    }
  });

  it('porte les huit entrées de la conception, dans son ordre', () => {
    assert.deepEqual(VUES.map((v) => v.cle), [
      'file', 'rendez-vous', 'carnets', 'mesure', 'exploitation',
      'affiche', 'comptes', 'compte',
    ]);
    assert.deepEqual(GROUPES, ['JOURNÉE', 'PILOTAGE', 'CLINIQUE', 'COMPTE']);
  });

  it('donne un libellé à chaque vue, jamais une icône seule', () => {
    for (const v of VUES) assert.ok(v.libelle && v.libelle.length > 1);
  });
});

describe('le routage par fragment dans le tableau', () => {
  it('une navigation = une entrée d\'historique', () => {
    // Écrire location.hash suffit ; un pushState en plus doublerait les entrées
    // et il faudrait deux Retour pour revenir d'un clic.
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    assert.ok(!/history\.pushState/.test(source),
      'le fragment suffit : pushState doublerait l\'historique');
  });

  it('les bandeaux sont rendus hors du commutateur de vue', () => {
    // Le mode dégradé doit se voir sur TOUTES les vues : un bandeau enfermé dans
    // la vue « file » disparaîtrait au moment où il compte.
    const source = lire('front/src/accueil.jsx');
    assert.ok(source.includes('vue ==='), 'commutateur de vue introuvable');
    const commutateur = source.slice(source.indexOf('vue ==='));
    assert.ok(!/\<Bandeaux/.test(commutateur));
  });

  it('chaque entrée de la barre mène à une vue rendue', () => {
    // La panne qu'on ne verrait qu'en démonstration : une entrée déclarée dans
    // VUES mais oubliée dans le commutateur rend un écran vide.
    const source = lire('front/src/accueil.jsx');
    const absentes = VUES.filter((v) => !source.includes(`vue === '${v.cle}'`));
    assert.deepEqual(absentes.map((v) => v.cle), [],
      'entrée de barre sans vue : clic sur écran blanc');
  });
});

describe('la barre reste une barre à libellés', () => {
  const barre = sansCommentaires(lire('front/src/accueil/Barre.jsx'));

  it('n\'escamote jamais les libellés derrière une infobulle ou un sr-only', () => {
    // Le repli en rail d'icônes de shadcn remplace le libellé par un `tooltip`.
    // Des icônes muettes face à quelqu'un qui n'a pas appris le vocabulaire est
    // le contraire d'une démonstration réussie, et le survol n'existe pas sur
    // une tablette.
    assert.ok(barre.includes('{vue.libelle}'), 'le libellé n\'est pas rendu');
    assert.ok(!/tooltip=/.test(barre), 'un tooltip remplacerait le libellé');
    assert.ok(!/sr-only/.test(barre), 'un libellé sr-only est un libellé absent');
  });

  it('n\'utilise ni le mode rail ni le rail de redimensionnement', () => {
    assert.ok(!/collapsible=["']icon["']/.test(barre), 'le mode rail est écarté');
    assert.ok(!/SidebarRail/.test(barre), 'le rail de shadcn est écarté');
  });

  it('bascule en tiroir à 900 px, pas aux 768 de shadcn', () => {
    const sidebar = sansCommentaires(lire('front/src/ui/sidebar.jsx'));
    const styles = lire('front/src/styles.css');
    assert.match(styles, /--breakpoint-barre:\s*900px/,
      'le seuil de la barre doit être déclaré une fois, dans le thème');
    // Les deux faces du même seuil : le média CSS qui montre le panneau, et le
    // JavaScript qui décide d'ouvrir un tiroir. Désaccordées, il existe une
    // largeur où la barre n'est ni l'un ni l'autre.
    assert.ok(!/\bmd:(block|flex)\b/.test(sidebar),
      'le panneau suit encore le seuil md de shadcn');
    assert.match(sidebar, /barre:(block|flex)/);
    assert.match(sidebar, /useIsMobile\(SEUIL_BARRE\)/);
    assert.match(sidebar, /SEUIL_BARRE\s*=\s*900/);
  });
});
