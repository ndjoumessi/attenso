import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');

/** Imports retirés EN PLUS des commentaires : une garde qui cherche
 *  « BandeLegale » dans le fichier trouve la ligne d'import et reste verte
 *  quand le composant n'est plus rendu. Mutation mesurée, et c'est la
 *  deuxième fois dans la même journée que ce piège se referme. */
const rendu = (source) => sansCommentaires(source)
  .replace(/^import[\s\S]*?;$/gm, '');

const CHROME = 'front/src/composants/chrome.jsx';

/** Toutes les pages et tous les composants, sans exception nommée à la main :
 *  un fichier ajouté demain entre dans la garde sans qu'on y pense. */
const TOUS = ['front/src', 'front/src/accueil', 'front/src/composants']
  .flatMap((dossier) => readdirSync(resolve(RACINE, dossier))
    .filter((f) => f.endsWith('.jsx'))
    .map((f) => `${dossier}/${f}`));

/**
 * La bande légale était écrite TROIS fois — vitrine, pages légales,
 * connexion — et le retour au site QUATRE. Une seule copie portait les
 * classes qui donnent 44 px, celle corrigée le matin même : mesuré en
 * production, le pied de la vitrine rendait cinq cibles à 17 px et la
 * coquille légale autant.
 *
 * Corriger les copies une par une, c'est ce qui a produit le deuxième
 * défaut le même jour. La garde porte donc sur l'UNICITÉ, pas sur les
 * classes de chaque page : tant qu'il n'y a qu'un endroit, il n'y a qu'un
 * endroit à mesurer.
 */
describe('la coquille partagée des pages publiques', () => {
  it('est le seul endroit qui écrit la bande légale', () => {
    const coupables = TOUS.filter((chemin) => chemin !== CHROME
      && /mentions-legales/.test(sansCommentaires(lire(chemin))));
    assert.deepEqual(coupables, [],
      'la bande légale est réécrite ailleurs : une copie finira sous 44 px');
  });

  it('est le seul endroit qui écrit un retour en flèche', () => {
    const coupables = TOUS.filter((chemin) => chemin !== CHROME
      && /←/.test(sansCommentaires(lire(chemin))));
    assert.deepEqual(coupables, [], 'un retour en flèche est écrit hors de la coquille');
  });

  /**
   * `cible-min` règle une hauteur MINIMALE, qui n'a AUCUN effet sur un
   * élément en ligne. Les deux classes sont donc exigées ensemble : l'une
   * sans l'autre ne fait rien, et c'est précisément l'erreur qui a donné
   * 17 px alors que la bonne classe était posée.
   */
  it('donne à chacun de ses liens les deux classes qui, ensemble, font 44 px', () => {
    // Les constantes de classe sont RÉSOLUES avant de juger. Ma première
    // version exigeait les classes en littéral dans la balise ; elle était
    // rouge sur un composant correct, parce qu'un des liens passe par une
    // constante. C'est le test qui avait tort, et c'est dit plutôt que fait
    // en silence.
    const brut = sansCommentaires(lire(CHROME));
    // Les fragments sont lus quel que soit leur délimiteur : une constante
    // peut ouvrir sur `'…'` et finir sur un gabarit `` `…` `` depuis qu'elle
    // interpole l'anneau partagé. Le motif d'avant n'acceptait que les
    // guillemets simples et ne trouvait alors plus RIEN — c'est l'assertion
    // de garde ci-dessous qui l'a dit, au lieu de laisser passer un test
    // devenu vide.
    const constantes = [...brut.matchAll(/const ([A-Z_]+) = ([\s\S]*?);\n/g)]
      .map(([, nom, valeur]) => [nom, (valeur.match(/'[^']*'|`[^`]*`/g) ?? [])
        .join(' ').replaceAll(/['`]/g, '')]);
    const source = constantes.reduce(
      (texte, [nom, valeur]) => texte.replaceAll(`{${nom}}`, `"${valeur}"`), brut,
    );
    assert.ok(constantes.length > 0, 'plus aucune constante de classe à résoudre');
    const liens = source.match(/<a[\s\S]*?>/g) ?? [];
    assert.ok(liens.length >= 2, 'la coquille ne rend plus de liens');
    for (const lien of liens) {
      assert.match(lien, /cible-min/, `lien sans plancher tactile : ${lien.slice(0, 50)}`);
      assert.match(lien, /inline-flex|inline-block/,
        `« cible-min » est sans effet en ligne : ${lien.slice(0, 50)}`);
    }
  });

  /** Les trois destinations légales, et le retour, restent joignables. */
  it('mène bien aux trois pages légales', () => {
    const source = sansCommentaires(lire(CHROME));
    for (const chemin of ['/mentions-legales', '/cgv', '/confidentialite']) {
      assert.ok(source.includes(`'${chemin}'`) || source.includes(`"${chemin}"`),
        `${chemin} manque`);
    }
  });

  it('est réellement employée par la vitrine et les pages légales', () => {
    for (const chemin of ['front/src/vitrine.jsx', 'front/src/composants/legal.jsx']) {
      assert.match(rendu(lire(chemin)), /<BandeLegale[\s/>]/,
        `${chemin} n'emploie pas la bande partagée`);
    }
  });
});

/**
 * Les pages légales, où l'anneau de focus était revenu à celui du navigateur.
 *
 * <p>Mesuré au clavier sur la production le 2026-11-28 : en tabulant dans le
 * corps de `/confidentialite`, le lien « CNIL » rendait
 * `1px auto rgb(153, 200, 255)` — le bleu par défaut — pendant que le lien
 * « Mentions légales » de la bande, deux centimètres plus bas, rendait
 * `2px solid rgb(78, 168, 138)`, celui du produit. Deux anneaux sur la même
 * page : exactement ce que `ANNEAU` dit avoir corrigé, revenu par la porte des
 * pages légales.
 *
 * <p>`PageLegale` habillait ses liens descendants en couleur et soulignement,
 * pas en focus. Sept liens étaient concernés : deux écrits dans
 * `mentions.jsx`, cinq dans le HTML juridique injecté — qui ne peut porter
 * aucune classe, puisqu'il n'est pas du JSX. C'est donc le CONTENEUR qui doit
 * les habiller, et c'est pour ça que la garde porte sur lui.
 */
describe('le corps des pages légales habille ses liens comme le reste du produit', () => {
  const LEGAL = 'front/src/composants/legal.jsx';

  it("donne à ses liens descendants l'anneau du produit", () => {
    const source = rendu(lire(LEGAL));
    // Sur la MÊME étendue que la couleur : un anneau posé sur un autre
    // élément de la page ne toucherait pas les liens du corps juridique.
    // Guillemets droits OU gabarit : la forme de la chaîne n'est pas la
    // propriété. Ce qui compte est que l'anneau soit sur la MÊME étendue que
    // la couleur — posé sur un autre élément, il ne toucherait pas les liens.
    const conteneur = source.match(/className=(?:"[^"]*"|\{`[^`]*`\})/g)
      ?.find((c) => c.includes('[&_a]:underline')) ?? '';
    assert.ok(conteneur, 'le conteneur qui habille les liens est introuvable');
    assert.match(conteneur, /ANNEAU_LIENS|\[&_a\]:focus-visible:outline-\[var\(--anneau\)\]/,
      'le corps légal ne pose pas l\'anneau sur ses liens : ils retombent sur '
      + 'celui du navigateur, différent de celui de la bande juste en dessous');
  });

  /**
   * Un champ d'éditeur absent ne doit pas fabriquer un lien vers nulle part.
   * Mesuré en production : les DOUZE champs sont vides, et la page publique de
   * confidentialité offrait « à compléter » en lien cliquable vers `#` comme
   * seul chemin pour exercer ses droits.
   */
  it("ne rend pas un lien quand le courriel de l'éditeur manque", () => {
    const source = rendu(lire('front/src/legal-prose.jsx'));
    const boucle = source.match(/for \(const lien of[\s\S]*?\n {4}\}/)?.[0] ?? '';
    assert.ok(boucle, 'la boucle sur les liens de courriel est introuvable');
    assert.match(boucle, /removeAttribute\('href'\)/,
      'le gabarit garde son `href="#"` quand le courriel manque : '
      + '« à compléter » devient un lien qui ne mène nulle part');
    assert.match(boucle, /if \(editeur\.email\)/,
      'la pose du lien n\'est pas conditionnée à la présence du courriel');
    // RÉVERSIBLE, et c'est une régression vécue : `useEditeur` part de champs
    // vides, donc l'effet passe une première fois AVANT la réponse du serveur.
    // Une version qui retirait l'élément au lieu de son `href` ne trouvait plus
    // rien à remplir au retour de la réponse, et la page affichait
    // « à compléter » pour toujours — mesuré en production.
    assert.doesNotMatch(boucle, /replaceWith|\.remove\(\)/,
      'le passage détruit l\'élément : la réponse du serveur, qui arrive après, '
      + 'ne trouvera plus rien à remplir');
  });
});
