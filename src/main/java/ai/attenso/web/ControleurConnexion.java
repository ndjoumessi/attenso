package ai.attenso.web;

import ai.attenso.securite.JetonPersonnel;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.utilisateur.Utilisateur;
import ai.attenso.utilisateur.UtilisateurRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion du personnel.
 *
 * <p>Limite serrée, contrairement à l'enregistrement public : ici une adresse
 * qui répète ses tentatives est une attaque, pas une salle d'attente pleine.
 * Et une seule erreur pour « compte inconnu » et « mot de passe faux », sinon
 * le message d'erreur énumère les comptes.
 */
@RestController
public class ControleurConnexion {

	private static final int TENTATIVES_MAX = 10;

	private static final Duration FENETRE = Duration.ofMinutes(10);

	private final ServiceTenant cliniques;

	private final UtilisateurRepository utilisateurs;

	private final PasswordEncoder motsDePasse;

	private final JetonPersonnel jetons;

	private final LimiteurDebit limiteur;

	private final AdresseAppelant adresses;

	public ControleurConnexion(ServiceTenant cliniques, UtilisateurRepository utilisateurs,
			PasswordEncoder motsDePasse, JetonPersonnel jetons, LimiteurDebit limiteur,
			AdresseAppelant adresses) {
		this.cliniques = cliniques;
		this.utilisateurs = utilisateurs;
		this.motsDePasse = motsDePasse;
		this.jetons = jetons;
		this.limiteur = limiteur;
		this.adresses = adresses;
	}

	@PostMapping("/api/connexion")
	public ReponseConnexion connecter(@Valid @RequestBody DemandeConnexion demande,
			HttpServletRequest requete) {
		String cle = "connexion:" + adresses.de(requete) + ":" + demande.email();
		if (!limiteur.autoriser(cle, TENTATIVES_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		Tenant clinique = cliniques.parSlug(demande.slug());
		Optional<Utilisateur> trouve = ContexteTenant.avec(clinique.id(),
				() -> utilisateurs.findByTenantIdAndEmailAndActifTrue(clinique.id(),
						demande.email().trim().toLowerCase()));
		Utilisateur utilisateur = trouve
				.filter(u -> motsDePasse.matches(demande.motDePasse(), u.mdpHash()))
				.orElseThrow(IdentifiantsRefuses::new);
		return new ReponseConnexion(
				jetons.emettre(utilisateur.id(), clinique.id(), utilisateur.role()),
				utilisateur.role().name(), clinique.nom(), clinique.slug());
	}
}
