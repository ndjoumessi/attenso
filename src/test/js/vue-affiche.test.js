import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { promesse, urlLisible } from '../../main/resources/static/app/vue-affiche.js';

describe("la promesse de l'affiche", () => {
  it('annonce un message quand la clinique peut vraiment en envoyer', () => {
    assert.equal(promesse({ sms: true }),
      'Vous recevrez un message quand votre tour approchera.');
  });

  it("ne promet pas de message tant que l'expéditeur n'est pas validé", () => {
    // L'état réel d'une clinique le jour de son inscription. Un client qui
    // range son téléphone en attendant un SMS qui ne partira jamais est pire
    // servi que celui à qui on n'a rien promis.
    const texte = promesse({ sms: false });
    assert.doesNotMatch(texte, /message|SMS/i);
    assert.equal(texte, 'Vous suivrez votre tour depuis votre téléphone.');
  });
});

describe("l'adresse écrite sur le papier", () => {
  it('laisse tomber https://, que personne ne tape', () => {
    assert.equal(urlLisible('https://attenso.fr/t/cerisiers'), 'attenso.fr/t/cerisiers');
  });

  it("garde http:// en entier : une adresse de développement doit se voir", () => {
    assert.equal(urlLisible('http://localhost:8080/t/cerisiers'),
      'http://localhost:8080/t/cerisiers');
  });

  it('ne laisse pas de barre oblique en fin de ligne', () => {
    assert.equal(urlLisible('https://attenso.fr/t/cerisiers/'), 'attenso.fr/t/cerisiers');
  });
});
