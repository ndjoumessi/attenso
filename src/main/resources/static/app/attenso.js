// Attenso — fonctions communes aux quatre écrans. Aucune dépendance : la page
// client s'ouvre sur un téléphone mal capté, dans une rue, et doit s'afficher
// avant que le réseau ait fini de réfléchir.

/** Appel JSON. Rend le détail du problème renvoyé par le serveur, pas un code. */
export async function appel(url, options = {}) {
  const reponse = await fetch(url, {
    ...options,
    headers: {
      'Accept': 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  });
  if (!reponse.ok) {
    let detail = `Erreur ${reponse.status}`;
    try {
      const probleme = await reponse.json();
      detail = probleme.detail || probleme.title || detail;
    } catch { /* corps non JSON : on garde le code */ }
    const erreur = new Error(detail);
    erreur.statut = reponse.status;
    throw erreur;
  }
  return reponse.status === 204 ? null : reponse.json();
}

/**
 * Abonnement SSE avec reprise. Le serveur ne transmet pas l'état, seulement
 * « ça a bougé » : on relit. Un écran qui se reconnecte repart donc du vrai
 * état, et non d'un historique d'événements à rejouer.
 */
export function flux(url, surMaj) {
  let source = null;
  let delai = 1000;

  const brancher = () => {
    source = new EventSource(url);
    source.addEventListener('maj', () => { delai = 1000; surMaj(); });
    source.addEventListener('battement', () => { delai = 1000; });
    source.onerror = () => {
      source.close();
      setTimeout(brancher, delai);
      delai = Math.min(delai * 2, 30000);
    };
  };

  brancher();
  return () => source && source.close();
}

/** Bandeau hors ligne. Jamais modal : l'accueil doit pouvoir continuer. */
export function surveillerReseau(bandeau) {
  const peindre = () => { bandeau.hidden = navigator.onLine; };
  addEventListener('online', peindre);
  addEventListener('offline', peindre);
  peindre();
}

/**
 * File des actions à (re)transmettre.
 *
 * Chaque action porte un UUID généré ici, avant l'envoi : le serveur
 * l'enregistre en clé primaire, donc un rejeu n'applique rien deux fois. C'est
 * ce qui rend sûr de réessayer sans se demander si le premier envoi est passé.
 *
 * LIMITE CONNUE : cette file vit en mémoire. Elle survit à une coupure réseau
 * de quelques minutes, pas à un rechargement de la page. La version persistante
 * (IndexedDB) est la suite — la base et l'API, elles, sont déjà prêtes.
 */
export function fileDActions(jeton, surEtat) {
  const enAttente = [];
  let encours = false;

  const vider = async () => {
    if (encours || enAttente.length === 0) return;
    encours = true;
    while (enAttente.length > 0) {
      const action = enAttente[0];
      try {
        await appel(action.url, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${jeton}`, 'Idempotency-Key': action.cle },
          ...(action.corps ? { body: JSON.stringify(action.corps) } : {}),
        });
        enAttente.shift();
      } catch (erreur) {
        if (erreur.statut >= 400 && erreur.statut < 500) {
          // Refus définitif du serveur : réessayer ne changera rien.
          enAttente.shift();
          surEtat({ erreur: erreur.message, restantes: enAttente.length });
          continue;
        }
        break; // panne réseau ou serveur : on garde la place dans la file
      }
    }
    encours = false;
    surEtat({ restantes: enAttente.length });
  };

  addEventListener('online', vider);

  return {
    pousser(url, corps) {
      enAttente.push({ url, corps, cle: crypto.randomUUID() });
      surEtat({ restantes: enAttente.length });
      return vider();
    },
    restantes: () => enAttente.length,
  };
}

export function minutes(min, max) {
  if (max <= 0) return "c'est votre tour";
  if (min === 0) return `moins de ${max} min`;
  return `${min} à ${max} min`;
}
