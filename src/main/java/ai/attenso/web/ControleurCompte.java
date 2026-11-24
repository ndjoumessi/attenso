package ai.attenso.web;

import ai.attenso.compte.ServiceCompte;
import ai.attenso.securite.JetonPersonnel;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.utilisateur.UtilisateurRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Son propre compte : qui l'on est, et changer son mot de passe.
 *
 * <p>Ouvert aux deux rôles, évidemment — un compte d'accueil est un compte
 * comme un autre, et c'est même celui qui en a le plus besoin : c'est lui qui
 * vit sur la tablette du comptoir.
 *
 * <p>Il n'y a pas de route de déconnexion, et c'est délibéré : voir
 * {@code accueil.html}. Le jeton n'est pas une session côté serveur, et la
 * seule chose qu'on puisse révoquer est le compte ENTIER — toutes ses
 * tablettes à la fois. Quitter un poste ne doit pas déconnecter les autres.
 */
@RestController
@RequestMapping("/api/compte")
public class ControleurCompte {

	/**
	 * Cinq essais par quart d'heure.
	 *
	 * <p>Plus serré que la connexion, qui en laisse dix : ici l'appelant tient
	 * déjà une session ouverte. Ce qu'il peut gagner en devinant le mot de
	 * passe actuel, c'est un accès qui SURVIT à l'expiration du jeton — donc
	 * la seule chose que la session ne lui donne pas déjà.
	 */
	private static final int ESSAIS_MAX = 5;

	private static final Duration FENETRE = Duration.ofMinutes(15);

	private final ServiceCompte comptes;

	private final UtilisateurRepository utilisateurs;

	private final JetonPersonnel jetons;

	private final LimiteurDebit limiteur;

	public ControleurCompte(ServiceCompte comptes, UtilisateurRepository utilisateurs,
			JetonPersonnel jetons, LimiteurDebit limiteur) {
		this.comptes = comptes;
		this.utilisateurs = utilisateurs;
		this.jetons = jetons;
		this.limiteur = limiteur;
	}

	@GetMapping
	public VueCompte compte(@AuthenticationPrincipal PrincipalPersonnel principal) {
		return utilisateurs.findById(principal.utilisateurId())
				.map(u -> new VueCompte(u.email(), u.role().name()))
				.orElseThrow(IdentifiantsRefuses::new);
	}

	/**
	 * Change le mot de passe et rend un jeton neuf.
	 *
	 * <p>Le jeton neuf n'est pas une commodité : le changement incrémente la
	 * version du compte, ce qui invalide à l'instant TOUS les jetons émis
	 * avant — y compris celui de l'appelant. Sans réémission, changer son mot
	 * de passe se solderait par une déconnexion immédiate, et ressemblerait à
	 * un échec.
	 */
	@PostMapping("/mot-de-passe")
	public ReponseMotDePasse changerMotDePasse(
			@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandeChangement demande) {
		if (!limiteur.autoriser("mdp:" + principal.utilisateurId(), ESSAIS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		int version = comptes.changerMotDePasse(principal.utilisateurId(),
				demande.actuel(), demande.nouveau());
		return new ReponseMotDePasse(jetons.emettre(principal.utilisateurId(),
				principal.tenantId(), principal.role(), version));
	}

	/**
	 * @param nouveau mêmes 12 caractères qu'à l'inscription et qu'à la
	 *        réinitialisation : aucun des trois chemins ne doit être celui par
	 *        lequel un mot de passe plus faible entre.
	 */
	public record DemandeChangement(
			@NotBlank @Size(max = 200) String actuel,
			@NotBlank @Size(min = 12, max = 200) String nouveau) {
	}

	public record ReponseMotDePasse(String jeton) {
	}
}
