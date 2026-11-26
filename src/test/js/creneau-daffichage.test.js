import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { balises } from './aide/balises.js';

const RACINE = resolve(import.meta.dirname, '../../..');
const lire = (c) => readFileSync(resolve(RACINE, c), 'utf8');
const rendu = (s) => s.replace(/\{\/\*[\s\S]*?\*\/\}/g, '').replace(/^\s*\/\/.*$/gm, '');

/** Les fichiers écrits à la main. `ui/` est du shadcn vendorisé : pas à nous. */
function sources(dossier = 'front/src', prefixe = '') {
  const out = [];
  for (const e of readdirSync(resolve(RACINE, dossier), { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (e.name === 'ui') continue;
      out.push(...sources(`${dossier}/${e.name}`, `${prefixe}${e.name}/`));
    }
    else if (e.name.endsWith('.jsx')) {
      out.push([`${prefixe}${e.name}`, rendu(lire(`${dossier}/${e.name}`))]);
    }
  }
  return out;
}

/**
 * Les tailles d'AFFICHAGE : à partir de `text-5xl` (48 px), un glyphe cesse
 * d'être de la ponctuation. C'est le seuil mesuré sur ce dépôt — le cadratin
 * du suivi faisait 58,5 px de large à `text-7xl`, celui de l'écran 281,6 px.
 * En dessous, un cadratin reste un tiret : les compteurs du tableau de bord
 * (`text-2xl`) s'en servent légitimement pour dire « pas de donnée ».
 */
const AFFICHAGE = /\btext-(5xl|6xl|7xl|8xl|9xl)\b|text-\[clamp\(\s*[5-9](\.\d+)?rem/;

/**
 * Les paragraphes en taille d'affichage, avec LEUR CONTENU.
 *
 * <p>La balise ouvrante vient du lecteur partagé (`aide/balises.js`), qui suit
 * la profondeur des accolades : un motif naïf s'arrêterait au `>` d'un
 * `${x ? 'a' : 'b'}` dans le `className` et rendrait une classe tronquée.
 *
 * <p>Le contenu s'arrête au premier `</p>`, et la garde VÉRIFIE qu'aucun `<p`
 * ne s'ouvre dans l'intervalle : sans ça, un paragraphe imbriqué ferait lire
 * la mauvaise étendue, et une garde qui lit le mauvais texte ne garde rien.
 */
function creneaux(source) {
  const out = [];
  let depuis = 0;
  for (const ouvrante of balises(source, 'p')) {
    const debut = source.indexOf(ouvrante, depuis);
    assert.notEqual(debut, -1, 'balise ouvrante introuvable : le lecteur a dérivé');
    depuis = debut + ouvrante.length;
    if (!AFFICHAGE.test(ouvrante)) continue;
    const fin = source.indexOf('</p>', depuis);
    assert.notEqual(fin, -1, 'paragraphe non refermé');
    const contenu = source.slice(depuis, fin);
    assert.ok(!contenu.includes('<p'), 'paragraphe imbriqué : étendue de lecture fausse');
    out.push(contenu);
  }
  return out;
}

describe("le créneau d'un grand chiffre", () => {
  // Trouvé à l'écran, jamais par une garde de texte : le texte était juste.
  // Celle-ci ne voit pas non plus la taille de rendu — elle garde le SIGNE
  // qui la produisait, puisque c'est lui qu'on a mesuré.
  it('ne dessine jamais de cadratin en taille d\'affichage', () => {
    const fautifs = [];
    for (const [nom, source] of sources()) {
      for (const contenu of creneaux(source)) {
        if (contenu.includes('\u2014')) fautifs.push(nom);
      }
    }
    assert.deepEqual([...new Set(fautifs)], [],
      'un cadratin à cette taille est une barre pleine, pas un tiret');
  });

  // La hauteur, elle, doit RESTER : mesuré dans la police réelle, la carte du
  // suivi fait 206 px avec ou sans chiffre. Un créneau vraiment vide la ferait
  // sauter de 72 px à l'arrivée du nombre.
  for (const [page, attendu] of [['suivi.jsx', 1], ['rendez-vous.jsx', 1]]) {
    it(`${page} tient la hauteur avec un espace insécable`, () => {
      const source = rendu(lire(`front/src/${page}`));
      const trouves = creneaux(source);
      assert.equal(trouves.length, attendu,
        `${page} : ${trouves.length} créneau(x) d'affichage, attendu ${attendu}`);
      assert.match(trouves[0], /: '\\u00A0'/,
        `${page} : le créneau vide doit tenir la ligne, pas disparaître`);
    });
  }
});
