package ai.attenso.web;

import ai.attenso.motdepasse.ServiceReinitialisation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Récupération de mot de passe.
 *
 * <p><strong>La demande répond toujours la même chose.</strong> Un message
 * différent selon que le compte existe ou non transformerait ce formulaire en
 * annuaire : on essaie des adresses, et l'on apprend lesquelles sont clientes.
 * D'où un 204 systématique, y compris pour une clinique inconnue.
 */
@RestController
public class ControleurMotDePasse {

	/**
	 * Plus serré que la connexion : une demande d'oubli envoie un COURRIEL. Un
	 * script qui en déclenche mille transforme le service en machine à harceler
	 * une boîte aux lettres, aux frais de notre réputation d'expéditeur.
	 */
	private static final int DEMANDES_MAX = 3;

	private static final Duration FENETRE = Duration.ofMinutes(15);

	private final ServiceReinitialisation reinitialisations;

	private final LimiteurDebit limiteur;

	private final AdresseAppelant adresses;

	public ControleurMotDePasse(ServiceReinitialisation reinitialisations,
			LimiteurDebit limiteur, AdresseAppelant adresses) {
		this.reinitialisations = reinitialisations;
		this.limiteur = limiteur;
		this.adresses = adresses;
	}

	@PostMapping("/api/connexion/oubli")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void oubli(@Valid @RequestBody DemandeOubli demande,
			HttpServletRequest requete) {
		// La limite s'applique à l'adresse appelante ET au couple visé : sans
		// le second, un script changeant d'adresse pilonnerait la même boîte.
		if (!limiteur.autoriser("oubli:" + adresses.de(requete), DEMANDES_MAX, FENETRE)
				|| !limiteur.autoriser("oubli:" + demande.slug() + ":" + demande.email(),
						DEMANDES_MAX, FENETRE)) {
			// Même en cas de refus : un 429 ici dirait « cette adresse existe
			// et on lui a déjà écrit ». Le refus est silencieux.
			return;
		}
		reinitialisations.demander(demande.slug().trim(), demande.email().trim());
	}

	@PostMapping("/api/connexion/reinitialiser")
	public ReponseReinitialisation reinitialiser(
			@Valid @RequestBody DemandeReinitialisation demande) {
		// Ici, en revanche, dire que le lien est périmé est utile et ne révèle
		// rien : il faut déjà détenir un jeton pour poser la question.
		return new ReponseReinitialisation(
				reinitialisations.reinitialiser(demande.jeton(), demande.motDePasse()));
	}

	public record DemandeOubli(
			@NotBlank @Size(max = 120) String slug,
			@NotBlank @Email @Size(max = 200) String email) {
	}

	/**
	 * @param motDePasse mêmes 12 caractères qu'à l'inscription : une
	 *        réinitialisation ne doit pas être la porte dérobée qui laisse
	 *        poser un mot de passe plus faible que celui d'origine.
	 */
	public record DemandeReinitialisation(
			@NotBlank @Size(max = 200) String jeton,
			@NotBlank @Size(min = 12, max = 200) String motDePasse) {
	}

	public record ReponseReinitialisation(boolean fait) {
	}
}
