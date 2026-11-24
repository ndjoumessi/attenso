// Pré-rend la vitrine en HTML pur, à la construction.
//
// Trois raisons, et aucune n'est l'élégance. Un prospect arrive souvent sur un
// réseau lent : la page doit se lire avant que le JavaScript ait fini de
// réfléchir. Un moteur de recherche doit y trouver les prix. Et deux tests
// Java — PrixAfficheTest, PagesLegalesIntegrationTest — lisent ce fichier pour
// vérifier que les tarifs affichés sont ceux de l'énumération Palier et que
// les pages légales sont liées : une coquille React vide les rendrait aveugles.
import { build } from 'vite';
import react from '@vitejs/plugin-react';
import { createElement } from 'react';
import { renderToStaticMarkup, renderToString } from 'react-dom/server';
import { readFile, writeFile, rm, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';

const ici = import.meta.dirname;

// Une ligne par page pré-rendue. « hydrate » dit si la page reprend vie côté
// client : la vitrine n'a rien d'interactif et ne charge aucun JavaScript,
// le comptoir est un formulaire et doit s'hydrater — son premier rendu ne
// dépend donc d'aucune donnée, sinon React verrait un balisage différent du
// sien et rejetterait l'hydratation.
const PAGES = [
  { nom: 'vitrine', composant: 'Vitrine', source: 'src/vitrine.jsx', hydrate: false },
  { nom: 'comptoir', composant: 'Comptoir', source: 'src/comptoir.jsx', hydrate: true },
  { nom: 'suivi', composant: 'Suivi', source: 'src/suivi.jsx', hydrate: true },
  { nom: 'inscription', composant: 'Inscription', source: 'src/inscription.jsx', hydrate: true },
  { nom: 'reinitialisation', composant: 'Reinitialisation', source: 'src/reinitialisation.jsx', hydrate: true },
  { nom: 'mentions', composant: 'Mentions', source: 'src/mentions.jsx', hydrate: true },
  { nom: 'cgv', composant: 'Page', source: 'src/cgv.jsx', hydrate: true },
  { nom: 'carnet', composant: 'Carnet', source: 'src/carnet.jsx', hydrate: true },
  { nom: 'rendez-vous', composant: 'RendezVous', source: 'src/rendez-vous.jsx', hydrate: true },
  { nom: 'reservation', composant: 'Reservation', source: 'src/reservation.jsx', hydrate: true },
  { nom: 'confidentialite', composant: 'Page', source: 'src/confidentialite.jsx', hydrate: true },
];

// configFile FAUX : sans ça, cette construction hérite des entrées HTML du
// client déclarées dans vite.config.js, compile les pages au lieu des
// composants et rend des modules sans aucun export — l'erreur se lit alors
// « Element type is invalid », à trois fichiers de sa cause.
await build({
  root: ici,
  configFile: false,
  plugins: [react()],
  logLevel: 'warn',
  build: {
    ssr: true,
    outDir: resolve(ici, '.ssr'),
    emptyOutDir: true,
    rollupOptions: {
      input: Object.fromEntries(PAGES.map((p) => [p.nom, resolve(ici, p.source)])),
      output: { entryFileNames: '[name].js' },
    },
  },
});

for (const page of PAGES) {
  const sortie = resolve(ici, `../src/main/resources/static/app/${page.nom}.html`);
  const module = await import(resolve(ici, `.ssr/${page.nom}.js`));
  // renderToString pour une page qui s'hydrate, renderToStaticMarkup sinon.
  // Le premier pose les marqueurs que hydrateRoot attend pour retrouver les
  // nœuds de texte ; sans eux l'hydratation échoue en « erreur React #418 »,
  // qui ne dit rien de sa cause. Le second rend un HTML plus propre, et
  // convient aux pages sans JavaScript.
  const rendu = page.hydrate ? renderToString : renderToStaticMarkup;
  const corps = rendu(createElement(module[page.composant]));

  const coquille = await readFile(sortie, 'utf8');
  const marque = '<div id="racine"></div>';
  if (!coquille.includes(marque)) {
    throw new Error(`point d'injection « ${marque} » absent de ${sortie}`);
  }
  await writeFile(sortie, coquille.replace(marque, `<div id="racine">${corps}</div>`));
  console.log(`${page.nom} pré-rendue : ${corps.length} caractères de HTML`);
}

await rm(resolve(ici, '.ssr'), { recursive: true, force: true });

// --- La coquille hors réseau -------------------------------------------
//
// Elle n'est plus tenable à la main : les bundles portent une empreinte qui
// change à chaque build. Une liste écrite dans le dépôt serait périmée au
// premier déploiement, et la coquille servirait des pages sans style ni
// script — pire que pas de coquille du tout.
//
// Ne sont mises en cache que les pages dont le MODE DÉGRADÉ a un sens : le
// tableau de l'accueil, qui doit rester utilisable pendant une panne, la page
// client et l'écran du mur. La vitrine en est absente : un prospect hors
// réseau n'existe pas.
const STATIQUE = resolve(ici, '../src/main/resources/static');
const PAGES_HORS_RESEAU = ['accueil', 'suivi', 'comptoir', 'ecran'];

// Le manifeste de Vite donne, pour chaque page, son fichier et ses imports
// transitifs. On ne met donc en cache QUE ce que ces quatre pages chargent —
// pas les bundles de la vitrine ou de l'inscription, qui n'ont rien à faire
// dans une coquille de mode dégradé.
const manifeste = JSON.parse(
  await readFile(resolve(STATIQUE, 'app/manifeste.json'), 'utf8'));

const nécessaires = new Set();
const suivre = (cle, vus = new Set()) => {
  if (vus.has(cle)) return;
  vus.add(cle);
  const entree = manifeste[cle];
  if (!entree) return;
  if (entree.file && !entree.file.endsWith('.html')) nécessaires.add(`/${entree.file}`);
  for (const feuille of entree.css ?? []) nécessaires.add(`/${feuille}`);
  for (const suivant of [...(entree.imports ?? []), ...(entree.dynamicImports ?? [])]) {
    suivre(suivant, vus);
  }
};
for (const page of PAGES_HORS_RESEAU) suivre(`app/${page}.html`);
const assets = [...nécessaires].sort();

const coquille = [
  ...PAGES_HORS_RESEAU.map((p) => `/app/${p}.html`),
  ...assets,
  '/manifest.webmanifest',
  // Les icônes font partie de la coquille : sans elles, une notification
  // poussée arrivant hors réseau s'afficherait sans sa vignette.
  '/app/icone-192.png',
];

// Le nom du cache dérive de la liste : il change tout seul quand un fichier
// change, et l'ancien cache est effacé à l'activation. Plus de version à
// penser à incrémenter — c'est précisément ce qu'on oublie.
const empreinte = createHash('sha256').update(coquille.join('\n')).digest('hex').slice(0, 12);

const gabarit = await readFile(resolve(ici, 'sw.js'), 'utf8');
await writeFile(resolve(STATIQUE, 'sw.js'), gabarit
  .replace('__CACHE__', `attenso-coquille-${empreinte}`)
  .replace('__COQUILLE__', JSON.stringify(coquille, null, 2)));

console.log(`coquille : ${coquille.length} fichiers, cache attenso-coquille-${empreinte}`);
