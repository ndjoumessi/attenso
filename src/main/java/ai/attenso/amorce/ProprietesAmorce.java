package ai.attenso.amorce;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Installation de la première clinique.
 *
 * <p>Inactif par défaut. Le mot de passe se passe par variable
 * d'environnement, jamais par un fichier de configuration : c'est le seul
 * identifiant de l'installation, et il ne doit pas finir dans un dépôt.
 *
 * @param expediteurSms nom affiché par les SMS de cette clinique, 3 à 11
 *        caractères alphanumériques. Laissé vide tant que les opérateurs ne
 *        l'ont pas validé : la clinique fonctionne alors sans SMS.
 * @param avecEcran     {@code true} seulement si la clinique possède déjà un
 *        écran en salle d'attente (EF-603). Un jeton d'appairage est alors
 *        tiré et l'URL du mur journalisée. Sans lui, aucune route d'écran ne
 *        répond pour cette clinique.
 */
@ConfigurationProperties("attenso.amorce")
public record ProprietesAmorce(boolean active, String slug, String nom, String email,
		String motDePasse, Integer seuilPrevenir, Integer delaiGraceSecondes,
		String expediteurSms, boolean avecEcran) {
}
