// Mises en forme partagées. Aucun import, aucun accès au DOM : ce module se
// charge aussi bien dans un navigateur que dans un runner de tests.

/** L'attente est annoncée en fourchette, jamais comme une promesse. */
export function minutes(min, max) {
  if (max <= 0) return "c'est votre tour";
  if (min === 0) return `moins de ${max} min`;
  return `${min} à ${max} min`;
}
