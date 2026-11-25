import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

import { ACTIONS, CIBLES, CLASSES, DEPLACEMENTS, LIBELLES, OUVERTS, actionsPour,
  avecEnAttente, deplacementsPour, doitOublierLeJournal, signature }
  from '../../main/resources/static/app/vue-file.js';
import { GESTES, VUES } from '../../../front/src/accueil/navigation.js';

const STATUT_JAVA = 'src/main/java/ai/attenso/ticket/Statut.java';

const RACINE = resolve(import.meta.dirname, '../../..');

/**
 * Le source d'un fichier du dépôt, résolu depuis l'emplacement de ce test.
 *
 * <p>Pas un chemin relatif nu : `node --test src/test/js/` et `mvnw verify` ne
 * partent pas forcément du même répertoire, et un test qui serait vert ou
 * rouge selon qui le lance ne tient rien. Même raison que dans
 * `navigation.test.js`.
 */
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/**
 * Le même source, commentaires retirés.
 *
 * <p>Ces gardes-ci portent sur ce que le code FAIT, pas sur ce qu'il raconte :
 * sans ce filtre, le commentaire qui explique une branche la satisfait à la
 * place du code, et il suffit d'allonger ce commentaire pour qu'une lecture
 * bornée n'atteigne plus la branche elle-même — constaté en écrivant cette
 * tâche. Même filtre, et même convention de commentaires, que
 * `navigation.test.js`.
 */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .filter((l) => !/^\s*(\/\/|\*)/.test(l))
  .join('\n');

/**
 * Les attributs déclarés AVANT le `className` de l'élément qui s'ouvre à
 * l'ancre, commentaires retirés.
 *
 * <p>Bornée des deux côtés : rien d'un autre élément ne peut s'y glisser, et un
 * attribut écrit dans un commentaire ne compte pas. C'est le patron de
 * `classesApres` dans `navigation.test.js`, pour la raison inverse — là-bas on
 * veut les classes, ici ce qui les précède. Le dépôt écrit tous ses contrôles
 * avec le `className` en dernier.
 */
function attributsAvantLesClasses(fichier, ancre) {
  const source = sansCommentaires(lire(fichier));
  const i = source.indexOf(ancre);
  assert.notEqual(i, -1, `${ancre} introuvable dans ${fichier}`);
  const fin = source.indexOf('className=', i);
  assert.notEqual(fin, -1, `aucun className après ${ancre} dans ${fichier}`);
  return source.slice(i, fin);
}

/** Les états ouverts, lus dans la machine à états du serveur. */
function ouvertsDuServeur() {
  const source = readFileSync(STATUT_JAVA, 'utf8');
  const bloc = source.match(/OUVERTS\s*=[\s\S]*?EnumSet\.of\(([^)]*)\)/);
  assert.ok(bloc, 'OUVERTS introuvable dans Statut.java');
  return new Set(bloc[1].split(',').map((mot) => mot.trim()).filter(Boolean));
}

/** Les transitions permises, lues dans la machine à états du serveur. */
function transitionsDuServeur() {
  const source = readFileSync(STATUT_JAVA, 'utf8');
  const permises = new Map();
  const motif = /SUIVANTS\.put\((\w+),\s*EnumSet\.(?:of\(([^)]*)\)|noneOf)/g;
  for (const [, depuis, vers] of source.matchAll(motif)) {
    permises.set(depuis, new Set(
      (vers ?? '').split(',').map((mot) => mot.trim()).filter(Boolean)));
  }
  assert.ok(permises.size >= 8, 'machine à états incomplète dans Statut.java');
  return permises;
}

describe('le tableau de l\'accueil est un miroir fidèle du serveur', () => {
  it('a exactement les mêmes états ouverts que ServiceFile', () => {
    // Ce miroir sert à recalculer les rangs pendant une panne, au moment
    // précis où l'ASV n'a que lui. S'il dérive, il ment.
    assert.deepEqual([...OUVERTS].sort(), [...ouvertsDuServeur()].sort());
  });

  it('ne propose que des boutons que le serveur acceptera', () => {
    const permises = transitionsDuServeur();
    for (const [statut, boutons] of Object.entries(ACTIONS)) {
      for (const [route] of boutons) {
        const cible = CIBLES[route];
        assert.ok(cible, `route « ${route} » sans état cible`);
        assert.ok(permises.get(statut)?.has(cible),
          `le tableau propose ${statut} → ${cible}, que la machine à états refuse`);
      }
    }
  });

  it('a un libellé et une classe pour chaque état du serveur', () => {
    for (const statut of transitionsDuServeur().keys()) {
      assert.ok(LIBELLES[statut], `libellé manquant pour ${statut}`);
      assert.ok(statut in CLASSES, `classe manquante pour ${statut}`);
    }
  });

  it("ne fait jamais de « Parti » un bouton principal", () => {
    // Le confondre avec « absent » fausserait le compteur de départs, qui est
    // le seul chiffre justifiant l'abonnement.
    for (const boutons of Object.values(ACTIONS)) {
      for (const [route, , classe] of boutons) {
        if (route === 'parti') {
          assert.equal(classe, 'b-parti', '« Parti » garde sa propre forme');
        }
      }
    }
  });

  it("propose « Présent » directement à un absent qui se présente", () => {
    assert.ok(actionsPour('ABSENT').some(([route]) => route === 'present'),
      'sinon l\'ASV doit le rappeler avant de le faire entrer');
  });
});

const tableau = {
  jour: '2026-11-23',
  attenteMedianeMinutes: 15,
  enFile: 3,
  departs: 0,
  lignes: [
    { id: 'a', numeroJour: 1, position: 1, statut: 'PREVENU', priorite: 0, joignable: true },
    { id: 'b', numeroJour: 2, position: 2, statut: 'EN_ATTENTE', priorite: 0, joignable: true },
    { id: 'c', numeroJour: 3, position: 3, statut: 'EN_ATTENTE', priorite: 0, joignable: false },
  ],
};

describe('les gestes pas encore envoyés', () => {
  it('ne changent rien quand il n\'y en a pas', () => {
    const vue = avecEnAttente(tableau, []);
    assert.deepEqual(vue.lignes.map((l) => l.position), [1, 2, 3]);
    assert.equal(vue.enFile, 3);
    assert.equal(vue.lignes.every((l) => l.enAttenteEnvoi === false), true);
  });

  it('remontent la file quand quelqu\'un entre en consultation', () => {
    // Le cas qui compte : un ticket qui quitte la file libère un rang, et
    // l'ASV doit le voir tout de suite, même sans réseau.
    const vue = avecEnAttente(tableau,
      [{ ticketId: 'a', cible: 'EN_CONSULTATION' }]);

    assert.deepEqual(vue.lignes.map((l) => [l.numeroJour, l.position]),
      [[1, 0], [2, 1], [3, 2]]);
    assert.equal(vue.enFile, 2);
    assert.equal(vue.lignes[0].enAttenteEnvoi, true);
    assert.equal(vue.lignes[1].enAttenteEnvoi, false);
  });

  it('mettent à jour le compteur de départs, qui porte la preuve de valeur', () => {
    const vue = avecEnAttente(tableau, [{ ticketId: 'c', cible: 'PARTI' }]);
    assert.equal(vue.departs, 1);
    assert.equal(vue.enFile, 2);
  });

  it('un appel ne libère aucun rang : APPELE occupe toujours la file', () => {
    const vue = avecEnAttente(tableau, [{ ticketId: 'a', cible: 'APPELE' }]);
    assert.deepEqual(vue.lignes.map((l) => l.position), [1, 2, 3]);
    assert.equal(vue.enFile, 3);
  });

  it('font gagner le dernier geste quand deux visent le même ticket', () => {
    const vue = avecEnAttente(tableau, [
      { ticketId: 'a', cible: 'APPELE' },
      { ticketId: 'a', cible: 'EN_CONSULTATION' },
    ]);
    assert.equal(vue.lignes[0].statut, 'EN_CONSULTATION');
  });

  it('ne modifient pas le tableau reçu du serveur', () => {
    const copie = structuredClone(tableau);
    avecEnAttente(tableau, [{ ticketId: 'a', cible: 'PARTI' }]);
    assert.deepEqual(tableau, copie, 'le dernier état connu doit rester intact');
  });

  it('ignorent une commande sans ticket ni cible', () => {
    const vue = avecEnAttente(tableau, [{ cible: 'PARTI' }, { ticketId: 'a' }]);
    assert.equal(vue.departs, 0);
    assert.equal(vue.enFile, 3);
  });
});

describe('la signature d\'affichage', () => {
  it('est stable tant que rien ne bouge', () => {
    const vue = avecEnAttente(tableau, []);
    assert.equal(signature(vue.lignes), signature(avecEnAttente(tableau, []).lignes));
  });

  it('change dès qu\'un état change', () => {
    const avant = signature(avecEnAttente(tableau, []).lignes);
    const apres = signature(
      avecEnAttente(tableau, [{ ticketId: 'b', cible: 'APPELE' }]).lignes);
    assert.notEqual(avant, apres, 'sinon le tableau ne se redessinerait pas');
  });
});

describe("l'annulation d'un ticket", () => {
  it("n'est offerte qu'en attente", () => {
    // Annuler un ticket déjà appelé effacerait un départ du compteur, donc
    // l'argument de vente. Après un appel, le geste juste est « parti ».
    assert.ok(actionsPour('EN_ATTENTE').some(([route]) => route === 'annule'));
    for (const statut of ['PREVENU', 'APPELE', 'ABSENT', 'EN_CONSULTATION']) {
      assert.ok(!actionsPour(statut).some(([route]) => route === 'annule'),
        `annuler ne doit pas être proposé en ${statut}`);
    }
  });

  it("n'est jamais le bouton principal", () => {
    const [, , classe] = actionsPour('EN_ATTENTE').find(([r]) => r === 'annule');
    assert.notEqual(classe, 'b-principal');
  });

  it('a son état cible, sinon l\'affichage optimiste perdrait la ligne', () => {
    assert.equal(CIBLES.annule, 'ANNULE');
  });
});

describe('le déplacement manuel dans la file', () => {
  it('est proposé sur tous les états ouverts', () => {
    for (const statut of OUVERTS) {
      assert.equal(deplacementsPour(statut).length, 2,
        `monter/descendre doivent être proposés en ${statut}`);
    }
  });

  it("n'est pas proposé sur un ticket clos, qui n'a plus de place dans la file", () => {
    for (const statut of ['TERMINE', 'PARTI', 'ANNULE', 'EN_CONSULTATION']) {
      assert.deepEqual(deplacementsPour(statut), []);
    }
  });

  it("ne passe pas par la machine à états : aucune cible d'affichage optimiste", () => {
    // Si un jour un déplacement entrait dans CIBLES, il serait rejoué comme
    // une transition par la file hors ligne et corromprait l'ordre.
    for (const [route] of DEPLACEMENTS) {
      assert.equal(CIBLES[route], undefined);
      assert.equal(ACTIONS[route], undefined);
    }
  });

  it('porte une flèche visible, pas seulement un libellé lu par le lecteur d\'écran', () => {
    for (const [, libelle, fleche] of DEPLACEMENTS) {
      assert.ok(libelle.length > 0);
      assert.ok(fleche.length > 0);
    }
  });
});

describe("le journal local d'une tablette", () => {
  it("s'oublie quand on ouvre une session sur une AUTRE clinique", () => {
    // Le journal porte le dernier tableau connu et les gestes pas encore
    // partis. Les garder montrerait la file de l'une à l'autre sur la même
    // tablette, et rejouerait les gestes de la première avec le jeton de la
    // seconde.
    assert.equal(doitOublierLeJournal('cerisiers', 'tilleuls'), true);
  });

  it('se garde quand la même clinique se reconnecte', () => {
    // C'est le cas qui compte : une session expirée pendant une panne, et des
    // gestes en attente qu'il ne faut surtout pas jeter.
    assert.equal(doitOublierLeJournal('cerisiers', 'cerisiers'), false);
  });

  it("s'oublie au tout premier usage, quand la tablette ne retient rien", () => {
    assert.equal(doitOublierLeJournal(null, 'cerisiers'), true);
  });
});

describe("l'écran de la file", () => {
  /**
   * GARDE DE NON-RÉGRESSION, et déclarée comme telle : la propriété est DÉJÀ
   * vraie avant cette tâche — `File.jsx` porte sa branche `lignes.length === 0`
   * depuis l'écriture de l'écran. Elle n'a donc pas de phase rouge, et mimer un
   * cycle TDD sur une propriété acquise ne prouverait rien. Ce qu'elle tient,
   * c'est le jour où la liste sera réécrite : une file vide qui ne dit rien
   * ressemble à une page cassée, et c'est l'état dans lequel la démonstration
   * commence.
   */
  it('une file vide dit quoi faire, elle ne reste pas muette', () => {
    const source = sansCommentaires(lire('front/src/accueil/File.jsx'));
    assert.match(source, /lignes\.length === 0|aucun ticket/i);
    // Et la PHRASE, pas seulement la garde : un `return null` sous la même
    // condition passerait l'assertion ci-dessus en laissant l'écran muet.
    const branche = source.slice(source.indexOf('lignes.length === 0'));
    assert.match(branche.slice(0, 300), />\s*[A-ZÀ-Ý][^<]{10,}</,
      'la branche de file vide ne rend aucune phrase');
  });

  /**
   * Trouvé AU DOIGT sur tablette, pas au navigateur : les deux boutons
   * faisaient bien 44 px — le plancher d'Apple, déjà gardé par
   * `jetons.test.js` — mais ne laissaient que 4 px entre eux. La TAILLE d'une
   * cible et son VOISINAGE sont deux problèmes distincts, et seul le premier
   * était tenu. Une cible de 44 px collée à sa voisine se manque autant
   * qu'une cible de 30.
   *
   * Ce qui rend celui-ci coûteux plutôt que gênant : les deux flèches font
   * l'inverse l'une de l'autre. Se tromper ne fait pas « rien », cela déplace
   * le ticket dans le mauvais sens, devant la clinique.
   */
  it('les flèches de déplacement sont assez écartées pour le doigt', () => {
    const source = sansCommentaires(lire('front/src/accueil/File.jsx'));
    const i = source.indexOf('deplacementsPour(');
    assert.ok(i > 0, 'les boutons de déplacement sont introuvables');
    // Fenêtre bornée au conteneur : on remonte au `<div` le plus proche, sans
    // traverser le JSX voisin, dont les écarts ne gardent pas cette propriété.
    const avant = source.slice(0, i);
    const conteneur = avant.slice(avant.lastIndexOf('<div'));
    const ecart = conteneur.match(/\bgap-([\d.]+)/);
    assert.ok(ecart, 'le conteneur des flèches ne déclare aucun écart');
    const pixels = Number(ecart[1]) * 4;
    assert.ok(pixels >= 8,
      `les deux flèches ne sont séparées que de ${pixels} px ; Apple et `
      + 'Material demandent 8 px au minimum entre deux cibles tactiles '
      + 'voisines, et sous ce seuil on monte un ticket qu\'on voulait descendre');
  });
});

describe('les gestes de la file', () => {
  it('« Urgence » et « Client sans mobile » sont des gestes, pas des destinations', () => {
    // La barre latérale ne les porte pas : on ne VA PAS à une urgence, on en
    // insère une. La table dit lesquels, et dans quel ordre ils s'offrent.
    assert.deepEqual(GESTES.map((g) => g.cle), ['urgence', 'client']);
    for (const geste of GESTES) {
      assert.ok(!VUES.some((v) => v.cle === geste.cle),
        `« ${geste.cle} » est déclaré à la fois comme geste et comme vue`);
      assert.ok(geste.libelle?.length > 1, `libellé de bouton manquant pour ${geste.cle}`);
      assert.match(geste.route, /^\/api\/tickets\//);
      assert.ok(geste.messageReseau?.length > 10,
        `${geste.cle} ne dit pas ce qui se passe si le serveur ne répond pas`);
    }
  });

  /**
   * La branche `vue === 'file'` du commutateur, COMMENTAIRES RETIRÉS.
   *
   * <p>Le filtre n'est pas un détail de confort : cette tâche ajoute un bloc de
   * commentaire de huit lignes dans cette branche même, et un `<File` ou un
   * `<Gestes` écrit dedans inverserait l'ordre mesuré. Une garde sur ce que le
   * code FAIT ne doit pas dépendre de ce qu'il raconte.
   */
  function brancheDeLaFile() {
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    const debut = source.indexOf("vue === 'file'");
    const fin = source.indexOf("vue === 'rendez-vous'");
    assert.ok(debut !== -1 && fin > debut,
      'la branche de la vue file est introuvable dans le commutateur');
    return source.slice(debut, fin);
  }

  it('sont rendus EN TÊTE de la vue file, avant la liste', () => {
    // C'est tout l'objet de la tâche : deux boutons atteignables sans faire
    // défiler, là où c'étaient deux sections sous la file.
    const branche = brancheDeLaFile();
    const gestes = branche.indexOf('<Gestes');
    const liste = branche.indexOf('<File');
    assert.notEqual(gestes, -1, 'la vue file ne rend aucun geste');
    assert.notEqual(liste, -1, 'la vue file ne rend plus la liste');
    assert.ok(gestes < liste,
      'les gestes sont rendus sous la liste : il faut faire défiler pour les atteindre');
  });

  it('laissent les compteurs en tête, eux aussi', () => {
    const branche = brancheDeLaFile();
    const compteurs = branche.indexOf('<Compteurs');
    const liste = branche.indexOf('<File');
    assert.notEqual(compteurs, -1, 'les compteurs ont disparu de la vue file');
    assert.notEqual(liste, -1, 'la vue file ne rend plus la liste');
    assert.ok(compteurs < liste, 'les compteurs sont passés sous la liste');
  });

  it("ne laissent plus de section de saisie à faire défiler sous la file", () => {
    assert.ok(!/Saisies/.test(sansCommentaires(lire('front/src/accueil.jsx'))),
      'le tableau rend encore les saisies en section');
    assert.ok(!/Saisies/.test(sansCommentaires(lire('front/src/accueil/Sections.jsx'))),
      'les saisies en section survivent dans Sections.jsx');
  });

  it('tirent leurs boutons de la table, et non d\'un double codé à la main', () => {
    // Sans ceci, la table ci-dessus serait décorative : deux boutons écrits à
    // la main passeraient les assertions de forme sans rien partager avec elle.
    //
    // Un simple `assert.match(source, /GESTES\.map/)` ne tenait PAS : vérifié
    // par mutation, deux boutons codés en dur le laissaient vert, parce que le
    // composant parcourt AUSSI la table pour l'aide hors réseau. La garde était
    // satisfaite par le mauvais jeton. On exige donc qu'un parcours de la table
    // rende le CONTRÔLE lui-même, et non n'importe quoi.
    const source = sansCommentaires(lire('front/src/accueil/Gestes.jsx'));
    const parcours = [...source.matchAll(/GESTES\.map\(/g)]
      .map((m) => source.slice(m.index, m.index + 300));
    assert.ok(parcours.length > 0, 'le composant ne parcourt pas la table');
    assert.ok(parcours.some((bloc) => /<Geste\s/.test(bloc)),
      'les contrôles ne sont pas rendus depuis la table : un double codé à la '
      + 'main dériverait de GESTES sans que rien ne rougisse');
  });

  it('refusent le mode hors réseau en disant pourquoi', () => {
    // Ces deux gestes sont les seuls de la file qui ne peuvent pas attendre le
    // retour du réseau : le numéro du ticket est attribué par le serveur. La
    // raison doit rester écrite à côté du bouton, sinon un bouton grisé sans
    // explication passe pour une panne.
    for (const geste of GESTES) {
      assert.match(geste.aideHorsReseau, /hors réseau/i,
        `${geste.cle} ne dit pas pourquoi il est indisponible`);
    }
    // Les DEUX contrôles, et bornés à leur propre balise : un `disabled`
    // cherché n'importe où dans le fichier restait vert s'il migrait sur le
    // mauvais élément, ou s'il ne subsistait que dans un commentaire. Et c'est
    // la seule garde du mode dégradé pour ces gestes.
    //
    // La validation compte autant que le déclencheur : `horsReseau` bascule
    // AUSSI pendant que la boîte est ouverte — `etatLiaison` est réévalué à
    // chaque `rafraichir`, donc au moins toutes les trente secondes par le
    // filet de sécurité — et l'aide hors réseau est rendue hors de la boîte,
    // donc derrière le voile et illisible.
    for (const [ancre, quoi] of [['<button type="button"', 'le bouton de tête'],
      ['<button type="submit"', 'la validation']]) {
      assert.match(attributsAvantLesClasses('front/src/accueil/Gestes.jsx', ancre),
        /disabled=\{[^}]*horsReseau/, `${quoi} reste cliquable hors réseau`);
    }
  });
});
