// Service worker minimal : met en cache la coquille, jamais les données.
//
// Volontairement limité. Mettre en cache une position dans une file
// produirait un écran qui affirme « 2 personnes devant vous » alors que c'est
// le tour du client depuis dix minutes — pire que pas d'écran du tout. Les
// réponses de l'API ne sont donc jamais servies depuis le cache.

const CACHE = 'attenso-coquille-v1';
const COQUILLE = [
  '/app/attenso.css',
  '/app/attenso.js',
  '/app/suivi.html',
  '/app/accueil.html',
  '/manifest.webmanifest',
];

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
  const url = new URL(evenement.request.url);
  if (evenement.request.method !== 'GET' || url.pathname.startsWith('/api/')) {
    return; // les données passent toujours par le réseau
  }
  evenement.respondWith(
    fetch(evenement.request).catch(() => caches.match(evenement.request)),
  );
});
