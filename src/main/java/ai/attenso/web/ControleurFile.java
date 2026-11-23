package ai.attenso.web;

import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.LigneFile;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le tableau de l'accueil.
 *
 * <p>Toutes les transitions acceptent un en-tête {@code Idempotency-Key} :
 * c'est ce qui permet à la tablette d'empiler ses actions hors ligne et de les
 * rejouer à la reconnexion sans rien appliquer deux fois.
 *
 * <p>Aucune de ces actions n'envoie de SMS — l'appel « c'est à vous » passe par
 * l'écran et la voix. C'est ce qui rend le rejeu sûr : rejouer vingt minutes
 * plus tard n'expédie pas un message devenu absurde.
 */
@RestController
@RequestMapping("/api")
public class ControleurFile {

	private final ServiceTenant cliniques;

	private final ServiceFile file;

	private final ServiceCreationTicket creation;

	private final ServiceEstimation estimation;

	public ControleurFile(ServiceTenant cliniques, ServiceFile file,
			ServiceCreationTicket creation, ServiceEstimation estimation) {
		this.cliniques = cliniques;
		this.file = file;
		this.creation = creation;
		this.estimation = estimation;
	}

	@GetMapping("/file")
	public VueFile file(@AuthenticationPrincipal PrincipalPersonnel principal) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		LocalDate jour = file.jourCourant(clinique);
		List<LigneFile> lignes = file.tableauDuJour(clinique.id(), jour);
		long enFile = lignes.stream().filter(ligne -> ligne.statut().ouvert()).count();
		return new VueFile(jour, lignes, enFile, file.departsDuJour(clinique.id(), jour),
				estimation.dureeMedianeMinutes(clinique.id()));
	}

	@PostMapping("/tickets/{id}/appeler")
	public ReponseTransition appeler(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) UUID commande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(file.appeler(clinique, id, principal.utilisateurId(), commande));
	}

	@PostMapping("/tickets/{id}/present")
	public ReponseTransition present(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) UUID commande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(file.present(clinique, id, principal.utilisateurId(), commande));
	}

	@PostMapping("/tickets/{id}/termine")
	public ReponseTransition termine(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) UUID commande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(file.termine(clinique, id, principal.utilisateurId(), commande));
	}

	/**
	 * Parti sans être vu. Jamais le même bouton que « absent » : c'est le
	 * chiffre qu'on présentera au gérant pour justifier l'abonnement.
	 */
	@PostMapping("/tickets/{id}/parti")
	public ReponseTransition parti(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) UUID commande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(file.parti(clinique, id, principal.utilisateurId(), commande));
	}

	@PostMapping("/tickets/{id}/annule")
	public ReponseTransition annule(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id,
			@RequestHeader(value = "Idempotency-Key", required = false) UUID commande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(file.annule(clinique, id, principal.utilisateurId(), commande));
	}

	@PostMapping("/tickets/urgence")
	public ReponseTransition urgence(@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandeUrgence demande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		Ticket ticket = creation.creerUrgence(clinique, file.jourCourant(clinique),
				demande.telephone(), principal.utilisateurId());
		return vue(ticket);
	}

	private ReponseTransition vue(Ticket ticket) {
		int position = ticket.statut().ouvert() ? file.position(ticket) : 0;
		return new ReponseTransition(ticket.id(), ticket.numeroJour(),
				ticket.statut().name(), position);
	}
}
