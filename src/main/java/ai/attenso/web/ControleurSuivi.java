package ai.attenso.web;

import ai.attenso.journal.JournalDao;
import ai.attenso.push.AbonnementPush;
import ai.attenso.push.ProprietesPush;
import ai.attenso.push.PushDao;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.EvenementFileModifiee;
import ai.attenso.ticket.Fourchette;
import ai.attenso.ticket.ResolveurReference;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import ai.attenso.ticket.TicketInconnu;
import ai.attenso.ticket.TicketRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * La page « où en est mon tour ».
 *
 * <p>La référence du lien tient lieu de justificatif. Elle ne donne accès qu'à
 * une position dans une file, elle est cloisonnée à une clinique, et elle est
 * courte — ce qu'un jeton signé n'est pas, et c'est la contrainte qui tranche :
 * le lien doit tenir dans un SMS d'un seul segment.
 *
 * <p><strong>Elle ne vaut que pour la journée de son ticket</strong>, et c'est
 * vérifié ici plutôt que supposé. La ligne {@code ticket} survit à la journée —
 * c'est elle qui porte le compteur de départs — donc rien n'expire tout seul :
 * sans ce contrôle, le lien reçu par SMS resterait ouvert indéfiniment.
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

	private final JournalDao journal;

	private final Clock horloge;

	private final ApplicationEventPublisher evenements;

	private final Optional<PushDao> push;

	private final ProprietesPush proprietesPush;

	public ControleurSuivi(ResolveurReference resolveur, TicketRepository tickets,
			ServiceTenant cliniques, ServiceFile file, ServiceEstimation estimation,
			DiffusionFile diffusion, LimiteurDebit limiteur, JournalDao journal,
			Clock horloge, ApplicationEventPublisher evenements, Optional<PushDao> push,
			ProprietesPush proprietesPush) {
		this.resolveur = resolveur;
		this.tickets = tickets;
		this.cliniques = cliniques;
		this.file = file;
		this.estimation = estimation;
		this.diffusion = diffusion;
		this.limiteur = limiteur;
		this.journal = journal;
		this.horloge = horloge;
		this.evenements = evenements;
		this.push = push;
		this.proprietesPush = proprietesPush;
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
			Ticket ticket = duJour(reference, clinique);
			int position = file.position(ticket);
			Fourchette attente = estimation.fourchette(tenantId, position);
			return new EtatSuivi(clinique.nom(), ticket.numeroJour(), position,
					ticket.statut().name(), attente.minMinutes(), attente.maxMinutes(),
					clinique.seuilPrevenir());
		});
	}

	/**
	 * EF-205 — le client signale qu'il arrive.
	 *
	 * <p><strong>Ne change pas l'ordre de la file.</strong> Le cahier des
	 * charges dit « signaler », pas « passer devant » : un bouton qui
	 * réordonnerait donnerait à chacun le moyen de doubler les autres depuis
	 * son téléphone, et l'ASV perdrait le contrôle de sa salle. L'information
	 * s'affiche sur la ligne ; la décision reste à l'accueil.
	 *
	 * <p>Aucun SMS ne part : ce serait un message payant pour une information
	 * que le tableau porte déjà, et EF-304 interdit d'ailleurs qu'une action
	 * de l'accueil en déclenche — ici c'est le client, mais la logique
	 * budgétaire est la même.
	 *
	 * <p>La limite de débit est celle de la consultation : la référence vaut
	 * justificatif, et marteler le bouton ne fait que réécrire un horodatage.
	 */
	@PostMapping("/api/suivi/{reference}/jarrive")
	public EtatSuivi jarrive(@PathVariable String reference) {
		if (!limiteur.autoriser("suivi:" + reference, CONSULTATIONS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		UUID tenantId = resolveur.tenantDe(reference).orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		ContexteTenant.executer(tenantId, () -> {
			Ticket ticket = duJour(reference, clinique);
			ticket.signalerProche(horloge.instant());
			tickets.save(ticket);
			journal.consigner(tenantId, null, ticket.id(), "SIGNALE_PROCHE", null);
			// Le tableau de l'accueil l'apprend par le flux, sans attendre son
			// rafraîchissement de sécurité : quelqu'un qui dit « j'arrive »
			// veut être vu maintenant, pas dans trente secondes.
			evenements.publishEvent(new EvenementFileModifiee(tenantId, ticket.jour()));
		});
		return etat(reference);
	}

	/**
	 * EF-307 — le client autorise les notifications poussées.
	 *
	 * <p>Rend {@code 404} quand le push n'est pas configuré sur ce service :
	 * la page n'a alors rien à proposer, et dire « non configuré » à un
	 * visiteur anonyme ne lui apprendrait rien d'utile.
	 */
	@PostMapping("/api/suivi/{reference}/push")
	public void abonnerAuPush(@PathVariable String reference,
			@Valid @RequestBody DemandePush demande) {
		if (push.isEmpty()) {
			throw TicketInconnu.parReference();
		}
		if (!limiteur.autoriser("suivi:" + reference, CONSULTATIONS_MAX, FENETRE)) {
			throw new TropDeRequetes();
		}
		UUID tenantId = resolveur.tenantDe(reference).orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		ContexteTenant.executer(tenantId, () -> push.get().poser(tenantId,
				duJour(reference, clinique).id(),
				new AbonnementPush(demande.endpoint(), demande.clePublique(),
						demande.cleAuth())));
	}

	/** La clé publique VAPID, sans laquelle le navigateur ne peut pas s'abonner. */
	@GetMapping("/api/push/cle")
	public VueClePush clePush() {
		return new VueClePush(proprietesPush.actif() ? proprietesPush.clePublique() : null);
	}

	@GetMapping(value = "/api/suivi/{reference}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter flux(@PathVariable String reference) {
		UUID tenantId = resolveur.tenantDe(reference)
				.orElseThrow(TicketInconnu::parReference);
		Tenant clinique = cliniques.parId(tenantId);
		return ContexteTenant.avec(tenantId,
				() -> diffusion.abonner(tenantId, duJour(reference, clinique).jour()));
	}

	/**
	 * Le ticket de cette référence, à condition qu'il soit d'aujourd'hui dans
	 * le fuseau de la clinique. Un lien de la veille est traité exactement
	 * comme une référence inconnue : même code, même message, rien à déduire.
	 */
	private Ticket duJour(String reference, Tenant clinique) {
		return tickets.findByReference(reference)
				.filter(ticket -> ticket.jour().equals(file.jourCourant(clinique)))
				.orElseThrow(TicketInconnu::parReference);
	}
}
