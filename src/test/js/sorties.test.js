import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (chemin) => readFileSync(resolve(RACINE, chemin), 'utf8');

const rendu = (source) => source
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\{\/\*[\s\S]*?\*\/\}/g, '')
  .replace(/^\s*\/\/.*$/gm, '');

/**
 * Les destinations qui sortent d'une page : le site, le légal, la connexion.
 *
 * La DESTINATION, pas la forme de l'attribut. `chrome.jsx` tient les siennes
 * dans un tableau parcouru — `href={href}` — donc une expression ancrée sur
 * `href="…"` ne les voit pas et déclare des impasses qui n'en sont pas.
 * C'est la troisième fois que ce dépôt me prend à écrire une garde sur une
 * façon d'écrire plutôt que sur la propriété.
 */
const SORTIES = new RegExp([
  'href=["\'`]/["\'`]',
  ...['connexion', 'inscription', 'mentions-legales', 'cgv', 'confidentialite']
    .map((d) => `["\'\`]/${d}["\'\`]`),
].join('|'));

/**
 * Une page peut sortir PAR UN COMPOSANT qu'elle rend — `PageLegale` porte le
 * retour et la bande pour les trois pages légales. Lire le seul fichier de la
 * page déclarerait donc des impasses qui n'en sont pas : il faut suivre la
 * délégation.
 */
function sort(chemin, vus = new Set()) {
  if (vus.has(chemin) || !existsSync(resolve(RACINE, chemin))) return false;
  vus.add(chemin);
  const brut = rendu(lire(chemin));
  const corps = brut.replace(/^import[\s\S]*?;$/gm, '');
  if (SORTIES.test(corps)) return true;
  const base = dirname(chemin);
  // On ne suit que les composants RENDUS, pas les modules importés. Deux
  // mutations ont survécu à la version d'avant : retirer `<BandeLegale />`
  // laissait sa ligne d'`import`, et la garde trouvait la sortie quand même.
  // Quatrième fois aujourd'hui qu'un import est compté comme un usage.
  return [...brut.matchAll(/^import\s*\{([^}]*)\}\s*from\s*['"](\.[^'"]+)['"]/gm)]
    .filter(([, noms]) => noms.split(',')
      .some((n) => new RegExp(`<${n.trim()}[\\s/>]`).test(corps)))
    .some(([, , rel]) => sort(resolve(base, rel).replace(`${RACINE}/`, ''), vus));
}

/**
 * Signalé à l'usage : « dans l'application il est impossible d'aller au login
 * ou à l'accueil ». Vérifié, et exact : `accueil.jsx` ne contenait AUCUN
 * `href` — la seule sortie du tableau était la déconnexion — et cinq pages
 * clientes n'offraient aucun lien du tout.
 */
describe("aucune page n'est une impasse", () => {
  /** Chaque exemption porte sa raison, et la raison est vérifiable. */
  const EXEMPTES = new Map([
    ['ecran.jsx',
      "l'écran du mur est en lecture seule et accroché en hauteur : personne "
      + "ne le touche, et un lien qu'on ne peut pas cliquer n'est pas une sortie"],
  ]);

  const PAGES = readdirSync(resolve(RACINE, 'front/src'))
    .filter((f) => f.endsWith('.jsx') && !f.endsWith('-client.jsx')
      && f !== 'legal-prose.jsx');

  it('toutes mènent quelque part, sauf celle dont la raison est écrite', () => {
    const impasses = PAGES.filter((p) => !EXEMPTES.has(p) && !sort(`front/src/${p}`));
    assert.deepEqual(impasses, [],
      'ces pages ne mènent nulle part : une personne qui y arrive est bloquée');
  });

  it("et l'exemption n'est pas oubliée en route", () => {
    for (const [page, raison] of EXEMPTES) {
      assert.ok(PAGES.includes(page), `exemption d'une page disparue : ${page}`);
      assert.ok(raison.length > 60, `exemption de ${page} sans raison écrite`);
      assert.ok(!sort(`front/src/${page}`),
        `${page} a désormais une sortie : son exemption n'a plus lieu d'être`);
    }
  });
});

describe('le tableau a une sortie vers le site', () => {
  it('la barre mène à la vitrine', () => {
    assert.match(rendu(lire('front/src/accueil/Barre.jsx')), /href="\/"/,
      'le tableau ne mène nulle part : seule la déconnexion en sort');
  });

  /**
   * Un lien, pas une déconnexion déguisée : aller voir le site ne doit ni
   * fermer la session ni vider les gestes en attente de rejeu.
   */
  it('sans rien fermer au passage', () => {
    const lien = rendu(lire('front/src/accueil/Barre.jsx'))
      .match(/<a href="\/"[\s\S]{0,260}?<\/a>/)?.[0] ?? '';
    assert.ok(lien, 'le lien vers le site est introuvable');
    assert.doesNotMatch(lien, /onClick|quitter|removeItem|vider/,
      'le lien vers le site emporte la session avec lui');
  });
});
