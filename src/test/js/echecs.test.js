import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

const TOUS = ['front/src', 'front/src/accueil', 'front/src/composants']
  .flatMap((dossier) => readdirSync(resolve(RACINE, dossier))
    .filter((f) => f.endsWith('.jsx'))
    .map((f) => `${dossier}/${f}`));

/**
 * Un échec avalé ne se voit pas : la page reste telle quelle et personne
 * n'apprend que la lecture a raté. Quatre défauts du même jour venaient de
 * là, dont le comptoir qui laissait un client donner son numéro avant
 * d'échouer.
 *
 * <p>La garde est TRANSVERSALE, avec une liste d'exemptions motivées, parce
 * que corriger site par site est ce qui a laissé passer les suivants. Un
 * nouveau `.catch` muet fait rougir, et il faut écrire ici pourquoi il est
 * légitime — ce qui est tout l'intérêt : la justification devient obligatoire.
 */
describe('aucun échec de lecture n\'est avalé en silence', () => {
  /** Chaque exemption porte sa raison, et la raison est vérifiable. */
  const EXEMPTES = new Map([
    ['front/src/suivi.jsx',
      "la clé push : proposer un bouton de notification qui échouera est pire "
      + 'que de ne pas le proposer, et le code le dit'],
    ['front/src/composants/legal.jsx',
      "l'identité de l'éditeur : la page affiche « à compléter » plutôt qu'un "
      + 'blanc, donc le manque EST dit'],
    ['front/src/accueil.jsx',
      "le cache hors ligne : l'indisponibilité d'IndexedDB est annoncée "
      + 'ailleurs, par `disponible()` et le bandeau de journal absent'],
    ['front/src/ecran.jsx',
      "l'écran de salle garde son dernier affichage : un mur vide inquiète la "
      + "salle plus qu'un numéro de trente secondes, et personne n'agit dessus"],
    ['front/src/reservation.jsx',
      'le NOM de la clinique seulement : une adresse inactive est déjà dite '
      + "par l'appel aux créneaux, qui rend 404 par le même `parSlug`, et sans "
      + 'créneau il n\'y a pas de formulaire à remplir — le défaut du comptoir '
      + "n'existe donc pas ici"],
    ['front/src/accueil/Connexion.jsx',
      'la demande de lien oublié rend la MÊME réponse qu\'un compte existe ou '
      + 'non : la distinguer ferait de ce formulaire un annuaire des cliniques '
      + 'clientes. Propriété de sécurité, pas une négligence'],
  ]);

  // SANS le drapeau `g`. Avec, `assert.match` est *stateful* : il passe par
  // `test()`, qui avance `lastIndex`, et le fichier suivant est examiné à
  // partir du milieu. Mesuré : `legal.jsx` était déclaré propre alors qu'il
  // avale bien un échec. La garde ratait de la façon même qu'elle surveille.
  const MUET = /\.catch\(\s*\(\s*\)\s*=>\s*(?:\{\s*\}|null|undefined)\s*\)|\bcatch\s*\{\s*\}/;

  it('ne laisse aucun échec muet hors de la liste des exemptions', () => {
    const fautifs = TOUS.filter((chemin) => {
      return MUET.test(sansCommentaires(lire(chemin))) && !EXEMPTES.has(chemin);
    });
    assert.deepEqual(fautifs, [],
      'échec avalé sans justification : ajouter la raison à EXEMPTES, ou le dire à l\'écran');
  });

  it('garde chaque exemption motivée, et pas seulement nommée', () => {
    for (const [chemin, raison] of EXEMPTES) {
      assert.ok(raison.length > 60, `exemption de ${chemin} sans raison écrite`);
      // Et l'exemption doit servir : une entrée dont le fichier n'avale plus
      // rien est une permission oubliée, qui couvrirait le prochain défaut.
      assert.match(sansCommentaires(lire(chemin)), MUET,
        `${chemin} n'avale plus rien : retirer son exemption`);
    }
  });
});

/**
 * Les trois échecs que cette branche rend visibles. Chacun a une conséquence
 * mesurée, pas une gêne théorique.
 */
describe('les trois lectures du tableau disent leur échec', () => {
  /**
   * `/api/compte` était documenté « information de confort ». Ce n'en est plus
   * une : c'est de lui que vient le courriel du champ d'identifiant caché,
   * sans lequel le navigateur ne peut pas enregistrer le nouveau mot de passe.
   * L'échec dégradait donc en silence le correctif du matin même.
   */
  it('le compte : son échec atteint le formulaire de mot de passe', () => {
    const source = sansCommentaires(lire('front/src/accueil.jsx'));
    assert.match(source, /setEchecCompte\(true\)/, "l'échec du compte n'est plus retenu");
    assert.match(source, /<MotDePasse[^>]*echecCompte/,
      "le formulaire de mot de passe n'est pas averti");
    assert.match(source, /enregistrer/i,
      "rien ne prévient que le navigateur ne pourra pas enregistrer");
  });

  /**
   * `motifs` partait de `[]`, et `motifs.length > 0` gardait le menu. Trois
   * états étaient donc confondus : pas encore chargé, échec, et aucun motif
   * configuré. Sur une démo, un appel qui bronche retire la priorité (EF-407)
   * sans un mot.
   */
  it('les motifs : « indisponibles » ne se confond plus avec « aucun »', () => {
    const source = sansCommentaires(lire('front/src/accueil/Gestes.jsx'));
    // BORNÉ aux motifs. Un simple `useState(null)` était vert après
    // mutation : ce fichier en contient un autre, sans rapport. Troisième
    // garde de la journée verte par accident sur un motif trop large.
    assert.match(source, /\[\s*motifs\s*,\s*setMotifs\s*\]\s*=\s*useState\(null\)/,
      "les motifs ne partent plus de `null` : l'échec se confond avec la liste vide");
    assert.match(source, /echecMotifs/, "l'échec des motifs n'est pas retenu");
  });

  /**
   * `/api/tableau-de-bord` est relu toutes les cinq minutes. Avalé, un chiffre
   * périmé pouvait rester à l'écran indéfiniment sans que rien ne le dise —
   * et un chiffre faux lu comme vrai est pire qu'un chiffre absent.
   */
  it('le tableau de bord : un chiffre périmé est annoncé comme tel', () => {
    const source = sansCommentaires(lire('front/src/accueil/Exploitation.jsx'));
    assert.match(source, /setEchecBord/, "l'échec des chiffres n'est pas retenu");
    assert.match(source, /echecBord\s*&&/, "l'échec n'est pas rendu");
  });
});
