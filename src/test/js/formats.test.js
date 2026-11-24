import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { describe, it } from 'node:test';

import {
  duree, euros, grouperParJour, heureCourte, jourAvecAnnee, jourLong, minutes,
} from '../../main/resources/static/app/formats.js';

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

describe("le jour tel qu'on l'écrit", () => {
  it('rend le jour de la semaine, le numéro et le mois', () => {
    assert.equal(jourLong('2026-11-24'), 'mardi 24 novembre');
  });

  it("porte l'année pour le carnet, où les vaccins s'étalent sur des années", () => {
    assert.equal(jourAvecAnnee('2026-11-24'), '24 novembre 2026');
  });

  /**
   * LE test qui compte, et il doit passer par un AUTRE processus : le fuseau
   * par défaut est figé au démarrage de node, donc le changer en cours de route
   * ne prouverait rien.
   *
   * Ce qu'il garde : une date de calendrier ne change pas de jour selon le
   * réglage de l'appareil. Un écran de clinique laissé sur UTC, un téléphone en
   * itinérance — le cas n'est pas théorique. Vérifié en cassant la mise en
   * forme dans les deux sens : c'est la variante « date nue » qui le fait
   * tomber, et à Pago Pago seulement (« lundi 23 novembre »). Les fuseaux à
   * l'est passent même avec une implémentation naïve ; ils restent ici parce
   * que ce test garde une propriété — le libellé ne dépend d'aucun fuseau — et
   * non une implémentation.
   */
  const dansLeFuseau = (zone, appel) => execFileSync(process.execPath,
    ['--input-type=module', '-e',
      `import('${new URL('../../main/resources/static/app/formats.js', import.meta.url).pathname}')`
      + `.then((m) => process.stdout.write(${appel}));`],
    { env: { ...process.env, TZ: zone }, encoding: 'utf8' });

  for (const zone of ['Europe/Paris', 'UTC', 'Pacific/Pago_Pago', 'Pacific/Kiritimati']) {
    it(`rend le même jour quel que soit le fuseau de l'appareil (${zone})`, () => {
      assert.equal(dansLeFuseau(zone, "m.jourLong('2026-11-24')"),
        'mardi 24 novembre');
      assert.equal(dansLeFuseau(zone, "m.jourAvecAnnee('2026-11-24')"),
        '24 novembre 2026');
    });
  }
});

describe('l\'heure d\'un créneau', () => {
  it('perd ses secondes', () => {
    assert.equal(heureCourte('09:00:00'), '09:00');
    assert.equal(heureCourte('14:30'), '14:30');
  });
});

describe('les créneaux groupés par jour', () => {
  const creneaux = [
    { jour: '2026-11-24', heure: '09:00:00' },
    { jour: '2026-11-24', heure: '09:30:00' },
    { jour: '2026-11-25', heure: '09:00:00' },
  ];

  it('range chaque créneau sous son jour', () => {
    const parJour = grouperParJour(creneaux);
    assert.deepEqual([...parJour.keys()], ['2026-11-24', '2026-11-25']);
    assert.equal(parJour.get('2026-11-24').length, 2);
    assert.equal(parJour.get('2026-11-25').length, 1);
  });

  // L'ordre du serveur fait autorité : il trie par jour puis par heure, et
  // retrier côté écran ferait deux sources de vérité pour le même affichage.
  it('garde l\'ordre reçu, sans retrier', () => {
    const desordre = [
      { jour: '2026-11-25', heure: '09:00:00' },
      { jour: '2026-11-24', heure: '09:00:00' },
    ];
    assert.deepEqual([...grouperParJour(desordre).keys()],
      ['2026-11-25', '2026-11-24']);
  });

  it('rend une table vide pour aucun créneau', () => {
    assert.equal(grouperParJour([]).size, 0);
  });
});

describe('un montant', () => {
  // L'espace avant l'euro est une espace insécable : c'est ce que rend
  // l'Intl française, et l'écrire en dur dans le test le rendrait faux.
  const insecable = (texte) => texte.replace(/\u202f|\u00a0/g, ' ');

  it('tombe les centimes quand il n\'y en a pas', () => {
    assert.equal(insecable(euros(6900)), '69 €');
    assert.equal(insecable(euros(0)), '0 €');
  });

  // Le vrai cas : deux copies de cette fonction existaient, l'une écrivait
  // « 69,00 € » et l'autre « 69 € » pour le MÊME palier. Ce test fixe la
  // réponse unique.
  it('les montre quand il y en a', () => {
    assert.equal(insecable(euros(15)), '0,15 €');
    assert.equal(insecable(euros(6915)), '69,15 €');
  });

  it('accepte les gros montants sans perdre le séparateur de milliers', () => {
    assert.match(insecable(euros(149000)), /^1 490 €$/);
  });
});
