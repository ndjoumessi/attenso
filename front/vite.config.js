import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwind from '@tailwindcss/vite';
import { resolve } from 'node:path';

// Les écrans d'Attenso, construits dans les ressources de Spring.
//
// MULTI-PAGE, et non une application à page unique. Le routage reste côté
// serveur (ControleurPages) parce que les URL doivent être lisibles sur un QR
// imprimé et courtes dans un SMS : /t/{slug}, /s/{reference}. Une SPA les
// remplacerait par des routes client sur un index unique, ce qui casserait le
// service worker — qui associe un chemin à une page en cache — et obligerait
// chaque page à embarquer le code de toutes les autres.
export default defineConfig({
  root: import.meta.dirname,
  plugins: [react(), tailwind()],
  build: {
    // emptyOutDir FAUX : le répertoire contient encore les pages écrites à la
    // main, qui continuent d'être servies tant qu'elles ne sont pas migrées.
    // Vite le viderait sans prévenir.
    outDir: resolve(import.meta.dirname, '../src/main/resources/static'),
    emptyOutDir: false,
    // Sous /app/ : c'est le seul préfixe que la configuration de sécurité
    // ouvre au public. Mettre les bundles ailleurs demanderait d'y toucher.
    assetsDir: 'app/assets',
    rollupOptions: {
      input: {
        vitrine: resolve(import.meta.dirname, 'app/vitrine.html'),
        ecran: resolve(import.meta.dirname, 'app/ecran.html'),
      },
    },
  },
});
