package ai.attenso.web;

import ai.attenso.notification.FabriqueLien;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Fourchette;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-enregistrement depuis le QR du comptoir.
 *
 * <p><strong>La limite de débit est volontairement large.</strong> Les clients
 * scannent le QR depuis la salle d'attente, souvent sur le wifi de la clinique :
 * ils partagent alors une seule adresse publique. Une limite serrée par IP
 * bloquerait les vrais clients un jour de forte affluence. La protection utile
 * est ailleurs — deux tickets par numéro et par jour, tenue en base. Celle-ci
 * ne sert qu'à arrêter un script.
 */
@RestController
public class ControleurPublic {

	private static final int ENREGISTREMENTS_MAX = 30;

	private static final Duration FENETRE = Duration.ofMinutes(10);

	private final ServiceTenant cliniques;

	private final ServiceCreationTicket creation;

	private final ServiceFile file;

	private final ServiceEstimation estimation;

	private final FabriqueLien liens;

	private final LimiteurDebit limiteur;

	private final AdresseAppelant adresses;

	public ControleurPublic(ServiceTenant cliniques, ServiceCreationTicket creation,
			ServiceFile file, ServiceEstimation estimation, FabriqueLien liens,
			LimiteurDebit limiteur, AdresseAppelant adresses) {
		this.cliniques = cliniques;
		this.creation = creation;
		this.file = file;
		this.estimation = estimation;
		this.liens = liens;
		this.limiteur = limiteur;
		this.adresses = adresses;
	}

	@PostMapping("/t/{slug}/tickets")
	@ResponseStatus(HttpStatus.CREATED)
	public ReponseTicket enregistrer(@PathVariable String slug,
			@Valid @RequestBody DemandeTicket demande, HttpServletRequest requete) {
		if (!limiteur.autoriser("enregistrement:" + adresses.de(requete), ENREGISTREMENTS_MAX,
				FENETRE)) {
			throw new TropDeRequetes();
		}
		Tenant clinique = cliniques.parSlug(slug);
		return ContexteTenant.avec(clinique.id(), () -> {
			Ticket ticket = creation.creerDepuisQr(clinique, file.jourCourant(clinique),
					demande.telephone());
			int position = file.position(ticket);
			Fourchette attente = estimation.fourchette(clinique.id(), position);
			return new ReponseTicket(ticket.numeroJour(), ticket.reference(),
					liens.pour(ticket), position, attente.minMinutes(), attente.maxMinutes());
		});
	}
}
