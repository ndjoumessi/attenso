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
import { readFile, writeFile, rm } from 'node:fs/promises';
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
