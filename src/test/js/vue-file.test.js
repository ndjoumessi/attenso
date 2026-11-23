import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

import { ACTIONS, CIBLES, CLASSES, LIBELLES, OUVERTS, actionsPour, avecEnAttente, signature }
  from '../../main/resources/static/app/vue-file.js';

const STATUT_JAVA = 'src/main/java/ai/attenso/ticket/Statut.java';

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
