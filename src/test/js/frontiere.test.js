import assert from 'node:assert/strict';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, it } from 'node:test';

const RACINE = resolve(import.meta.dirname, '../../..');
const SRC = resolve(RACINE, 'front/src');
const UI = resolve(SRC, 'ui') + '/';
const CONFIG = resolve(RACINE, 'front/vite.config.js');

/** La seule page qui a le droit d'atteindre front/src/ui/ : le tableau du personnel. */
const PAGE_PERSONNEL = 'accueil';

/**
 * Les pages client, DÉRIVÉES des entrées de construction et non recopiées :
 * une liste écrite ici se périmerait au premier ajout de page, et la garde
 * cesserait de couvrir la page nouvelle sans que rien ne rougisse.
 */
function pagesClient() {
  const source = readFileSync(CONFIG, 'utf8');
  const bloc = source.match(/input:\s*\{([^}]*)\}/);
  assert.ok(bloc, 'rollupOptions.input introuvable dans vite.config.js');
  const noms = [...bloc[1].matchAll(/^\s*(?:'([^']+)'|([\w-]+))\s*:/gm)]
    .map((m) => m[1] ?? m[2]);
  return noms.filter((n) => n !== PAGE_PERSONNEL);
}

/** Les spécificateurs importés par un source : statiques, dynamiques, réexports. */
function specificateurs(source) {
  const motifs = [
    /\bfrom\s*['"]([^'"]+)['"]/g,
    /\bimport\s*['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ];
  return motifs.flatMap((m) => [...source.matchAll(m)].map((x) => x[1]));
}

/**
 * LISTE BLANCHE des paquets qu'une page client peut atteindre. Volontairement
 * étroite : un `clsx`, un `vaul` ou un `cmdk` passait la liste noire d'avant.
 * L'élargir est une décision, pas l'effet de bord d'une installation.
 */
const PAQUETS_CLIENT = ['react', 'react-dom'];

const nomDuPaquet = (spec) =>
  spec.startsWith('@') ? spec.split('/').slice(0, 2).join('/') : spec.split('/')[0];

/** Un spécificateur relatif ou en alias `@/` devient un fichier ; un paquet reste nu. */
function resoudre(depuis, spec) {
  let base;
  if (spec.startsWith('@/')) base = resolve(SRC, spec.slice(2));
  else if (spec.startsWith('.')) base = resolve(dirname(depuis), spec);
  else return null;
  const candidats = [base, `${base}.jsx`, `${base}.js`, `${base}/index.jsx`, `${base}/index.js`];
  return candidats.find((c) => existsSync(c) && statSync(c).isFile()) ?? null;
}

/** Tous les fichiers atteints depuis les points d'entrée, entrées comprises. */
function importsTransitifs(entrees) {
  const vus = new Set();
  const pile = [...entrees];
  while (pile.length > 0) {
    const fichier = pile.pop();
    if (vus.has(fichier)) continue;
    vus.add(fichier);
    if (!/\.jsx?$/.test(fichier)) continue;
    for (const spec of specificateurs(readFileSync(fichier, 'utf8'))) {
      const cible = resoudre(fichier, spec);
      if (cible) pile.push(cible);
    }
  }
  return [...vus];
}

describe('frontière entre le tableau du personnel et les pages des clients', () => {
  it('la liste dérivée contient les pages attendues, et pas l\'accueil', () => {
    const pages = pagesClient();
    assert.ok(pages.includes('comptoir') && pages.includes('suivi'), `vu : ${pages}`);
    assert.ok(!pages.includes(PAGE_PERSONNEL));
    assert.ok(pages.length >= 10, `liste suspectement courte : ${pages}`);
  });

  it('chaque page client a ses fichiers, composant et hydratation quand la page en a une', () => {
    for (const page of pagesClient()) {
      const html = readFileSync(resolve(RACINE, `front/app/${page}.html`), 'utf8');
      // La vitrine est pré-rendue sans JavaScript : pas de script, pas de fichier -client.
      const fichiers = /<script[^>]+src=/.test(html)
        ? [`${page}.jsx`, `${page}-client.jsx`]
        : [`${page}.jsx`];
      for (const f of fichiers) {
        assert.ok(existsSync(resolve(SRC, f)), `${f} manquant : la garde ne couvrirait pas ${page}`);
      }
    }
  });

  it('aucune page client n\'atteint front/src/ui/', () => {
    for (const page of pagesClient()) {
      const entrees = [`${page}.jsx`, `${page}-client.jsx`]
        .map((f) => resolve(SRC, f))
        .filter((f) => existsSync(f));
      const atteints = importsTransitifs(entrees);
      const fautifs = atteints.filter((c) => c.startsWith(UI));
      const paquets = atteints.filter((f) => /\.jsx?$/.test(f)).flatMap((f) =>
        specificateurs(readFileSync(f, 'utf8'))
          .filter((spec) => !spec.startsWith('.') && !spec.startsWith('@/') && !spec.startsWith('node:'))
          .filter((spec) => !PAQUETS_CLIENT.includes(nomDuPaquet(spec))));
      assert.deepEqual(paquets, [], `${page} importe un paquet hors liste blanche`);
      assert.deepEqual(fautifs.map((c) => c.slice(RACINE.length + 1)), [],
        `${page} atteint front/src/ui/ : Radix partirait sur le téléphone d'un client`);
    }
  });
});
