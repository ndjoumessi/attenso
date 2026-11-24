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
    // CIBLES FIGÉES, et non la valeur par défaut de Vite.
    //
    // Sans cette déclaration, Vite retombe sur « baseline-widely-available »,
    // qui est une FENÊTRE GLISSANTE : ce que ce mot désigne avance tout seul
    // avec le temps. Une reconstruction dans six mois pourrait donc relever le
    // plancher sans que rien ne change dans ce dépôt, et casser la tablette de
    // l'accueil sans qu'aucun test ne rougisse. ENF-08 demande Safari iOS 16+,
    // Chrome Android et bureau (deux dernières versions) : on l'écrit.
    //
    // Les planchers Chrome, Edge et Firefox sont du mou — « deux dernières
    // versions » est très au-dessus. Le seul qui contraigne est safari16.
    target: ['safari16', 'chrome107', 'edge107', 'firefox104'],

    // LE CSS NE TIENT PAS LE MÊME PLANCHER, et c'est une dette assumée.
    //
    // Tailwind 4 annonce Safari 16.4, et la feuille produite le confirme :
    // 48 règles @property (Safari 16.4) et 6 color-mix() (Safari 16.2).
    // Baisser cette cible n'y changerait rien — esbuild abaisse de la syntaxe,
    // il ne remplace pas une fonctionnalité absente. La déclarer sert donc à
    // deux choses : empêcher la dérive vers le haut, et inscrire l'écart avec
    // ENF-08 à l'endroit où il se décide. Sur iOS 16.0 à 16.3, les propriétés
    // personnalisées de Tailwind perdent leur valeur initiale et la mise en
    // forme se dégrade ; la page reste lisible et utilisable.
    cssTarget: ['safari16.4', 'chrome111', 'edge111', 'firefox128'],

    // emptyOutDir FAUX : le répertoire de sortie contient aussi des SOURCES
    // suivies par git — jetons.css, attenso.js, journal.js, les vue-*.js, les
    // icônes et le manifeste — que le front importe. Vite les effacerait sans
    // prévenir. (Le motif d'origine, les pages écrites à la main, a disparu
    // avec la migration : il n'en reste aucune.)
    outDir: resolve(import.meta.dirname, '../src/main/resources/static'),
    emptyOutDir: false,
    // Sous /app/ : c'est le seul préfixe que la configuration de sécurité
    // ouvre au public. Mettre les bundles ailleurs demanderait d'y toucher.
    assetsDir: 'app/assets',
    // Le manifeste dit quels morceaux chaque page charge réellement. C'est
    // lui qui permet à la coquille hors réseau de ne contenir que ce qui lui
    // sert, au lieu de tout ce que la construction a produit.
    manifest: 'app/manifeste.json',
    rollupOptions: {
      input: {
        vitrine: resolve(import.meta.dirname, 'app/vitrine.html'),
        ecran: resolve(import.meta.dirname, 'app/ecran.html'),
        comptoir: resolve(import.meta.dirname, 'app/comptoir.html'),
        suivi: resolve(import.meta.dirname, 'app/suivi.html'),
        inscription: resolve(import.meta.dirname, 'app/inscription.html'),
        reinitialisation: resolve(import.meta.dirname, 'app/reinitialisation.html'),
        affiche: resolve(import.meta.dirname, 'app/affiche.html'),
        mentions: resolve(import.meta.dirname, 'app/mentions.html'),
        cgv: resolve(import.meta.dirname, 'app/cgv.html'),
        carnet: resolve(import.meta.dirname, 'app/carnet.html'),
        'rendez-vous': resolve(import.meta.dirname, 'app/rendez-vous.html'),
        reservation: resolve(import.meta.dirname, 'app/reservation.html'),
        accueil: resolve(import.meta.dirname, 'app/accueil.html'),
        confidentialite: resolve(import.meta.dirname, 'app/confidentialite.html'),
      },
    },
  },
});
