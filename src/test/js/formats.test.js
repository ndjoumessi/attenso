import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { duree, minutes } from '../../main/resources/static/app/formats.js';

describe("l'annonce d'attente", () => {
  it('est une fourchette, jamais une promesse', () => {
    assert.equal(minutes(20, 40), '20 à 40 min');
  });

  it('dit « moins de » quand la borne basse est nulle', () => {
    assert.equal(minutes(0, 5), 'moins de 5 min');
  });

  it("dit « c'est votre tour » plutôt que « 0 min »", () => {
    assert.equal(minutes(0, 0), "c'est votre tour");
  });
});

describe("l'attente écoulée du tableau", () => {
  const T0 = new Date('2026-11-24T09:00:00Z').getTime();

  it('arrondit à la minute, sans jamais afficher de secondes', () => {
    // Une durée qui défile seconde par seconde transforme la salle d'attente
    // en chronomètre, pour l'ASV comme pour qui regarde par-dessus son épaule.
    assert.equal(duree(T0, T0 + 12 * 60000 + 45000), '12 min');
  });

  it("dit « à l'instant » sous la minute", () => {
    assert.equal(duree(T0, T0 + 20000), "à l'instant");
  });

  it('passe en heures au-delà de soixante minutes, « 83 min » ne se lisant pas', () => {
    assert.equal(duree(T0, T0 + 83 * 60000), '1 h 23');
    assert.equal(duree(T0, T0 + 120 * 60000), '2 h');
  });

  it("ne rend jamais de durée négative quand l'horloge de la tablette "
    + "est en avance sur celle du serveur", () => {
    assert.equal(duree(T0, T0 - 5 * 60000), "à l'instant");
  });

  it('accepte une date ISO, qui est ce que renvoie le serveur', () => {
    assert.equal(duree('2026-11-24T09:00:00Z', T0 + 5 * 60000), '5 min');
  });
});
