import { hydrateRoot } from 'react-dom/client';
import { Suivi } from './suivi.jsx';

hydrateRoot(document.getElementById('racine'), <Suivi />);

// Enregistré ici, et pas seulement déclaré : sans cet appel le fichier sw.js
// n'est jamais installé, la coquille n'est pas mise en cache — et le push
// d'EF-307 n'a pas de worker auquel s'abonner.
if ('serviceWorker' in navigator) {
  addEventListener('load', () => navigator.serviceWorker.register('/sw.js')
    .catch((echec) => console.warn('service worker non enregistré', echec)));
}
