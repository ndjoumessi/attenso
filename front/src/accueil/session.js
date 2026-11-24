// Ce que la tablette retient d'une session, et rien de plus.
export const CLE_JETON = 'attenso.jeton';
export const CLE_CLINIQUE = 'attenso.clinique';
export const CLE_SLUG = 'attenso.slug';
// Survit au rechargement qui suit un changement de mot de passe, et à lui
// seul : sans ça, la confirmation disparaîtrait avec la page qui l'a demandée.
export const CLE_MDP_CHANGE = 'attenso.mdp-change';

export const jetonCourant = () => localStorage.getItem(CLE_JETON);

export const entete = (jeton) => ({ Authorization: `Bearer ${jeton}` });

/** Vide la session. Le journal local est traité à part : il peut contenir
 *  des gestes qui ne sont jamais arrivés au serveur. */
export function oublierSession() {
  localStorage.removeItem(CLE_JETON);
  localStorage.removeItem(CLE_SLUG);
  localStorage.removeItem(CLE_CLINIQUE);
}
