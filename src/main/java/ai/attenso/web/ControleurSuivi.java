package ai.attenso.web;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Fourchette;
import ai.attenso.ticket.ResolveurReference;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import ai.attenso.ticket.TicketInconnu;
import ai.attenso.ticket.TicketRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * La page « où en est mon tour ».
 *
 * <p>La référence du lien tient lieu de justificatif. Elle ne donne accès qu'à
 * une position dans une file, elle est cloisonnée à une clinique, elle meurt
 * avec la journée, et elle est courte — ce qu'un jeton signé n'est pas, et
 * c'est la contrainte qui tranche : le lien doit tenir dans un SMS d'un seul
 * segment.
 */
@RestController
public class ControleurSuivi {

	private static final int CONSULTATIONS_MAX = 60;

	private static final Duration FENETRE = Duration.ofMinutes(1);

	private final ResolveurReference resolveur;

	private final TicketRepository tickets;

	private final ServiceTenant cliniques;

	private final ServiceFile file;

	private final ServiceEstimation estimation;

	private final DiffusionFile diffusion;

	private final LimiteurDebit limiteur;

	public ControleurSuivi(ResolveurReference resolveur, TicketRepository tickets,
			ServiceTenant cliniques, ServiceFile file, ServiceEstimation estimation,
			DiffusionFile diffusion, LimiteurDebit limiteur) {
		this.resolveur = resolveur;
		this.tickets = tickets;
		this.cliniques = cliniques;
		this.file = file;
		this.estimation = estimation;
		this.diffusion = diffusion;
		this.limiteur = limiteur;
	}

	@GetMapping("/api/suivi/{reference}")
	public EtatSuivi etat(@PathVariable String reference) {
		if (!limiteur.autoriser("suivi:" + reference, CONSULTATIONS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		UUID tenantId = resolveur.tenantDe(reference)
				.orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		return ContexteTenant.avec(tenantId, () -> {
			Ticket ticket = parReference(reference);
			int position = file.position(ticket);
			Fourchette attente = estimation.fourchette(tenantId, position);
			return new EtatSuivi(clinique.nom(), ticket.numeroJour(), position,
					ticket.statut().name(), attente.minMinutes(), attente.maxMinutes(),
					clinique.seuilPrevenir());
		});
	}

	@GetMapping(value = "/api/suivi/{reference}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter flux(@PathVariable String reference) {
		UUID tenantId = resolveur.tenantDe(reference)
				.orElseThrow(TicketInconnu::parReference);
		return ContexteTenant.avec(tenantId,
				() -> diffusion.abonner(tenantId, parReference(reference).jour()));
	}

	private Ticket parReference(String reference) {
		return tickets.findByReference(reference).orElseThrow(TicketInconnu::parReference);
	}
}
