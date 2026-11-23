import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { RIEN, appel, legende, prochains } from
  '../../main/resources/static/app/vue-ecran.js';

describe("l'écran de la salle d'attente", () => {
  it('annonce le numéro appelé', () => {
    const vue = { appeles: [7], prochains: [5, 4] };
    assert.equal(appel(vue), '7');
    assert.equal(legende(vue), 'est appelé');
    assert.equal(prochains(vue), 'ensuite : 5 · 4');
  });

  it('accorde au pluriel quand deux salles appellent en même temps', () => {
    const vue = { appeles: [7, 3], prochains: [] };
    assert.equal(appel(vue), '7 · 3');
    assert.equal(legende(vue), 'sont appelés');
  });

  it("dit « consultation en cours » plutôt que « personne en salle » quand "
    + 'des gens attendent', () => {
    // Un écran qui annonce le vide alors que la salle est pleine dit aux gens
    // que l'outil ne les voit pas.
    const vue = { appeles: [], prochains: [5, 4] };
    assert.equal(appel(vue), RIEN);
    assert.equal(legende(vue), 'consultation en cours');
  });

  it('assume la salle vide quand elle est vraiment vide', () => {
    const vue = { appeles: [], prochains: [] };
    assert.equal(appel(vue), RIEN);
    assert.equal(legende(vue), 'personne en salle');
    assert.equal(prochains(vue), '');
  });
});
