// Journal persistant de la tablette de l'accueil.
//
// Deux magasins, deux rôles distincts :
//   - « commandes » : les actions faites par l'ASV et pas encore acquittées par
//     le serveur. Clé auto-incrémentée, donc l'ordre de la clé EST l'ordre des
//     gestes — y compris après un rechargement de page, ce qui est tout
//     l'intérêt par rapport à une file en mémoire.
//   - « etat » : le dernier tableau reçu du serveur, pour que la page s'affiche
//     avant même d'avoir réussi un appel réseau.
//
// Pourquoi IndexedDB et pas localStorage : localStorage est synchrone (il gèle
// l'interface) et ne garantit pas l'atomicité d'une écriture multiple. Ici, une
// commande à moitié écrite serait une action perdue ou appliquée deux fois.

const BASE = 'attenso';
const VERSION = 1;
const COMMANDES = 'commandes';
const ETAT = 'etat';

let connexion = null;

function ouvrir() {
  if (connexion) return connexion;
  connexion = new Promise((resoudre, rejeter) => {
    const demande = indexedDB.open(BASE, VERSION);
    demande.onupgradeneeded = () => {
      const base = demande.result;
      if (!base.objectStoreNames.contains(COMMANDES)) {
        // autoIncrement : la clé primaire donne l'ordre d'arrivée, et « cle »
        // (l'UUID envoyé au serveur en Idempotency-Key) reste unique.
        const magasin = base.createObjectStore(COMMANDES, {
          keyPath: 'sequence', autoIncrement: true,
        });
        magasin.createIndex('cle', 'cle', { unique: true });
      }
      if (!base.objectStoreNames.contains(ETAT)) {
        base.createObjectStore(ETAT, { keyPath: 'nom' });
      }
    };
    demande.onsuccess = () => resoudre(demande.result);
    demande.onerror = () => rejeter(demande.error);
  });
  return connexion;
}

function transaction(magasin, mode, action) {
  return ouvrir().then((base) => new Promise((resoudre, rejeter) => {
    const tx = base.transaction(magasin, mode);
    const resultat = action(tx.objectStore(magasin));
    tx.oncomplete = () => resoudre(resultat && resultat.result !== undefined
      ? resultat.result : resultat);
    tx.onerror = () => rejeter(tx.error);
    tx.onabort = () => rejeter(tx.error);
  }));
}

/**
 * Le journal est-il utilisable ? En navigation privée, ou si le stockage est
 * refusé, IndexedDB échoue à l'ouverture. On veut le savoir pour le DIRE à
 * l'ASV plutôt que de lui laisser croire qu'elle est protégée.
 */
export async function disponible() {
  try {
    await ouvrir();
    return true;
  } catch {
    return false;
  }
}

export function empiler(commande) {
  return transaction(COMMANDES, 'readwrite', (magasin) => magasin.add(commande));
}

export async function enAttente() {
  const base = await ouvrir();
  return new Promise((resoudre, rejeter) => {
    const tx = base.transaction(COMMANDES, 'readonly');
    const demande = tx.objectStore(COMMANDES).getAll();
    demande.onsuccess = () => resoudre(demande.result);
    demande.onerror = () => rejeter(demande.error);
  });
}

export function acquitter(sequence) {
  return transaction(COMMANDES, 'readwrite', (magasin) => magasin.delete(sequence));
}

/**
 * Jette les commandes d'un autre jour que celui-ci.
 *
 * La file est une journée : rejouer le lendemain le « terminé » de la veille
 * viserait un ticket clos ou purgé. Le serveur refuserait, mais autant ne pas
 * lui envoyer une rafale de requêtes inutiles au premier café — et autant
 * pouvoir dire à l'ASV combien de gestes ont été abandonnés.
 */
export async function purgerAutresJours(jour) {
  const toutes = await enAttente();
  const perimees = toutes.filter((commande) => commande.jour !== jour);
  for (const commande of perimees) {
    await acquitter(commande.sequence);
  }
  return perimees.length;
}

export function poserEtat(nom, valeur) {
  return transaction(ETAT, 'readwrite',
    (magasin) => magasin.put({ nom, valeur, enregistreLe: Date.now() }));
}

export async function lireEtat(nom) {
  const base = await ouvrir();
  return new Promise((resoudre, rejeter) => {
    const tx = base.transaction(ETAT, 'readonly');
    const demande = tx.objectStore(ETAT).get(nom);
    demande.onsuccess = () => resoudre(demande.result ?? null);
    demande.onerror = () => rejeter(demande.error);
  });
}

/** Au changement de compte : on ne garde pas le tableau d'une autre clinique. */
export async function vider() {
  await transaction(COMMANDES, 'readwrite', (magasin) => magasin.clear());
  await transaction(ETAT, 'readwrite', (magasin) => magasin.clear());
}
