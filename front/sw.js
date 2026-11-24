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

// Le nom du cache et la liste de la coquille sont ÉCRITS À LA CONSTRUCTION,
// par prerender.mjs. Ils ne peuvent plus l'être à la main : les pages et leurs
// bundles portent une empreinte qui change à chaque build, et une liste tenue
// manuellement serait périmée au premier déploiement — la coquille servirait
// alors des pages sans style ni script, ce qui est pire que pas de coquille.
//
// Le nom du cache dérive de cette liste : il change tout seul quand un fichier
// change, et l'ancien cache est effacé à l'activation. Plus de version à
// penser à incrémenter.
const CACHE = '__CACHE__';

const COQUILLE = __COQUILLE__;

/**
 * Miroir de ControleurPages : quelle page sert quelle URL. Une navigation vers
 * `/` doit retrouver `/app/accueil.html` dans le cache, sinon un rechargement
 * hors réseau échoue alors que la page est pourtant là.
 */
function pagePour(chemin) {
  // La vitrine n'est pas mise en cache : un prospect hors réseau n'existe pas.
  // (Le second motif d'origine — ses polices distantes — a disparu avec
  // elles ; la décision tient sur le premier seul.)
  if (chemin === '/accueil') return '/app/accueil.html';
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

// --- EF-307 : notifications poussées -------------------------------------
//
// Le corps arrive déchiffré ici : l'opérateur de push (Google, Apple, Mozilla)
// n'a relayé qu'un bloc chiffré pour cet abonnement, qu'il ne peut pas lire.
// C'est ce qui rend acceptable d'y mettre le numéro de ticket.
self.addEventListener('push', (evenement) => {
  // Un push sans contenu existe : certains opérateurs en envoient pour
  // réveiller le worker. On affiche quand même quelque chose, parce qu'une
  // notification promise et non affichée fait révoquer l'autorisation par le
  // navigateur.
  const corps = evenement.data ? evenement.data.text() : "C'est bientôt à vous.";
  evenement.waitUntil(self.registration.showNotification('Attenso', {
    body: corps,
    // Un tag unique : deux messages successifs remplacent l'ancien au lieu
    // d'empiler des bulles que personne ne lira.
    tag: 'attenso-tour',
    renotify: true,
    icon: '/app/icone-192.png',
    badge: '/app/icone-192.png',
  }));
});

// Toucher la notification rouvre la page de suivi déjà ouverte si elle existe,
// plutôt que d'en empiler une seconde.
self.addEventListener('notificationclick', (evenement) => {
  evenement.notification.close();
  evenement.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    .then((fenetres) => {
      const suivi = fenetres.find((f) => f.url.includes('/s/'));
      if (suivi) return suivi.focus();
      return self.clients.openWindow('/');
    }));
});
