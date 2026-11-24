// L'identité de l'éditeur, posée dans les pages légales.
//
// Elle vient du serveur et non du HTML : ce dépôt est PUBLIC, et une raison
// sociale, un siège et un nom de directeur de publication n'ont rien à faire
// dans un historique Git que personne ne peut effacer. Voir ProprietesEditeur.

/** Ce qui manque est dit, pas masqué : un blanc dans des mentions légales
 *  ressemble à une page en règle, et ne l'est pas. */
const MANQUE = 'à compléter';

const CHAMPS = {
  denomination: 'denomination',
  'forme-juridique': 'formeJuridique',
  capital: 'capital',
  siege: 'siege',
  siret: 'siret',
  rcs: 'rcs',
  tva: 'tva',
  directeur: 'directeurPublication',
  email: 'email',
  telephone: 'telephone',
  hebergeur: 'hebergeur',
  'hebergeur-adresse': 'hebergeurAdresse',
};

export async function remplirEditeur() {
  let editeur = {};
  try {
    const reponse = await fetch('/api/editeur');
    if (reponse.ok) editeur = await reponse.json();
  } catch {
    // Hors réseau, la page reste lisible : seuls les champs d'identité
    // manquent, et le reste du texte est ce qui engage.
  }
  for (const [id, champ] of Object.entries(CHAMPS)) {
    const cible = document.getElementById(id);
    if (!cible) continue;
    const valeur = editeur[champ];
    if (valeur && valeur.trim()) {
      cible.textContent = valeur;
      continue;
    }
    cible.textContent = MANQUE;
    cible.className = 'discret';
  }
  // L'adresse de contact sert aussi de lien partout où elle apparaît.
  const email = editeur.email;
  for (const lien of document.querySelectorAll('[data-courriel]')) {
    lien.textContent = email || MANQUE;
    if (email) lien.href = `mailto:${email}`;
  }
}
