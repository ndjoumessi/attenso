import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { minutes } from '../../main/resources/static/app/formats.js';

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
