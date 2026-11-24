import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
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

  it('prend ses icônes dans UNE seule famille, en composants', () => {
    // Les glyphes Unicode de la première version venaient de blocs différents
    // — graisses, tailles optiques et styles de trait incompatibles — et l'euro
    // servait d'icône. Un composant par vue force une seule famille.
    for (const v of VUES) {
      // Les icônes de lucide sont des `forwardRef`, donc des objets et non des
      // fonctions : ce qu'on exige est qu'elles soient RENDABLES et nommées.
      const rendable = typeof v.icone === 'function'
        || (typeof v.icone === 'object' && v.icone !== null && '$$typeof' in v.icone);
      assert.ok(rendable, `icône non rendable pour ${v.cle} : ${typeof v.icone}`);
      assert.ok(v.icone.displayName || v.icone.name, `icône anonyme pour ${v.cle}`);
    }
    // Une seule provenance : un composant d'une autre nature ferait deux
    // familles, donc deux styles de trait dans la même barre.
    const familles = new Set(VUES.map((v) => String(v.icone.$$typeof ?? 'fonction')));
    assert.deepEqual([...familles], ['Symbol(react.forward_ref)'],
      'icônes de provenances mélangées');
    assert.equal(new Set(VUES.map((v) => v.icone.displayName)).size, VUES.length,
      'deux vues partagent la même icône');
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
    // La PRÉSENCE d'abord : sans elle, ce test restait vert si les bandeaux
    // disparaissaient purement et simplement — et c'est ce qui rend le mode
    // dégradé visible. Ce projet a déjà perdu deux comportements dans une
    // migration.
    assert.ok(/<Bandeaux/.test(source), 'les bandeaux ont disparu du tableau');
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
    assert.ok(!/\bmd:(block|flex)\b/.test(sidebar),
      'le panneau suit encore le seuil md de shadcn');
    assert.match(sidebar, /useIsMobile\(SEUIL_BARRE\)/);
    assert.match(sidebar, /SEUIL_BARRE\s*=\s*900/);
    // UNE seule déclaration : le seuil n'existe qu'en JavaScript, donc il n'y a
    // pas de seconde face qui puisse dériver.
    const seuils = [...sidebar.matchAll(/\b900\b/g)];
    assert.equal(seuils.length, 1, 'le seuil est déclaré plus d\'une fois');
  });

  /**
   * LA garde de fond, et la raison d'être de ce bloc.
   *
   * <p>ENF-08 demande Safari iOS 16+. Tailwind 4 compilait `barre:block` en
   * `@media (width>=900px)` — syntaxe d'intervalle, lue à partir de 16.4
   * seulement, et Lightning CSS la RÉÉCRIT ainsi même quand la source dit
   * `min-width`, parce que `build.cssTarget` l'y autorise. Sur un iPad en
   * 16.0-16.3 tenu en paysage, le panneau restait donc `display:none`, le
   * bouton « Menu » s'affichait (sa classe `barre:hidden` était ignorée de
   * même) mais `toggleSidebar` partait vers le panneau permanent : plus aucune
   * navigation atteignable.
   *
   * <p>La forme de la barre ne se décide donc plus en CSS du tout. `isMobile`
   * — `matchMedia` et `max-width`, lu depuis Safari 9 — tranche seul, et la
   * branche rendue est la bonne. Ces assertions interdisent le retour d'une
   * requête de média sur ce chemin.
   */
  describe('l\'atteignabilité de la navigation ne dépend d\'aucune requête de média', () => {
    it('le panneau n\'est gardé par aucune classe de point de rupture', () => {
      const sidebar = sansCommentaires(lire('front/src/ui/sidebar.jsx'));
      const panneau = sidebar.slice(sidebar.indexOf('data-slot="sidebar-gap"') - 1200,
        sidebar.indexOf('data-slot="sidebar-inner"'));
      assert.ok(panneau.length > 0, 'branche du panneau introuvable');
      assert.ok(!/\b(barre|sm|md|lg|xl):/.test(panneau),
        'le panneau se cache derrière un point de rupture : une syntaxe de média '
        + 'non lue le laisse display:none, sans navigation de remplacement');
    });

    it('le bouton du tiroir se montre sur le prédicat qui l\'actionne', () => {
      const barre = sansCommentaires(lire('front/src/accueil/Barre.jsx'));
      const ouvreur = barre.slice(barre.indexOf('export function OuvreLaBarre'));
      assert.ok(ouvreur.length > 0, 'OuvreLaBarre introuvable');
      assert.ok(!/\b(barre|sm|md|lg|xl):/.test(ouvreur),
        'visibilité en CSS et effet en JavaScript : deux sources qui peuvent se contredire');
      assert.match(ouvreur, /isMobile/);
      assert.match(ouvreur, /toggleSidebar/);
    });

    it('le prédicat emploie `max-width`, pas la syntaxe d\'intervalle', () => {
      const hook = sansCommentaires(lire('front/src/hooks/use-mobile.js'));
      assert.match(hook, /max-width:/);
      assert.ok(!/width\s*[<>]=?/.test(hook), 'syntaxe d\'intervalle dans le prédicat');
      // Le même prédicat pour l'abonnement et pour la décision : `innerWidth`
      // en décidait avant, et laissait une bande morte entre 899 et 900 px.
      assert.ok(!/innerWidth/.test(hook),
        'une seconde mesure réintroduit la bande morte sous le seuil');
      // Lu dès la première rendition, sinon la barre clignote dans sa forme
      // large sur un téléphone à chaque chargement.
      assert.match(hook, /useState\(\(\)\s*=>/);
    });

    it('aucune requête de média à 900 px ne subsiste dans la feuille livrée', () => {
      // La preuve est prise sur la FEUILLE LIVRÉE, pas sur la source : c'est
      // elle que la tablette télécharge, et c'est Lightning CSS qui décide de
      // sa syntaxe. Même dépendance au front construit que compatibilite.test.js.
      const actifs = resolve(RACINE, 'src/main/resources/static/app/assets');
      assert.ok(existsSync(actifs), `${actifs} absent : construire le front d'abord`);
      const feuilles = readdirSync(actifs).filter((f) => f.endsWith('.css'));
      assert.ok(feuilles.length > 0, 'aucune feuille produite');
      for (const f of feuilles) {
        const css = readFileSync(join(actifs, f), 'utf8');
        const medias = [...css.matchAll(/@media\s*\([^)]*900[^)]*\)/g)].map((m) => m[0]);
        assert.deepEqual(medias, [],
          `${f} : le seuil de la barre est reparti en CSS, sous une syntaxe que `
          + 'Safari 16.0 peut ignorer');
      }
    });
  });

  it('annonce le tiroir en français, et laisse de quoi en sortir', () => {
    // Les deux chaînes que le lecteur d'écran lit à l'ouverture du tiroir,
    // dans un produit qui n'a pas une autre phrase en anglais.
    const sidebar = sansCommentaires(lire('front/src/ui/sidebar.jsx'));
    assert.ok(!/>Sidebar<|Displays the mobile sidebar/.test(sidebar),
      'le tiroir s\'annonce encore en anglais');
    assert.match(sidebar, /<SheetTitle>Navigation<\/SheetTitle>/);
    // Et un bouton de fermeture : sous le seuil, la barre EST le tiroir.
    assert.ok(!/\[&>button\]:hidden/.test(sidebar),
      'le bouton de fermeture de Radix est masqué : on ne sort du menu que par la pellicule');
    assert.ok(!/sr-only">Close</.test(lire('front/src/ui/sheet.jsx')),
      'le bouton de fermeture s\'annonce encore en anglais');
  });
});
