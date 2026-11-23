package ai.attenso.amorce;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Installation de la première clinique.
 *
 * <p>Inactif par défaut. Le mot de passe se passe par variable
 * d'environnement, jamais par un fichier de configuration : c'est le seul
 * identifiant de l'installation, et il ne doit pas finir dans un dépôt.
 */
@ConfigurationProperties("attenso.amorce")
public record ProprietesAmorce(boolean active, String slug, String nom, String email,
		String motDePasse, Integer seuilPrevenir, Integer delaiGraceSecondes) {
}
