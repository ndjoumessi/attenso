import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { ADRESSE_INACTIVE, adresseInactive } from
  '../../main/resources/static/app/vue-comptoir.js';

/**
 * Le comptoir avalait l'échec du nom de clinique — `.catch(() => {})` — et
 * retombait sur « Cette clinique ». Pour un slug inconnu ou une clinique
 * désactivée, `findBySlugAndActifTrue` rend un 404 : le formulaire restait
 * donc actif, le client tapait son numéro de mobile, appuyait sur « J'y
 * suis », et c'est SEULEMENT là qu'il apprenait que l'adresse ne menait
 * nulle part. Avec son numéro déjà donné.
 */
describe("le diagnostic de l'adresse du comptoir", () => {
  it('reconnaît une adresse qui ne mène à aucune clinique active', () => {
    assert.equal(adresseInactive({ statut: 404 }), true);
  });

  /**
   * La distinction porte TOUT le correctif. Un comptoir hors ligne, ou dont
   * le serveur bronche une seconde, doit continuer à prendre des tickets : la
   * mention reste vraie mais générique, et la prise de ticket réessaiera.
   * Barrer la page sur n'importe quel échec fermerait le comptoir à chaque
   * coupure de wifi — un défaut pire que celui qu'on corrige.
   */
  it('ne barre pas la page pour une panne passagère', () => {
    assert.equal(adresseInactive({ reseau: true }), false,
      'hors ligne, le comptoir doit rester ouvert');
    for (const statut of [429, 500, 502, 503]) {
      assert.equal(adresseInactive({ statut }), false,
        `${statut} est passager : réessayer peut marcher`);
    }
  });

  it('ne barre pas la page sur un échec sans forme connue', () => {
    for (const echec of [null, undefined, {}, new Error('rien')]) {
      assert.equal(adresseInactive(echec), false);
    }
  });

  /**
   * Le message doit dire ce qu'il faut FAIRE. L'accueil peut inscrire un
   * client directement depuis son tableau ; sans cette phrase, la personne
   * reste devant une page morte sans savoir qu'il y a une autre porte.
   */
  it('dit où aller, pas seulement que ça ne marche pas', () => {
    assert.match(ADRESSE_INACTIVE, /accueil/i, "le message n'oriente pas");
    assert.ok(ADRESSE_INACTIVE.length > 40, 'un message trop court n\'explique rien');
  });
});
