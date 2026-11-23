import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { carte, consigne, rappelDuNumero } from
  '../../main/resources/static/app/vue-suivi.js';

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
