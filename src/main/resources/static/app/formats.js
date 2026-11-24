// Mises en forme partagées. Aucun import, aucun accès au DOM : ce module se
// charge aussi bien dans un navigateur que dans un runner de tests.

/** L'attente est annoncée en fourchette, jamais comme une promesse. */
export function minutes(min, max) {
  if (max <= 0) return "c'est votre tour";
  if (min === 0) return `moins de ${max} min`;
  return `${min} à ${max} min`;
}

/**
 * Depuis combien de temps cette personne attend (EF-402).
 *
 * <p>Arrondi à la minute, et jamais aux secondes : une durée qui défile
 * seconde par seconde sur un tableau transforme une salle d'attente en
 * chronomètre, pour l'ASV comme pour quiconque regarde par-dessus son épaule.
 * Au-delà d'une heure on passe en heures, parce qu'« 83 min » ne se lit pas.
 */
export function duree(depuis, maintenant = Date.now()) {
  const minutes = Math.max(0, Math.floor((maintenant - new Date(depuis)) / 60000));
  if (minutes < 1) return "à l'instant";
  if (minutes < 60) return `${minutes} min`;
  const heures = Math.floor(minutes / 60);
  const reste = minutes % 60;
  return reste === 0 ? `${heures} h` : `${heures} h ${String(reste).padStart(2, '0')}`;
}
