package ai.attenso.inscription;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ce qu'un établissement fournit pour s'inscrire seul (EF-901).
 *
 * <p>Le minimum strict : un nom, un courriel, un mot de passe, un palier. Pas
 * d'adresse, pas de SIRET, pas de numéro de téléphone — chaque champ demandé
 * fait abandonner une partie des inscrits, et aucun de ceux-là n'est
 * nécessaire pour faire tourner une file d'attente.
 *
 * @param motDePasse 12 caractères au minimum. C'est le seul identifiant de
 *        l'établissement, et il n'y a pas de procédure de récupération
 *        automatique : un mot de passe faible ici se paie cher.
 * @param cgvAcceptees obligatoire, et horodaté en base. Vendre à distance sans
 *        acceptation explicite des conditions rend le contrat contestable ; la
 *        case cochée ne vaut que si l'on peut dire QUAND elle l'a été.
 */
public record DemandeInscription(
		@NotBlank @Size(min = 2, max = 120) String nom,
		@NotBlank @Email @Size(max = 200) String email,
		@NotBlank @Size(min = 12, max = 200) String motDePasse,
		@NotBlank @Size(max = 20) String palier,
		boolean avecEcran,
		@AssertTrue(message = "les conditions générales doivent être acceptées")
		boolean cgvAcceptees) {
}
