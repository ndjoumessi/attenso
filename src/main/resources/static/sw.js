// Service worker : sert la coquille quand le serveur est injoignable.
//
// Ce n'est pas un confort. Le journal des gestes de l'accueil vit dans
// IndexedDB et survit à une panne — mais si la tablette recharge la page
// pendant la panne et que le navigateur ne peut rien servir, l'ASV se retrouve
// devant une erreur réseau avec ses gestes enfermés dans une page qu'elle ne
// peut plus ouvrir. La promesse « le tableau reste utilisable » tient à ce
// fichier.
//
// En revanche les DONNÉES ne sont jamais servies depuis le cache : afficher
// « 2 personnes devant vous » alors que c'est le tour du client depuis dix
// minutes est pire que pas d'écran du tout.

const CACHE = 'attenso-coquille-v3';

const COQUILLE = [
  '/app/attenso.css',
  '/app/attenso.js',
  '/app/journal.js',
  '/app/accueil.html',
  '/app/suivi.html',
  '/app/comptoir.html',
  '/app/ecran.html',
  '/manifest.webmanifest',
];

/**
 * Miroir de ControleurPages : quelle page sert quelle URL. Une navigation vers
 * `/` doit retrouver `/app/accueil.html` dans le cache, sinon un rechargement
 * hors réseau échoue alors que la page est pourtant là.
 */
function pagePour(chemin) {
  if (chemin === '/' || chemin === '/index.html') return '/app/accueil.html';
  if (chemin.startsWith('/s/')) return '/app/suivi.html';
  if (chemin.startsWith('/t/')) return '/app/comptoir.html';
  if (chemin.startsWith('/ecran/')) return '/app/ecran.html';
  return null;
}

self.addEventListener('install', (evenement) => {
  evenement.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(COQUILLE)));
  self.skipWaiting();
});

self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(
    caches.keys().then((cles) =>
      Promise.all(cles.filter((cle) => cle !== CACHE).map((cle) => caches.delete(cle)))),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (evenement) => {
  const requete = evenement.request;
  const url = new URL(requete.url);

  // Les données passent toujours par le réseau, sans exception.
  if (requete.method !== 'GET' || url.origin !== self.location.origin
      || url.pathname.startsWith('/api/')) {
    return;
  }

  evenement.respondWith((async () => {
    try {
      return await fetch(requete);
    } catch (panne) {
      const direct = await caches.match(requete);
      if (direct) return direct;
      const page = pagePour(url.pathname);
      const repli = page ? await caches.match(page) : null;
      if (repli) return repli;
      throw panne;
    }
  })());
});
