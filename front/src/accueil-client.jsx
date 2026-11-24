import { createRoot } from 'react-dom/client';
import { Accueil } from './accueil.jsx';

// Monté et non hydraté : le tableau dépend entièrement de ce que la tablette
// a en mémoire — jeton, journal local, dernier tableau connu — et pré-rendre
// un écran de connexion à quelqu'un déjà connecté le ferait clignoter.
createRoot(document.getElementById('racine')).render(<Accueil />);

// Enregistré ici, et pas seulement déclaré : sans cet appel le fichier sw.js
// n'est jamais installé et la coquille n'est pas mise en cache. C'est tout le
// mode dégradé qui en dépend.
if ('serviceWorker' in navigator) {
  addEventListener('load', () => navigator.serviceWorker.register('/sw.js')
    .catch((echec) => console.warn('service worker non enregistré', echec)));
}
