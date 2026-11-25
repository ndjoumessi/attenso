import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

/**
 * Le même source, commentaires retirés : ces gardes portent sur ce que le code
 * FAIT, et un jeton cité dans un commentaire ne doit pas les satisfaire.
 */
const sansCommentaires = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .filter((l) => !/^\s*(\/\/|\*)/.test(l))
  .join('\n');

const AFFICHE = 'front/src/accueil/vues/Affiche.jsx';
const TABLEAU = 'front/src/accueil.jsx';

/** Le source d'`Affiche`, de son `export function` à la fin du fichier. */
const corpsAffiche = () => {
  const source = sansCommentaires(lire(AFFICHE));
  const debut = source.indexOf('export function Affiche(');
  assert.notEqual(debut, -1, 'Affiche introuvable');
  return source.slice(debut);
};

describe('la vue Affiche', () => {
  it("l'affiche s'imprime dans un nouvel onglet, sans quitter le tableau", () => {
    // Le lien « Retour au tableau » de /affiche mène à /connexion : le suivre
    // ferait perdre au personnel l'état du tableau.
    //
    // UNE expression, UNE balise : trois lookaheads bornés à l'intérieur d'un
    // même `<a …>` (`[^>]*` ne franchit pas le `>` qui le ferme). Trois jetons
    // cherchés dans tout le fichier tomberaient sur des éléments différents.
    // `rel="noopener"` : la convention d'inscription.jsx, et sans elle la page
    // ouverte obtient `window.opener` vers le tableau du personnel.
    assert.match(corpsAffiche(),
      /<a\b(?=[^>]*\bhref=(?:"\/affiche"|\{"\/affiche"\}))(?=[^>]*\btarget="_blank")(?=[^>]*\brel="[^"]*\bnoopener\b[^"]*")[^>]*>/,
      "le lien vers /affiche n'a pas, sur le MÊME élément, href, target=\"_blank\" et rel=\"noopener\"");
  });

  it("trois états, pas deux : en cours, chargée, en échec", () => {
    const corps = corpsAffiche();
    assert.match(corps, /\[affiche, setAffiche\] = useState\(null\)/,
      "l'affiche démarre autrement que `null` : faux aperçu vide au chargement");
    assert.match(corps, /\{echec && \(\s*<div[^>]*>\s*<Erreur>\s*L'affiche n'a pas pu être chargée/,
      "l'échec du chargement ne se dit pas");
    assert.match(corps, /\{!affiche && !echec && \(\s*<p role="status"[^>]*>\s*Chargement de l'affiche/,
      "l'attente de la réponse ne se dit pas, ou sa condition n'est plus liée à l'échec");
    assert.match(corps, /\.catch\(\(\) => \{ if \(!annule\) setEchec\(true\); \}\)/,
      "le catch ne pose pas l'échec");
    assert.match(corps, /\{affiche && \(\s*<div[^>]*>\s*<p[^>]*>\{affiche\.nom\}/,
      "l'aperçu n'est pas conditionné à la réponse");
  });

  it("la vue montre le QR, l'adresse lisible et la promesse, DANS le bloc chargé", () => {
    const corps = corpsAffiche();
    // Le bloc, de `{affiche && (` à son `)}` (six espaces, seul à ce retrait) :
    // hors de lui, `affiche.qr` serait lu quand `affiche` vaut `null`.
    const bloc = corps.match(/\{affiche && \(([\s\S]*?)\n {6}\)\}/);
    assert.ok(bloc, "le bloc `{affiche && (` est introuvable");
    assert.match(bloc[1], /<Qr qr=\{affiche\.qr\}/, "le QR n'est pas dans l'aperçu");
    assert.match(bloc[1], /\{urlLisible\(affiche\.url\)\}/,
      "l'adresse n'est pas écrite sous sa forme lisible");
    assert.match(bloc[1], /\{promesse\(affiche\)\}/,
      "la promesse faite au client n'est pas dans l'aperçu");
    // Une seule occurrence de chacun, et c'est celle du bloc.
    assert.equal(corps.split('<Qr ').length, 2, 'un <Qr> hors du bloc chargé');
    assert.equal(corps.split('promesse(').length, 2, 'un appel à promesse hors du bloc');
    assert.equal(corps.split('urlLisible(').length, 2, 'un appel à urlLisible hors du bloc');
    assert.match(sansCommentaires(lire(AFFICHE)), /import \{[^}]*\burlLisible\b[^}]*\} from/);
  });

  it("l'appel porte le jeton, et la réponse peuple l'aperçu en effaçant l'échec", () => {
    const corps = corpsAffiche();
    assert.match(corps, /appel\('\/api\/affiche', \{ headers: entete\(jeton\) \}\)/,
      "l'appel part sans le jeton");
    // UNE expression : le peuplement et la remise à zéro de l'échec sont sur
    // le chemin de SUCCÈS. Sans le premier, « Chargement » reste affiché pour
    // toujours ; sans la seconde, l'erreur survit à une relecture réussie.
    assert.match(corps,
      /\.then\(\(recu\) => \{ if \(!annule\) \{ setAffiche\(recu\); setEchec\(false\); \} \}\)/,
      "le succès ne peuple pas l'aperçu, ou ne remet pas l'échec à zéro");
    assert.match(corps, /\}, \[jeton\]\);/,
      "le chargement ne se refait plus quand le jeton change");
  });

  it("l'appel est annulé au démontage, et ne relit pas en boucle", () => {
    const corps = corpsAffiche();
    assert.match(corps, /let annule = false;[\s\S]{0,400}return \(\) => \{ annule = true; \};/);
    assert.ok(!/setInterval/.test(corps));
  });
});

describe("le titre n'est pas écrit deux fois", () => {
  const tableau = sansCommentaires(lire(TABLEAU));

  for (const nom of ['Affiche', 'MotDePasse']) {
    it(`${nom} : la vue passe titre={false}`, () => {
      assert.match(tableau, new RegExp(`<${nom}\\b[^>]*\\btitre=\\{false\\}[^>]*/>`),
        "la vue laisse la section redoubler le h1 de l'en-tête");
    });
  }

  it("Affiche : la section déclare `titre = true` et conditionne son Titre", () => {
    const corps = corpsAffiche();
    assert.match(corps, /export function Affiche\(\{[^}]*\btitre = true\b[^}]*\}\)/);
    assert.match(corps, /\{titre && <Titre>L'affiche du comptoir<\/Titre>\}/);
  });

  it("MotDePasse : la section déclare `titre = true` et conditionne son Titre", () => {
    assert.match(tableau, /function MotDePasse\(\{[^}]*\btitre = true\b[^}]*\}\)/);
    assert.match(tableau, /\{titre && <Titre>Mon mot de passe<\/Titre>\}/);
  });

  it("le tableau monte Affiche depuis la vue, et ne la définit plus", () => {
    assert.match(tableau, /import \{ Affiche \} from '\.\/accueil\/vues\/Affiche\.jsx'/);
    assert.ok(!/\nfunction Affiche\(/.test(tableau));
  });
});
