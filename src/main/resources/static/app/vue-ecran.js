// Ce que l'écran du mur annonce. Aucun nom, aucun téléphone : uniquement des
// numéros, puisque c'est affiché sur un mur public.

export const RIEN = '—';

/**
 * Le grand nombre. Plusieurs personnes peuvent être appelées à la fois —
 * deux salles de consultation, c'est le cas courant.
 */
export function appel(vue) {
  return vue.appeles.length > 0 ? vue.appeles.join(' · ') : RIEN;
}

/**
 * La légende sous le nombre.
 *
 * <p>« Consultation en cours » plutôt que « personne en salle » quand la file
 * n'est pas vide : un écran qui annonce le vide alors que des gens attendent
 * leur dit que l'outil ne les voit pas.
 */
export function legende(vue) {
  if (vue.appeles.length > 1) return 'sont appelés';
  if (vue.appeles.length === 1) return 'est appelé';
  return vue.prochains.length > 0 ? 'consultation en cours' : 'personne en salle';
}

export function prochains(vue) {
  return vue.prochains.length > 0 ? `ensuite : ${vue.prochains.join(' · ')}` : '';
}
