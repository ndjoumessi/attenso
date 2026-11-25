package ai.attenso.web;

import ai.attenso.inscription.DemandeInscription;
import ai.attenso.inscription.ReponseInscription;
import ai.attenso.inscription.ServiceInscription;
import ai.attenso.facturation.Palier;
import ai.attenso.paiement.PasserellePaiement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription autonome (EF-901).
 *
 * <p><strong>Point d'entrée anonyme qui crée des comptes</strong>, donc borné
 * serré : trois inscriptions par adresse et par heure. Une vraie clinique s'en
 * inscrit une ; au-delà, c'est du squat de slug ou du remplissage de base. La
 * limite est bien plus stricte que celle de l'enregistrement d'un ticket,
 * parce que les coûts d'erreur sont inverses : refuser un ticket perd un
 * client présent, refuser une 4e inscription dans l'heure ne perd personne.
 */
@RestController
public class ControleurInscription {

	private static final int INSCRIPTIONS_MAX = 3;

	private static final Duration FENETRE = Duration.ofHours(1);

	private final ServiceInscription inscriptions;

	private final LimiteurDebit limiteur;

	private final AdresseAppelant adresses;

	private final PasserellePaiement paiement;

	public ControleurInscription(ServiceInscription inscriptions, LimiteurDebit limiteur,
			AdresseAppelant adresses, PasserellePaiement paiement) {
		this.inscriptions = inscriptions;
		this.limiteur = limiteur;
		this.adresses = adresses;
		this.paiement = paiement;
	}

	/**
	 * Les paliers et leurs prix, et SI l'on peut souscrire.
	 *
	 * <p>Le drapeau d'ouverture voyage avec les paliers plutôt que dans un
	 * appel à part : la page les demande déjà, et une seconde requête sur une
	 * page tenue à une seconde au 95e centile se paierait pour rien.
	 */
	@GetMapping("/api/inscription/paliers")
	public VueOuvertureInscription paliers() {
		return new VueOuvertureInscription(paiement.disponible(),
				Arrays.stream(Palier.values())
						.map(p -> new VuePalier(p.name(), p.visitesIncluses(),
								p.prixCentimes(), Palier.DEPASSEMENT_CENTIMES))
						.toList());
	}

	@PostMapping("/api/inscription")
	@ResponseStatus(HttpStatus.CREATED)
	public ReponseInscription inscrire(@Valid @RequestBody DemandeInscription demande,
			HttpServletRequest requete) {
		if (!limiteur.autoriser("inscription:" + adresses.de(requete), INSCRIPTIONS_MAX,
				FENETRE)) {
			throw new TropDeRequetes();
		}
		return inscriptions.inscrire(demande);
	}
}
