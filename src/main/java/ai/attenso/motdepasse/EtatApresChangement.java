package ai.attenso.motdepasse;

import ai.attenso.utilisateur.Role;

/**
 * Ce que la ligne du compte dit APRÈS que le mot de passe y a été posé.
 *
 * <p>Le rôle est là pour une raison précise : le jeton réémis doit être bâti
 * sur ce que la base dit à cet instant, pas sur ce que portait le jeton
 * d'avant. Entre le contrôle du filtre et l'écriture, un gérant peut avoir
 * rétrogradé ou désactivé ce compte ; sans ce rôle-ci, le changement de mot de
 * passe rendrait un jeton neuf portant l'ancien privilège, et le rendrait
 * renouvelable indéfiniment.
 */
public record EtatApresChangement(int version, Role role) {
}
