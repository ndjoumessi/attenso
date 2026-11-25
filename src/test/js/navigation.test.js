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

  /**
   * Les cibles tactiles des contrôles que le tableau introduit — ceux de la
   * barre, et les croix de fermeture des surfaces qu'elle a amenées avec elle.
   *
   * <p>`jetons.test.js` tient les JETONS `--cible` (56 px, la cible du produit)
   * et `--cible-min` (44 px, le plancher) ; personne ne vérifiait qu'un contrôle
   * s'en serve. C'est ainsi qu'est passée une croix de fermeture de 16 px —
   * remise pour élargir une cible, et plus étroite que la bande de pellicule
   * qu'elle remplaçait.
   *
   * <p>Le piège à garder en tête : `cible-min` ne pose qu'un `min-height`. Un
   * contrôle carré doit AUSSI tenir sa largeur, sans quoi il reste large comme
   * son icône. Les deux sont donc vérifiés séparément.
   */
  describe('les contrôles du tableau tiennent le plancher tactile', () => {
    /**
     * Les classes acceptées, en JETONS EXACTS et non en motifs.
     *
     * <p>C'est la correction d'une faille de cette garde même, et de la classe
     * de défaut qu'elle prétend interdire, retournée contre elle. La hauteur
     * était cherchée par `/\bcible(-min)?\b/`, qui correspond AUSSI à la
     * sous-chaîne `--cible-min` du jeton de largeur `w-(--cible-min)` — `-`
     * n'étant pas un caractère de mot, `\b` s'ancre à l'intérieur. Une croix
     * écrite `absolute w-(--cible-min)` SANS la classe `cible-min` passait donc
     * les deux assertions : large de 44 px, haute de 16, garde au vert.
     *
     * <p>Comparer des jetons découpés rend la faille impossible par
     * construction, au lieu de la repousser derrière un motif plus fin.
     */
    const HAUTEUR = ['cible', 'cible-min', 'h-11', 'h-12', 'size-11', 'size-12'];
    const LARGEUR = ['w-(--cible)', 'w-(--cible-min)', 'w-11', 'w-12', 'size-11', 'size-12'];

    /** Le pas de l'échelle d'espacement de Tailwind, que le projet ne surcharge pas. */
    const PAS = 4;

    /** Les jetons de cible, LUS dans jetons.css et non recopiés ici. */
    function jetonsDeCible() {
      const css = lire('src/main/resources/static/app/jetons.css');
      const px = (nom) => {
        const m = css.match(new RegExp(`--${nom}:\\s*(\\d+)px`));
        assert.ok(m, `--${nom} introuvable dans jetons.css`);
        return Number(m[1]);
      };
      return { 'w-(--cible)': px('cible'), 'w-(--cible-min)': px('cible-min') };
    }

    /**
     * La largeur en pixels que DÉCLARENT les classes d'un contrôle.
     *
     * <p>Dérivée du jeton que la garde vient de lire, et non réécrite à côté.
     * Une constante `16 + 44` recopiée mesurerait le dégagement contre une
     * largeur qui n'est plus la vraie : passer la croix à `w-(--cible)` la
     * porterait à 56 px, donc à 72 px de place occupée, et un dégagement de
     * 64 px passerait à tort.
     */
    function largeurDeclaree(classes) {
      const parJeton = jetonsDeCible();
      for (const c of classes) {
        if (c in parJeton) return parJeton[c];
        const pas = c.match(/^(?:w|size)-(\d+)$/);
        if (pas) return Number(pas[1]) * PAS;
      }
      return null;
    }

    /** Le décalage depuis le bord droit que déclarent les classes. */
    function decalageDroite(classes) {
      const m = classes.map((c) => c.match(/^right-(\d+)$/)).find(Boolean);
      return m ? Number(m[1]) * PAS : null;
    }

    /**
     * Les classes du premier `className` qui suit l'ancre, en jetons.
     *
     * <p>Découpe sur les espaces : seul un jeton entier compte, donc
     * `w-(--cible-min)` ne peut pas se faire passer pour `cible-min`.
     */
    function classesApres(fichier, ancre) {
      const source = sansCommentaires(lire(fichier));
      const i = source.indexOf(ancre);
      assert.notEqual(i, -1, `${ancre} introuvable dans ${fichier}`);
      // Les trois formes que le dépôt écrit : chaîne, littéral de gabarit, et
      // `cn("…", className)` — celle des composants shadcn. Sans la troisième,
      // la lecture sautait l'élément visé et atteignait le `className` d'un
      // AUTRE élément plus bas, ou aucun : constaté sur l'en-tête de la boîte
      // de dialogue.
      const m = source.slice(i)
        .match(/className=(?:"([^"]*)"|\{`([^`]*)`\}|\{cn\(\s*"([^"]*)")/);
      assert.ok(m, `aucun className après ${ancre} dans ${fichier}`);
      return (m[1] ?? m[2] ?? m[3]).split(/\s+/).filter(Boolean);
    }

    const tient = (classes, acceptees) => classes.some((c) => acceptees.includes(c));

    it('le bouton qui ouvre le tiroir', () => {
      const classes = classesApres('front/src/accueil/Barre.jsx', 'export function OuvreLaBarre');
      assert.ok(tient(classes, HAUTEUR),
        `le bouton « Menu » n'annonce aucune cible tactile : ${classes.join(' ')}`);
    });

    it('chaque entrée de la barre', () => {
      const classes = classesApres('front/src/accueil/Barre.jsx', 'function Entree');
      assert.ok(tient(classes, HAUTEUR),
        `une entrée de navigation sous le plancher tactile : ${classes.join(' ')}`);
    });

    /**
     * Les composants à croix de fermeture, et l'en-tête qui doit lui dégager
     * la place.
     *
     * <p>La table existe parce que cette garde était CÂBLÉE sur `sheet.jsx`.
     * La boîte de dialogue, arrivée avec la vue file, a donc reçu une croix de
     * 44 px et un dégagement de 44 px — la croix en occupant 56 — sans que rien
     * ne rougisse, alors que l'arithmétique qui le démontre était déjà écrite
     * ici. Un troisième composant à croix doit entrer dans cette table, et le
     * test « aucune croix hors garde » plus bas est là pour qu'il ne puisse pas
     * arriver sans.
     */
    const CROIX = [
      {
        quoi: 'le tiroir de la barre',
        fichier: 'front/src/ui/sheet.jsx',
        primitive: 'SheetPrimitive',
        entete: { fichier: 'front/src/accueil/Barre.jsx', ancre: '<SidebarHeader' },
      },
      {
        quoi: 'la boîte de dialogue',
        fichier: 'front/src/ui/dialog.jsx',
        primitive: 'DialogPrimitive',
        entete: { fichier: 'front/src/ui/dialog.jsx', ancre: 'data-slot="dialog-header"' },
      },
    ];

    /**
     * Les classes de la croix, lues DANS SON PROPRE ÉLÉMENT.
     *
     * <p>Deux ancres plus naïves ont été essayées et écartées, chacune par
     * mutation plutôt que par lecture. `'<SheetPrimitive.Close'` tombe d'abord
     * sur le composant enveloppe `SheetClose`, qui ne porte aucun `className` :
     * la lecture avançait jusqu'au premier `className` reconnu et atteignait la
     * vraie croix par chance. Resserrer en
     * `'<SheetPrimitive.Close className'` ne suffit pas non plus — il suffit que
     * l'enveloppe en reçoive un pour que la garde lise SES classes et repasse au
     * vert sur une croix dépouillée ; vérifié.
     *
     * <p>La lecture est donc BORNÉE au rendu conditionnel de la croix et à sa
     * balise de fermeture. Rien avant ni après ne peut s'y glisser.
     */
    function classesDeLaCroix({ fichier, primitive }) {
      const source = sansCommentaires(lire(fichier));
      const debut = source.indexOf('showCloseButton && (');
      assert.notEqual(debut, -1,
        `${fichier} : le rendu conditionnel de la croix est introuvable`);
      const fin = source.indexOf(`</${primitive}.Close>`, debut);
      assert.notEqual(fin, -1, `${fichier} : la croix n'est plus un élément fermé`);
      const bloc = source.slice(debut, fin);
      assert.match(bloc, new RegExp(`<${primitive}\\.Close`),
        `${fichier} : la croix a changé de nature`);
      const m = bloc.match(/className="([^"]*)"/);
      assert.ok(m, `${fichier} : la croix ne porte aucun className`);
      return m[1].split(/\s+/).filter(Boolean);
    }

    for (const croix of CROIX) {
      it(`la croix de ${croix.quoi} tient sa HAUTEUR`, () => {
        const classes = classesDeLaCroix(croix);
        assert.ok(tient(classes, HAUTEUR),
          `croix sans hauteur de cible : ${classes.join(' ')}`);
      });

      it(`la croix de ${croix.quoi} tient sa LARGEUR`, () => {
        // Séparée de la hauteur, et pas seulement par goût : tant que les deux
        // vivaient dans un même `it`, la hauteur rougissait d'abord et la
        // largeur n'était jamais éprouvée seule. `cible-min` ne pose qu'un
        // `min-height`, donc c'est bien cette assertion-ci qui tient le défaut
        // d'origine.
        const classes = classesDeLaCroix(croix);
        assert.ok(tient(classes, LARGEUR),
          'croix sans largeur de cible : elle reste large comme son icône, donc '
          + `plus étroite que la pellicule qu'elle remplace : ${classes.join(' ')}`);
      });

      it(`et l'en-tête de ${croix.quoi} dégage la place de cette croix`, () => {
        // La GÉOMÉTRIE, pas une liste de classes permises : `pr-15` valait
        // 60 px et ne laissait pas un pixel de marge, mais satisfaisait le
        // motif `pr-1[5-9]` d'avant.
        const classesCroix = classesDeLaCroix(croix);
        const largeur = largeurDeclaree(classesCroix);
        const decalage = decalageDroite(classesCroix);
        assert.ok(largeur,
          `largeur de la croix indéterminable : ${classesCroix.join(' ')}`);
        assert.ok(decalage !== null,
          `décalage de la croix indéterminable : ${classesCroix.join(' ')}`);
        // La place occupée est DÉRIVÉE de la croix qu'on vient de lire : si elle
        // passe à `w-(--cible)`, l'exigence suit d'elle-même.
        const place = decalage + largeur;

        const classes = classesApres(croix.entete.fichier, croix.entete.ancre);
        const pas = classes.map((c) => c.match(/^'?pr-(\d+)'?$/)).find(Boolean);
        assert.ok(pas, `aucun dégagement à droite sur l'en-tête : ${classes.join(' ')}`);
        const px = Number(pas[1]) * PAS;
        assert.ok(px > place,
          `pr-${pas[1]} = ${px}px : la croix occupe ${place}px depuis le bord `
          + `(right ${decalage} + largeur ${largeur}), il en faut davantage`);
      });
    }

    it('aucune croix de fermeture n\'échappe à la table', () => {
      // La garde doit COUVRIR : c'est son câblage en dur sur sheet.jsx qui a
      // laissé passer la croix de la boîte de dialogue. Tout composant qui
      // rend une croix doit avoir son entrée, sinon sa géométrie n'est tenue
      // par personne.
      const dossier = resolve(RACINE, 'front/src/ui');
      const aCroix = readdirSync(dossier)
        .filter((f) => f.endsWith('.jsx'))
        .filter((f) => lire(`front/src/ui/${f}`).includes('showCloseButton && ('))
        .map((f) => `front/src/ui/${f}`);
      assert.ok(aCroix.length > 0, 'balayage de front/src/ui/ en défaut');
      assert.deepEqual(aCroix.filter((f) => !CROIX.some((c) => c.fichier === f)), [],
        'composant à croix sans entrée dans CROIX : sa cible et son dégagement '
        + 'ne seraient tenus par aucune garde');
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

/**
 * Les vues du groupe Journée : Rendez-vous et Carnets de santé.
 *
 * <p>Leurs états vides sont vus en démonstration — une clinique neuve n'a ni
 * rendez-vous ni carnet, et la clinique de démonstration n'a aucun créneau —
 * donc ils doivent nommer l'action à faire, pas rester muets.
 *
 * <p>Où vit la décision « c'est vide » : dans la SECTION, qui seule détient la
 * liste chargée (les sections chargent indépendamment et ne remontent rien).
 * La VUE fournit le message, par la prop `vide`. Les deux moitiés sont gardées,
 * chacune sur une lecture bornée : l'une sans l'autre laisserait un message
 * jamais affiché, ou un affichage sans message.
 */
describe('les vues du groupe Journée', () => {
  const VUES_JOURNEE = [
    { composant: 'VueRendezVous', section: 'Agenda', apres: 'export function Agenda',
      jusque: 'export function Carnets' },
    { composant: 'VueCarnets', section: 'Carnets', apres: 'export function Carnets',
      jusque: null },
  ];
  const fichier = (composant) => `front/src/accueil/vues/${composant}.jsx`;

  /** Le source de la section, de son `export` à celui de la suivante. */
  function corpsDeSection({ apres, jusque }) {
    const source = sansCommentaires(lire('front/src/accueil/Sections.jsx'));
    const debut = source.indexOf(apres);
    assert.notEqual(debut, -1, `${apres} introuvable`);
    const fin = jusque ? source.indexOf(jusque, debut) : source.length;
    assert.notEqual(fin, -1, `${jusque} introuvable`);
    return source.slice(debut, fin);
  }

  for (const v of VUES_JOURNEE) {
    describe(v.composant, () => {
      it('enveloppe la section existante, sans alias', () => {
        const source = sansCommentaires(lire(fichier(v.composant)));
        assert.match(source, new RegExp(`export function ${v.composant}\\b`));
        assert.match(source, new RegExp(`import \\{[^}]*\\b${v.section}\\b[^}]*\\} from '\\.\\./Sections\\.jsx'`));
        assert.ok(!/\bas\s+\w+/.test(source.match(/import[\s\S]*?;/g).join('')),
          'import aliasé : le préfixe Vue existe pour l\'éviter');
        assert.match(source, new RegExp(`<${v.section}\\b`));
      });

      it('dit quoi faire quand elle est vide', () => {
        const source = sansCommentaires(lire(fichier(v.composant)));
        const i = source.indexOf('vide={');
        assert.notEqual(i, -1, 'la vue ne fournit aucun état vide à sa section');
        // Borné à partir de `vide={` : un « Aucun » de l'introduction ne doit
        // pas faire passer un état vide devenu muet.
        const message = source.slice(i);
        assert.match(message, /Aucun|Aucune/);
        // Un état vide muet laisse croire à une panne : il nomme l'action.
        assert.match(message, /Les clients réservent sur|formulaire ci-dessus/);
      });

      it('et sa section l\'affiche vraiment quand la liste est vide', () => {
        const corps = corpsDeSection(v);
        assert.match(corps, /export function \w+\(\{[^}]*\bvide\b[^}]*\}\)/,
          'la section ne reçoit pas `vide`');
        assert.match(corps, /(?:&&|:)\s*vide\b|\{vide\}/, 'la section n\'affiche jamais `vide`');
        assert.match(corps, /length/, 'la section ne teste pas la liste vide');
      });
    });
  }

  it('le lien de réservation du vide tient la cible tactile', () => {
    const classes = (() => {
      const source = sansCommentaires(lire(fichier('VueRendezVous')));
      const i = source.indexOf('<a href={lien}');
      assert.notEqual(i, -1, 'le lien de réservation est introuvable');
      const m = source.slice(i).match(/className="([^"]*)"/);
      assert.ok(m, 'le lien de réservation ne porte aucune classe');
      return m[1].split(/\s+/).filter(Boolean);
    })();
    assert.ok(classes.includes('cible-min') || classes.includes('cible'),
      `lien sans cible tactile : ${classes.join(' ')}`);
  });

  it('le tableau rend les deux vues, avec les props de leur section', () => {
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    assert.match(source, /vue === 'rendez-vous' && <VueRendezVous jeton=\{jeton\} \/>/);
    assert.match(source,
      /vue === 'carnets' && <VueCarnets jeton=\{jeton\} surErreur=\{setErreur\} \/>/);
    // Les sections ne sont plus montées en direct : elles passent par la vue,
    // faute de quoi l'état vide ne serait jamais fourni.
    assert.ok(!/<Agenda\b|<Carnets\b/.test(source),
      'le tableau monte encore une section en direct, sans état vide');
  });
});
