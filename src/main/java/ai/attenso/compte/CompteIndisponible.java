package ai.attenso.compte;

/**
 * Le compte n'existe plus ou vient d'être désactivé.
 *
 * <p>Distinct d'un mot de passe actuel faux : là, la personne est toujours
 * elle-même et reste sur son tableau ; ici, son accès a été retiré et il n'y a
 * plus rien à faire sur cette page.
 */
public class CompteIndisponible extends RuntimeException {
}
