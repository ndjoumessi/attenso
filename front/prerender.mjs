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
import { renderToStaticMarkup } from 'react-dom/server';
import { readFile, writeFile, rm } from 'node:fs/promises';
import { resolve } from 'node:path';

const ici = import.meta.dirname;
const SORTIE = resolve(ici, '../src/main/resources/static/app/vitrine.html');

await build({
  root: ici,
  plugins: [react()],
  logLevel: 'warn',
  build: {
    ssr: resolve(ici, 'src/vitrine.jsx'),
    outDir: resolve(ici, '.ssr'),
    emptyOutDir: true,
    rollupOptions: { output: { entryFileNames: 'vitrine.js' } },
  },
});

const { Vitrine } = await import(resolve(ici, '.ssr/vitrine.js'));
const corps = renderToStaticMarkup(createElement(Vitrine));

const coquille = await readFile(SORTIE, 'utf8');
const marque = '<div id="racine"></div>';
if (!coquille.includes(marque)) {
  throw new Error(`point d'injection « ${marque} » absent de ${SORTIE}`);
}
await writeFile(SORTIE, coquille.replace(marque, `<div id="racine">${corps}</div>`));
await rm(resolve(ici, '.ssr'), { recursive: true, force: true });

console.log(`vitrine pré-rendue : ${corps.length} caractères de HTML`);
