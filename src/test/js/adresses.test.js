import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const rendu = (source) => sansCommentaires(source).replace(/^import[\s\S]*?;$/gm, '');

const ADRESSE = 'front/src/composants/adresse.jsx';

// `vues/` compris : je l'avais oublié, et la clause d'unicité avait donc un
// angle mort entier — c'est précisément là que vivait l'adresse de
// réservation que cette garde est censée couvrir.
const TOUS = ['front/src', 'front/src/accueil', 'front/src/accueil/vues',
  'front/src/composants']
  .flatMap((d) => readdirSync(resolve(RACINE, d))
    .filter((f) => f.endsWith('.jsx')).map((f) => `${d}/${f}`));

/**
 * Trouvé en REGARDANT le tableau : les adresses que la clinique doit
 * transmettre — le carnet d'un animal, la page de réservation — étaient
 * posées en texte brut, sans aucun geste pour les emporter. Sur une
 * tablette posée sur un comptoir, par quelqu'un qui tient un animal,
 * sélectionner une URL de soixante caractères n'arrive pas.
 */
describe("les adresses à transmettre ont un geste pour les emporter", () => {
  it('vivent dans un seul composant', () => {
    const source = rendu(lire(ADRESSE));
    assert.match(source, /navigator\.clipboard|writeText/,
      'la copie ne passe pas par le presse-papiers');
    // BORNÉ au chemin de succès. Ma première version cherchait « copi »
    // n'importe où : deux mutations y ont survécu, parce que le mot reste
    // dans le texte affiché même quand plus rien ne le déclenche.
    assert.match(source, /writeText[\s\S]{0,100}?setEtat\('copie'\)/,
      'la confirmation ne suit pas la copie réussie');
    assert.match(source, /etat === 'copie' &&/, "la confirmation n'est pas rendue");
  });

  /**
   * Un presse-papiers n'est pas garanti : hors contexte sécurisé, et sur
   * certains navigateurs embarqués, `navigator.clipboard` est absent. Le
   * bouton ne doit alors pas mentir.
   */
  it("prévoit l'absence de presse-papiers, et le DIT", () => {
    const source = rendu(lire(ADRESSE));
    // Un `catch` ne suffit pas : il peut annoncer un succès. Ce qui compte
    // est que les deux issues mènent à deux états DIFFÉRENTS, tous deux
    // rendus. Une confirmation fausse est pire que pas de bouton, parce
    // qu'on colle du vide sans le savoir.
    assert.match(source, /catch\s*\{[\s\S]{0,100}?setEtat\('echec'\)/,
      "l'échec de la copie est annoncé comme un succès, ou pas annoncé");
    assert.match(source, /etat === 'echec' &&/, "l'échec n'est pas rendu");
  });

  it("donne une vraie cible tactile au bouton", () => {
    const source = rendu(lire(ADRESSE));
    const bouton = source.match(/<button[\s\S]*?>/)?.[0] ?? '';
    assert.match(bouton, /cible/, 'le bouton de copie est sous le plancher tactile');
  });

  it("est le seul endroit qui écrit une adresse à copier", () => {
    const coupables = TOUS.filter((c) => c !== ADRESSE
      && /writeText/.test(rendu(lire(c))));
    assert.deepEqual(coupables, [], 'une copie est réécrite hors du composant partagé');
  });
});

/**
 * EF-906 promet un carnet « auquel son propriétaire accède en scannant un QR
 * code ». Le tableau disait « Émettre un QR de carnet », « Révoquer le QR »,
 * et n'affichait qu'une URL : aucun QR n'existait nulle part, pas même côté
 * serveur. La clinique n'avait rien à faire scanner.
 */
describe('le carnet montre enfin son QR', () => {
  const SECTIONS = 'front/src/accueil/Sections.jsx';

  it('demande le QR au serveur, par animal', () => {
    const source = rendu(lire(SECTIONS));
    assert.match(source, /\/carnet\/qr/, "le QR du carnet n'est pas demandé");
    assert.match(source, /<Qr\b/, "le QR n'est pas dessiné");
  });

  /**
   * À LA DEMANDE : charger un QR par animal au montage ferait payer la liste
   * entière pour un geste qui porte sur un seul.
   */
  it('ne le charge pas pour toute la liste au montage', () => {
    const source = rendu(lire(SECTIONS));
    assert.doesNotMatch(source, /animaux[\s\S]{0,120}?map\([\s\S]{0,200}?\/carnet\/qr/,
      'un QR est chargé par animal au rendu de la liste');
  });

  it("offre aussi l'adresse du carnet à copier", () => {
    assert.match(rendu(lire(SECTIONS)), /<Adresse/, 'le lien du carnet ne se copie pas');
  });
});

describe("l'adresse de réservation se transmet", () => {
  const VUE = 'front/src/accueil/vues/VueRendezVous.jsx';

  it('passe par le composant partagé', () => {
    assert.match(rendu(lire(VUE)), /<Adresse/,
      "l'adresse de réservation n'a pas de geste pour l'emporter");
  });

  /**
   * Le lien s'ouvrait DÉJÀ, et bien — nouvel onglet, cible tactile. Mon
   * constat initial disait « texte brut » : c'était faux. Ce qui manquait
   * était la copie, et l'ouverture ne doit pas disparaître en l'ajoutant.
   */
  it("garde l'ouverture de la page en plus de la copie", () => {
    assert.match(rendu(lire(VUE)), /<Adresse[^>]*href=/,
      "la page de réservation ne s'ouvre plus depuis le tableau");
  });
});
