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
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    // La PRÉSENCE d'abord : sans elle, ce test restait vert si les bandeaux
    // disparaissaient purement et simplement — et c'est ce qui rend le mode
    // dégradé visible. Ce projet a déjà perdu deux comportements dans une
    // migration.
    assert.ok(/<Bandeaux/.test(source), 'les bandeaux ont disparu du tableau');
    // SANS CONDITION, et pas seulement présents. Mesuré le 2026-11-28 :
    // `{false && <Bandeaux …}` laissait cette garde verte — les bandeaux
    // auraient disparu de l'écran en restant dans le fichier. C'est le
    // filet de sécurité de tout le mode hors réseau.
    const avantLeRendu = source.slice(Math.max(0, source.indexOf('<Bandeaux') - 60),
      source.indexOf('<Bandeaux'));
    assert.doesNotMatch(avantLeRendu, /&&\s*$|\?\s*$/,
      'les bandeaux sont rendus derrière une condition : ils peuvent disparaître '
      + 'de l\'écran sans disparaître du fichier');
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
    { composant: 'VueRendezVous', section: 'Agenda', liste: 'rdvs', apres: 'export function Agenda',
      jusque: 'export function Carnets' },
    { composant: 'VueCarnets', section: 'Carnets', liste: 'animaux', apres: 'export function Carnets',
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
        // Le vide est lié à `length === 0` de SA liste, pas à un `length`
        // quelconque : un `&& vide` inconditionnel ne passe plus.
        assert.match(corps,
          new RegExp(`\\b${v.liste}\\??\\.length === 0\\s*(?:&&|\\?)\\s*vide\\b`),
          'la section n\'affiche `vide` que si sa liste est chargée ET vide');
      });

      it('et ne l\'affiche pas tant que la liste n\'est pas chargée', () => {
        // `useState([])` rendrait « aucun » pendant le chargement, et les
        // gardes ci-dessus resteraient vertes : l'état initial est tenu ici.
        const corps = corpsDeSection(v);
        assert.match(corps, new RegExp(`\\[${v.liste}, set\\w+\\] = useState\\(null\\)`),
          'la liste démarre autrement que `null` : faux « aucun » au chargement');
      });
    });
  }

  for (const v of VUES_JOURNEE) {
    it(`${v.composant} ne redouble pas le titre de l'en-tête`, () => {
      // Le h1 de l'écran est celui de la COQUILLE (libellePour), présent pour
      // toute vue : c'est lui qui survit. La section enveloppée se tait, mais
      // garde son titre hors d'une vue, d'où une prop et non une suppression.
      const vue = sansCommentaires(lire(fichier(v.composant)));
      assert.match(vue, new RegExp(`<${v.section}\\b[^>]*\\btitre=\\{false\\}`),
        'la vue laisse sa section redoubler le h1 de l\'en-tête');
      const corps = corpsDeSection(v);
      assert.match(corps, /export function \w+\(\{[^}]*\btitre = true\b[^}]*\}\)/,
        'la section ne sait pas taire son titre');
      assert.match(corps, /\{titre && <Titre>/,
        'la section ne conditionne pas son titre à la prop');
    });
  }

  it('un agenda qui n\'a pas pu charger ne dit pas « aucun rendez-vous »', () => {
    const corps = corpsDeSection(VUES_JOURNEE[0]);
    // Le statut, et non un booléen : un 401 ne se dit pas comme une panne.
    assert.match(corps, /\.catch\(\(probleme\) => setEchec\(probleme\.statut \?\? 'reseau'\)\)/,
      'l\'échec du chargement n\'est pas retenu');
    // La PROPRIÉTÉ, et non la forme du ternaire : la branche « pas encore
    // chargé » doit dire l'échec s'il y en a un, montrer qu'elle travaille
    // sinon, et surtout ne jamais atteindre `vide` — « aucun rendez-vous »
    // pendant un chargement est un mensonge.
    //
    // L'assertion d'avant tenait le ternaire au caractère près ; elle a
    // rougi sur un code qui respectait pourtant la propriété, le jour où la
    // branche de chargement a gagné son squelette.
    const branche = corps.slice(corps.indexOf('{rdvs === null ?'),
      corps.indexOf('rdvs.length === 0'));
    assert.ok(branche, 'la branche « pas encore chargé » a disparu');
    assert.doesNotMatch(branche, /\bvide\b/,
      '« aucun rendez-vous » peut s\'afficher avant que la liste soit chargée');
    // L'échec se dit, mais plus DANS cette branche : il y vivait, et une
    // relecture ratée après un chargement réussi ne laissait alors aucun
    // signal — l'écran montrait des rendez-vous périmés sans le dire. Le
    // signal est donc sorti de la branche ; la garde suit la propriété.
    assert.match(corps, /\{echec && <EchecRelecture/,
      'un échec de chargement ne se dit plus');
    assert.doesNotMatch(corps, /rdvs === null && echec|echec && rdvs === null/,
      'le signal est de nouveau conditionné à une liste absente : '
      + 'une liste périmée redeviendrait muette');
  });

  /**
   * Les deux propriétés n'ont pas changé — nouvel onglet, cible tactile —
   * mais elles ont changé de FICHIER : le lien passe désormais par le
   * composant d'adresse partagé, qui lui ajoute la copie. La garde suit la
   * propriété là où elle vit, elle ne la relâche pas.
   */
  it('le lien de réservation sort du tableau sans le fermer, et n\'est jamais mort', () => {
    const vue = sansCommentaires(lire(fichier('VueRendezVous')));
    assert.match(vue, /<Adresse[^>]*href=\{lien\}/,
      "le lien de réservation ne passe plus par le composant d'adresse");
    // Sans slug, pas de lien : `/rdv/null` serait cliquable et mort.
    assert.match(vue, /slug \? `[^`]*\$\{slug\}` : null/);
    assert.match(vue, /\{lien && \(/, 'le lien est rendu même sans slug');

    const adresse = sansCommentaires(
      readFileSync(resolve(RACINE, 'front/src/composants/adresse.jsx'), 'utf8'));
    const i = adresse.indexOf('<a href={href}');
    assert.notEqual(i, -1, "le composant d'adresse n'ouvre plus l'adresse");
    const balise = adresse.slice(i, adresse.indexOf('>', i));
    assert.match(balise, /target="_blank"/);
    assert.match(balise, /rel="noopener"/);
  });

  it('le lien de réservation du vide tient la cible tactile', () => {
    const adresse = sansCommentaires(
      readFileSync(resolve(RACINE, 'front/src/composants/adresse.jsx'), 'utf8'));
    const i = adresse.indexOf('<a href={href}');
    assert.notEqual(i, -1, 'le lien de réservation est introuvable');
    // BORNÉ à la balise, et les deux écritures acceptées. `slice(i)` suivi
    // d'un `className="…"` partait chercher le PREMIER attribut à guillemets
    // doubles venant après — devenu celui du `<code>` de repli quand ce lien
    // est passé en gabarit. Le test lisait alors les classes d'un autre
    // élément et accusait un lien conforme.
    const balise = adresse.slice(i, adresse.indexOf('>', i) + 1);
    const attribut = balise.match(/className=(?:"([^"]*)"|\{`([\s\S]*?)`\})/);
    assert.ok(attribut, 'le lien de réservation n\'a plus de classes');
    const classes = (attribut[1] ?? attribut[2]).split(/\s+/);
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

/**
 * Le tableau est l'écran que l'accueil tient toute la journée, et c'était le
 * seul du produit sans repère de navigation ni lien d'évitement.
 *
 * <p>Mesuré dans l'arbre d'accessibilité : ONZE arrêts de tabulation — huit
 * entrées de menu, le lien vers le site, la déconnexion — avant la première
 * action de la file. La vitrine, elle, a son lien d'évitement depuis le
 * début. La barre latérale de shadcn rend un `div` : il n'existait aucun
 * `nav` dans tout le front.
 */
describe('le tableau se traverse au clavier', () => {
  const BARRE = 'front/src/accueil/Barre.jsx';
  const ACCUEIL = 'front/src/accueil.jsx';

  it('expose la barre latérale comme un repère de navigation nommé', () => {
    const source = sansCommentaires(lire(BARRE));
    const nav = source.match(/<nav[^>]*>/)?.[0] ?? '';
    assert.ok(nav, 'la barre ne rend aucun `nav` : sa navigation n\'est pas un repère');
    assert.match(nav, /aria-label="[^"]+"/,
      'le repère de navigation n\'est pas nommé : plusieurs `nav` dans une page '
      + 'ne se distinguent que par l\'étiquette');
    // Fenêtre BORNÉE : le repère doit englober le menu, pas vivre à côté.
    assert.match(source, /<nav[\s\S]{0,400}?<SidebarMenu/,
      'le menu est rendu hors du repère de navigation');
  });

  it('offre un lien d\'évitement vers le contenu', () => {
    const source = sansCommentaires(lire(ACCUEIL));
    const lien = source.match(/<a href="#([\w-]+)"[\s\S]{0,400}?<\/a>/);
    assert.ok(lien, 'aucun lien d\'évitement : onze tabulations avant la file');
    assert.match(lien[0], /sr-only focus:not-sr-only/,
      'le lien d\'évitement est visible en permanence, ou jamais');
    // La cible doit exister, sinon le lien ne mène nulle part — c'est le
    // défaut qu'on vient de corriger sur la page de confidentialité.
    assert.match(source, new RegExp(`id="${lien[1]}"`),
      `le lien d\'évitement vise #${lien[1]}, qui n\'existe pas dans la page`);
    assert.ok(source.indexOf(lien[0]) < source.indexOf('<Barre'),
      'le lien d\'évitement est rendu après la barre : on l\'atteint trop tard');
  });
});

/**
 * Le dégagement de la croix de fermeture n'existe que dans le tiroir.
 *
 * <p>`sheet.jsx` pose la croix en `absolute top-4 right-4`, 44 px : elle
 * occupe les soixante premiers pixels depuis le bord droit, et `pr-16` les
 * dégage. Mais AU-DESSUS du seuil il n'y a pas de croix, et prendre 64 px sur
 * un en-tête de 256 px replierait le nom de la clinique pour rien.
 *
 * <p>Aucune garde ne tenait ça. Mesuré le 2026-11-28 : sortir `pr-16` de la
 * branche `isMobile` ne faisait rougir aucun des 383 tests.
 */
describe('le d\'égagement de la croix reste dans le tiroir', () => {
  it('`pr-16` vit dans la branche isMobile, pas à côté', () => {
    const source = sansCommentaires(lire('front/src/accueil/Barre.jsx'));
    assert.match(source, /pr-16/, 'la croix du tiroir ne se dégage plus');
    // Fenêtre BORNÉE à la ternaire elle-même : `pr-16` ailleurs dans le
    // gabarit s'appliquerait aussi au-dessus du seuil.
    assert.match(source, /isMobile \? 'pr-16' : ''/,
      '`pr-16` n\'est plus conditionné au tiroir : il rogne 64 px sur un '
      + 'en-tête de 256 px là où il n\'y a aucune croix à dégager');
  });
});
