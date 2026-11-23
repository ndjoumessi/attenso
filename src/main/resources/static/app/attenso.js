// Attenso — fonctions communes aux quatre écrans. Aucune dépendance : la page
// client s'ouvre sur un téléphone mal capté, dans une rue, et doit s'afficher
// avant que le réseau ait fini de réfléchir.

// Import RELATIF : le navigateur le résout depuis l'URL du module (/app/),
// et un runner de tests depuis le disque. Le même fichier sert aux deux.
import { acquitter, empiler, enAttente, purgerAutresJours } from './journal.js';

/**
 * Appel JSON.
 *
 * <p>Distingue deux échecs que tout le reste traite différemment : une panne de
 * liaison ({@code reseau: true}, il faut réessayer) et un refus du serveur
 * ({@code statut}, réessayer ne changera rien).
 */
export async function appel(url, options = {}) {
  let reponse;
  try {
    reponse = await fetch(url, {
      ...options,
      headers: {
        'Accept': 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {}),
      },
    });
  } catch (panne) {
    const erreur = new Error('serveur injoignable');
    erreur.reseau = true;
    throw erreur;
  }
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
 * État de la liaison, déduit des appels réels.
 *
 * <p><strong>Et non de `navigator.onLine`.</strong> Celui-ci dit seulement que
 * l'appareil a une interface réseau active : dans une clinique dont le routeur
 * wifi fonctionne mais dont la fibre est tombée, il reste à `true`. Le bandeau
 * « hors ligne » ne se serait donc jamais affiché dans le cas de panne le plus
 * courant. Seul le succès ou l'échec d'un appel dit la vérité.
 */
export function etatLiaison(surChangement) {
  let enPanne = false;
  const poser = (valeur) => {
    if (enPanne !== valeur) {
      enPanne = valeur;
      surChangement(enPanne);
    }
  };
  return {
    succes: () => poser(false),
    echec: () => poser(true),
    enPanne: () => enPanne,
  };
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

const REPRISE_MIN = 3000;
const REPRISE_MAX = 60000;

/**
 * File persistante des actions de l'accueil.
 *
 * <p>Chaque action est écrite dans IndexedDB <em>avant</em> d'être tentée, avec
 * un UUID généré ici. Le serveur enregistre cet UUID en clé primaire : rejouer
 * n'applique donc rien deux fois, et on peut réessayer sans se demander si le
 * premier envoi est passé. C'est ce qui rend la panne réseau survivable — y
 * compris à un rechargement de la page, ce qu'une file en mémoire ne permettait
 * pas.
 *
 * <p>Les refus définitifs du serveur sont triés. Un 409 ou un 404 à la reprise
 * veut dire « l'état a déjà bougé » — l'ASV a tranché autrement, ou depuis un
 * autre poste : la commande est abandonnée <em>en silence</em>, parce que
 * afficher une erreur par geste périmé après vingt minutes de panne noierait le
 * seul message utile. Les autres 4xx sont montrés.
 */
export function fileDActions({ jeton, jour, liaison, surEtat }) {
  let encours = false;
  let minuterie = null;
  let delai = REPRISE_MIN;
  let abandons = 0;

  const entete = (commande) => ({
    'Authorization': `Bearer ${jeton}`,
    'Idempotency-Key': commande.cle,
  });

  const programmerReprise = () => {
    if (minuterie) return;
    minuterie = setTimeout(() => {
      minuterie = null;
      vider();
    }, delai);
    delai = Math.min(delai * 2, REPRISE_MAX);
  };

  async function vider() {
    if (encours) return;
    encours = true;
    let interrompu = false;
    try {
      const commandes = (await enAttente()).sort((a, b) => a.sequence - b.sequence);
      for (const commande of commandes) {
        try {
          await appel(commande.url, {
            method: 'POST',
            headers: entete(commande),
            ...(commande.corps ? { body: JSON.stringify(commande.corps) } : {}),
          });
          await acquitter(commande.sequence);
          liaison.succes();
          delai = REPRISE_MIN;
        } catch (echec) {
          if (echec.reseau || echec.statut >= 500) {
            liaison.echec();
            interrompu = true;
            break; // on garde la place dans la file, et son ordre
          }
          await acquitter(commande.sequence);
          if (echec.statut === 409 || echec.statut === 404) {
            abandons += 1;
          } else {
            surEtat({ erreur: echec.message });
          }
        }
      }
    } catch (panne) {
      // Journal illisible : on ne peut rien garantir, on le dit.
      surEtat({ erreur: `journal local indisponible : ${panne.message}` });
    } finally {
      encours = false;
    }
    if (interrompu) programmerReprise();
    await publier();
  }

  async function publier() {
    const restantes = await enAttente().catch(() => []);
    surEtat({ restantes: restantes.length, abandons, commandes: restantes });
    abandons = 0;
  }

  addEventListener('online', () => { delai = REPRISE_MIN; vider(); });

  return {
    /** Écrit l'action puis tente de l'envoyer. `cible` sert à l'affichage optimiste. */
    async pousser(url, { corps = null, ticketId = null, cible = null } = {}) {
      await empiler({
        cle: crypto.randomUUID(), url, corps, ticketId, cible, jour,
        creeLe: Date.now(),
      });
      await publier();
      return vider();
    },

    /** Au démarrage : jette les gestes d'un autre jour, puis reprend le reste. */
    async reprendre() {
      const perimees = await purgerAutresJours(jour);
      await publier();
      await vider();
      return perimees;
    },

    enAttente,
  };
}
