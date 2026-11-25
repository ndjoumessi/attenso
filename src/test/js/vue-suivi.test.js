import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

import {
  carte, consigne, lienVivant, peutDemanderUneNotification,
  peutSignalerSonArrivee,
  rappelDuNumero,
} from '../../main/resources/static/app/vue-suivi.js';

const enAttente = {
  numero: 4, position: 3, statut: 'PREVENU',
  attenteMinMinutes: 20, attenteMaxMinutes: 40,
};

describe('la carte de la page client', () => {
  it("n'annonce PAS de file d'attente à quelqu'un qu'on appelle", () => {
    // Le défaut trouvé en regardant l'écran : la page disait « Personnes avant
    // vous : 2 — 20 à 40 min » en grand, et « On vous appelle » en petit
    // dessous. Sur un téléphone, c'est le grand chiffre qu'on lit.
    const vue = carte({ ...enAttente, statut: 'APPELE' });

    assert.equal(vue.etiquette, "C'est à vous");
    assert.equal(vue.valeur, 4, 'son numéro, celui que le mur affiche');
    assert.equal(vue.dessous, '', "aucune estimation quand c'est son tour");
    assert.equal(vue.appel, true);
  });

  it('montre la position tant que la personne attend', () => {
    const vue = carte(enAttente);

    assert.equal(vue.etiquette, 'Personnes avant vous');
    assert.equal(vue.valeur, 2, 'position 3 = deux personnes devant');
    assert.equal(vue.dessous, '20 à 40 min');
    assert.equal(vue.appel, false);
  });

  it("garde la position pour un absent : elle dit où il reprend sa place", () => {
    const vue = carte({ ...enAttente, statut: 'ABSENT', position: 2,
      attenteMinMinutes: 10, attenteMaxMinutes: 20 });

    assert.equal(vue.etiquette, 'Personnes avant vous');
    assert.equal(vue.valeur, 1);
    assert.match(consigne('ABSENT'), /juste après le suivant/);
  });

  it('bascule en « c\'est votre tour » dès la première place', () => {
    const vue = carte({ ...enAttente, position: 1,
      attenteMinMinutes: 0, attenteMaxMinutes: 0 });

    assert.equal(vue.valeur, 0);
    assert.equal(vue.dessous, "c'est votre tour");
    assert.equal(vue.appel, true);
  });

  it('ne montre jamais de chiffre négatif', () => {
    assert.equal(carte({ ...enAttente, position: 0 }).valeur, 0);
  });

  for (const statut of ['EN_CONSULTATION', 'TERMINE', 'PARTI', 'ANNULE']) {
    it(`montre le numéro et aucune estimation pour ${statut}`, () => {
      const vue = carte({ ...enAttente, statut });
      assert.equal(vue.etiquette, 'Votre numéro');
      assert.equal(vue.valeur, 4);
      assert.equal(vue.dessous, '');
    });
  }

  it('ne répète pas le numéro quand la carte le montre déjà', () => {
    assert.equal(rappelDuNumero({ ...enAttente, statut: 'APPELE' }), '');
    assert.equal(rappelDuNumero(enAttente), 'Votre numéro : 4');
  });

  it('a une consigne pour chaque état où le client doit agir', () => {
    for (const statut of ['PREVENU', 'APPELE', 'ABSENT']) {
      assert.notEqual(consigne(statut), '', `consigne manquante pour ${statut}`);
    }
    assert.equal(consigne('EN_ATTENTE'), '', 'rien à faire tant qu\'on attend');
  });
});

/**
 * Les deux boutons de la page client s'affichaient pour TOUS les statuts.
 * « Consultation terminée. Bonne journée. » — et dessous, « Je suis à
 * quelques minutes ».
 *
 * <p>Et ce n'est pas un bouton mort : {@code Ticket.signalerProche} n'exige
 * aucun statut, donc l'appui écrit bel et bien un « j'arrive » sur un ticket
 * clos, que l'accueil voit apparaître sur son tableau.
 */
describe('les gestes que la page client propose', () => {
  /**
   * Les statuts sont LUS dans l'énumération Java, pas recopiés ici. Une liste
   * en dur serait verte le jour où un neuvième statut arrive là-bas sans
   * décision ici — exactement le défaut que ce test existe pour attraper.
   */
  const STATUTS = (() => {
    const source = readFileSync(
      resolve(import.meta.dirname, '../../main/java/ai/attenso/ticket/Statut.java'),
      'utf8',
    );
    const debut = source.indexOf('enum Statut {');
    const corps = source.slice(debut, source.indexOf(';', debut));
    return (corps.match(/^\s+[A-Z_]+,?$/gm) ?? [])
      .map((ligne) => ligne.trim().replace(',', ''));
  })();

  it("lit bien les huit statuts du ticket", () => {
    assert.deepEqual(STATUTS, [
      'EN_ATTENTE', 'PREVENU', 'APPELE', 'ABSENT',
      'EN_CONSULTATION', 'TERMINE', 'PARTI', 'ANNULE',
    ], "l'extraction des statuts depuis Statut.java ne rend plus la bonne liste");
  });

  /**
   * « Je suis à quelques minutes » prévient l'accueil qu'on arrive. C'est
   * utile tant que la place peut encore bouger — APPELE compris, et c'est
   * même là que ça compte le plus : c'est ce qui évite d'être marqué absent.
   * En consultation, ou le passage clos, le geste n'a plus d'objet.
   */
  it("n'offre de signaler son arrivée que si l'accueil peut encore en tenir compte", () => {
    const attendu = {
      EN_ATTENTE: true, PREVENU: true, APPELE: true, ABSENT: true,
      EN_CONSULTATION: false, TERMINE: false, PARTI: false, ANNULE: false,
    };
    for (const statut of STATUTS) {
      assert.ok(statut in attendu, `statut non classé : ${statut}`);
      assert.equal(peutSignalerSonArrivee(statut), attendu[statut],
        `mauvaise décision pour ${statut}`);
    }
  });

  /**
   * Une notification annonce l'appel À VENIR. Une fois appelé, elle n'annonce
   * plus rien : proposer « prévenez-moi quand ce sera à vous » sous un titre
   * qui dit « C'est à vous » est une contradiction à l'écran.
   */
  it("n'offre la notification que s'il reste un appel à annoncer", () => {
    const attendu = {
      EN_ATTENTE: true, PREVENU: true, ABSENT: true,
      APPELE: false, EN_CONSULTATION: false,
      TERMINE: false, PARTI: false, ANNULE: false,
    };
    for (const statut of STATUTS) {
      assert.ok(statut in attendu, `statut non classé : ${statut}`);
      assert.equal(peutDemanderUneNotification(statut), attendu[statut],
        `mauvaise décision pour ${statut}`);
    }
  });

  /** Un statut inconnu ne doit rien ouvrir : on refuse par défaut. */
  it('refuse les deux gestes pour un statut inconnu', () => {
    for (const inconnu of ['', null, undefined, 'PETIT_DEJEUNER']) {
      assert.equal(peutSignalerSonArrivee(inconnu), false);
      assert.equal(peutDemanderUneNotification(inconnu), false);
    }
  });
});

/**
 * Un lien de suivi invalide affichait « Personnes avant vous — / chargement… »
 * indéfiniment, avec l'erreur en dessous. Le 404 était bien reçu et bien dit ;
 * la carte, elle, restait sur son état de chargement, qui ne finissait jamais.
 *
 * <p>La nuance est dans le cas où l'on a DÉJÀ un état : l'erreur réseau promet
 * alors « dernier état affiché », et retirer la carte ferait de ce message un
 * mensonge. Le prédicat porte donc sur les deux variables, pas sur l'erreur
 * seule — une condition `!erreur` aurait été verte sur le défaut d'origine
 * tout en cassant le rafraîchissement hors ligne, qui est le cas courant.
 */
describe('la grande carte de la page client', () => {
  it('reste affichée pendant le chargement', () => {
    assert.equal(lienVivant(null, null), true);
  });

  it("disparaît quand le lien n'a jamais rien rendu", () => {
    assert.equal(lienVivant(null, "Ce lien n'est plus valable."), false);
  });

  it('reste affichée quand un état connu accompagne une erreur réseau', () => {
    assert.equal(lienVivant(enAttente, 'Serveur injoignable — dernier état affiché.'),
      true, 'le message promet le dernier état : il faut donc le montrer');
  });

  it('reste affichée en marche normale', () => {
    assert.equal(lienVivant(enAttente, null), true);
  });
});
