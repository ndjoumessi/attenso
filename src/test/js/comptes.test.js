import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const rendu = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '')
  .replace(/^import[\s\S]*?;$/gm, '');

const EXPLOITATION = 'front/src/accueil/Exploitation.jsx';

/**
 * TROUVÉ EN REGARDANT : j'ai cliqué « Désactiver » sur le seul compte gérant,
 * et le refus s'est affiché en haut, DANS LE FORMULAIRE D'INVITATION, entre
 * le menu « Rôle » et le bouton « Inviter ».
 *
 * <p>Le message lui-même est juste et dit quoi faire — « Il doit rester au
 * moins un gérant actif. Nommez d'abord quelqu'un d'autre gérant, puis
 * recommencez. » — et le serveur garde bien (`DerniereGerance`). C'est sa
 * PLACE qui ment : on croit que l'invitation a échoué, et le compte qu'on
 * visait n'affiche rien.
 *
 * <p>La cause : une seule variable d'erreur, alimentée par tous les gestes et
 * rendue à un seul endroit.
 */
describe("le refus d'un geste s'affiche là où le geste a été fait", () => {
  it("retient la CIBLE du refus, pas seulement son message", () => {
    const source = rendu(lire(EXPLOITATION));
    assert.match(source, /setErreurEquipe\(\s*\{[\s\S]{0,160}?cible/,
      "l'erreur ne porte pas la cible du geste : elle ne peut pas s'afficher au bon endroit");
  });

  it("n'affiche dans le formulaire que le refus d'une invitation", () => {
    const source = rendu(lire(EXPLOITATION));
    // Fenêtre bornée : la condition doit tenir à l'`Erreur` du formulaire.
    assert.match(source, /erreurEquipe\?\.cible === 'invitation'[\s\S]{0,120}?<\/Erreur>|<Erreur>\{erreurEquipe\?\.cible === 'invitation'/,
      "le formulaire d'invitation affiche encore les refus des autres gestes");
  });

  it('affiche le refus sur la ligne du compte visé', () => {
    const source = rendu(lire(EXPLOITATION));
    assert.match(source, /erreurEquipe\?\.cible === membre\.id/,
      'la ligne du compte visé ne montre pas son refus');
  });

  /**
   * Chaque geste doit DIRE sa cible. Un appel qui l'oublie retombe dans le
   * silence d'origine : l'erreur existe et ne s'affiche nulle part.
   */
  it('chaque geste nomme sa cible', () => {
    const source = rendu(lire(EXPLOITATION));
    // PAR COMPTAGE, sur un paramètre NOMMÉ. Ma première version lisait les
    // arguments avec `[^)]*`, qui s'arrête au premier `)` — or la route
    // contient `${membre.id}` et des parenthèses. La garde retrouvait donc
    // « membre.id » dans la ROUTE et restait verte sans l'argument : une
    // mutation y a survécu. Le paramètre nommé rend la correspondance
    // comptable, et un appel sans cible fait tomber le compte.
    // La DÉFINITION `function agir({ … })` se comptait comme un appel :
    // 4 gestes pour 3 cibles, et la garde tombait sur un code juste.
    const gestes = (source.match(/(?<!function )agir\(\{/g) ?? []).length;
    const cibles = (source.match(/\bcible:/g) ?? []).length;
    assert.ok(gestes >= 3, `trop peu de gestes trouvés : ${gestes}`);
    assert.equal(cibles, gestes,
      `${gestes} gestes pour ${cibles} cibles : un refus ne s'affichera nulle part`);
  });
});
