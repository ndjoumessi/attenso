import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { balises } from './aide/balises.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

/**
 * Un client doit pouvoir ARRIVER sur la page de réservation.
 *
 * <p>Elle fonctionnait de bout en bout, et personne ne pouvait l'atteindre.
 * Mesuré en production le 2026-11-26 : l'affiche du comptoir encode
 * `/t/{slug}`, la page de ticket ne prononçait jamais le mot « rendez-vous »,
 * la vitrine est le site de l'éditeur — et l'adresse `/rdv/{slug}` n'existait
 * qu'à UN endroit du produit entier, le tableau de bord de la clinique.
 *
 * <p>Aucun test ne pouvait le voir : chacun vérifiait sa page, et le défaut
 * était l'absence d'une arête ENTRE deux pages. C'est la classe de défaut que
 * le projet n'avait pas encore de garde pour attraper.
 */
describe('la réservation est joignable depuis le comptoir', () => {
  it('la page du QR porte un lien vers la réservation', () => {
    const source = rendu(lire('front/src/comptoir.jsx'));
    // Sur la BALISE, et non sur le fichier : le mot « rdv » apparaît aussi dans
    // les commentaires et dans l'appel de données, qui ne mènent nulle part.
    const liens = balises(source, 'a').filter((b) => /\/rdv\//.test(b));
    assert.equal(liens.length, 1,
      'la page de ticket n\'offre pas un et un seul chemin vers la réservation');
    // Le slug, pas une adresse en dur : une seule clinique sinon.
    assert.match(liens[0], /\$\{encodeURIComponent\(slug\)\}/,
      'le lien de réservation ne porte pas le slug de la clinique');
  });

  /**
   * Le pré-rendu est le premier rendu, et il ne connaît aucun slug : poser
   * l'adresse directement ferait diverger l'hydratation, comme le nom de la
   * clinique avant elle. Le lien doit donc être conditionné.
   */
  it('ne rend pas le lien avant de connaître le slug', () => {
    const source = rendu(lire('front/src/comptoir.jsx'));
    const i = source.indexOf('/rdv/');
    const avant = source.slice(Math.max(0, i - 200), i);
    assert.match(avant, /\{slug &&/,
      'le lien de réservation n\'attend pas que le slug soit connu');
    assert.match(source, /useState\(''\)/,
      'le slug ne part pas d\'une valeur sûre pour le pré-rendu');
  });
});

/**
 * L'adresse que la clinique communique ne s'efface pas quand l'agenda se
 * remplit.
 *
 * <p>Le lien vivait DANS la propriété `vide` de la section Agenda : il
 * disparaissait donc au premier rendez-vous de la journée, c'est-à-dire quand
 * la clinique commence à s'en servir. Une garde qui vérifie seulement que le
 * lien « est présent dans le fichier » reste verte dans ce cas — c'est
 * précisément le piège consigné pour ce projet.
 */
describe('le tableau de bord montre l\'adresse de réservation en permanence', () => {
  /** Le contenu de `vide={( … )}`, par appariement de parenthèses. */
  function etatVide(source) {
    const debut = source.indexOf('vide={(');
    assert.notEqual(debut, -1, 'la propriété `vide` a disparu de la vue');
    let i = debut + 'vide={'.length;
    let profondeur = 0;
    for (; i < source.length; i += 1) {
      if (source[i] === '(') profondeur += 1;
      else if (source[i] === ')') {
        profondeur -= 1;
        if (profondeur === 0) return source.slice(debut, i + 1);
      }
    }
    throw new Error('propriété `vide` non refermée');
  }

  it('hors de l\'état vide, donc visible un jour chargé', () => {
    const source = rendu(lire('front/src/accueil/vues/VueRendezVous.jsx'));
    const vide = etatVide(source);

    assert.ok(/\{lien &&/.test(source),
      'la vue ne rend plus l\'adresse de réservation du tout');
    assert.ok(!/\blien\b/.test(vide),
      'l\'adresse de réservation est enfermée dans l\'état vide : '
      + 'elle disparaîtrait au premier rendez-vous du jour');
  });

  it('l\'état vide explique toujours l\'absence de créneau', () => {
    const vide = etatVide(rendu(lire('front/src/accueil/vues/VueRendezVous.jsx')));
    assert.match(vide, /Aucun rendez-vous aujourd'hui/);
    assert.match(vide, /aucun horaire[\s\S]{0,80}ouvert à la réservation/);
  });
});

/**
 * L'affiche imprimée porte l'adresse de réservation.
 *
 * <p>C'est le dernier maillon manquant du chemin. Le produit n'a aucune
 * navigation côté client : QR, SMS ou jeton, et rien d'autre. Le client qui
 * n'est pas encore entré n'a ni QR (il n'est pas au comptoir) ni SMS (il n'a
 * pas de ticket) — l'affiche en vitrine est tout ce qui lui reste.
 */
describe('l\'affiche du comptoir donne l\'adresse de réservation', () => {
  const SOURCE = rendu(lire('front/src/affiche.jsx'));

  it('imprime l\'adresse, lisible, depuis la vue du serveur', () => {
    assert.match(SOURCE, /urlLisible\(vue\.urlRendezVous\)/,
      'l\'affiche n\'imprime pas l\'adresse de réservation');
    // Bornée à la balise : « rendez-vous » apparaît aussi dans la prose.
    const i = SOURCE.indexOf('vue.urlRendezVous');
    assert.match(SOURCE.slice(Math.max(0, i - 300), i), /Pour un rendez-vous/,
      'l\'adresse est imprimée sans dire à quoi elle sert');
  });

  /**
   * Un seul QR. Deux codes sur une affiche et personne ne sait lequel
   * scanner — et celui du comptoir est l'action du jour.
   */
  it('n\'encode pas un second QR', () => {
    assert.equal((SOURCE.match(/<Qr\b/g) ?? []).length, 1,
      'l\'affiche porte plus d\'un QR');
  });
});
