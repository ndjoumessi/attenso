package ai.attenso.notification;

import ai.attenso.config.ProprietesSms;
import ai.attenso.push.AbonnementPush;
import ai.attenso.push.ExpediteurPush;
import ai.attenso.push.PushDao;
import ai.attenso.push.ResultatPush;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Ticket;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demande et envoi des notifications.
 *
 * <p>Deux temps séparés, et c'est le point : la <em>demande</em> s'inscrit dans
 * la transaction de la transition qui l'a provoquée, ce qui met le plafond à
 * l'abri des doubles déclenchements ; l'<em>envoi</em> a lieu après commit, ce
 * qui évite de payer un SMS pour une transition finalement annulée.
 */
@Service
public class ServiceNotification {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceNotification.class);

	private static final int LOT_ENVOI = 20;

	private final NotificationDao dao;

	private final ExpediteurSms expediteur;

	private final FabriqueLien liens;

	private final ProprietesSms proprietes;

	private final Clock horloge;

	/**
	 * Vides quand le push n'est pas configuré (EF-307). Le service tourne
	 * alors exactement comme avant, tout en SMS : une fonction absente ne doit
	 * jamais empêcher la file de fonctionner.
	 */
	private final Optional<PushDao> push;

	private final Optional<ExpediteurPush> expediteurPush;

	public ServiceNotification(NotificationDao dao, ExpediteurSms expediteur, FabriqueLien liens,
			ProprietesSms proprietes, Clock horloge, Optional<PushDao> push,
			Optional<ExpediteurPush> expediteurPush) {
		this.dao = dao;
		this.expediteur = expediteur;
		this.liens = liens;
		this.proprietes = proprietes;
		this.horloge = horloge;
		this.push = push;
		this.expediteurPush = expediteurPush;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void demanderTicket(Tenant tenant, Ticket ticket) {
		demander(tenant, ticket, TypeNotification.TICKET,
				ModeleMessage.ticket(tenant.nom(), ticket.numeroJour(), liens.pour(ticket)));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void demanderPrevenir(Tenant tenant, Ticket ticket) {
		demander(tenant, ticket, TypeNotification.PREVENIR,
				ModeleMessage.prevenir(tenant.nom(), liens.pour(ticket)));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void demanderCorrectif(Tenant tenant, Ticket ticket, long minutes) {
		demander(tenant, ticket, TypeNotification.CORRECTIF,
				ModeleMessage.correctif(tenant.nom(), minutes, liens.pour(ticket)));
	}

	/**
	 * Rappel la veille d'un rendez-vous (EF-905), ou rappel de vaccin
	 * (EF-906).
	 *
	 * <p>Toujours en SMS : un rendez-vous se prend des semaines à l'avance, et
	 * l'abonnement push d'un ticket est purgé le lendemain de sa visite. Il
	 * n'y a donc rien à quoi pousser.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void demanderPourRendezVous(Tenant tenant, UUID rendezVousId, String telephoneE164,
			TypeNotification type, String corps) {
		demanderHorsFile(tenant, telephoneE164, type, corps,
				(id, cout) -> dao.creerPourRendezVous(id, tenant.id(), rendezVousId, type,
						Canal.SMS, telephoneE164, corps, cout));
	}

	/** Rappel de vaccin (EF-906), qui n'est attaché ni à un ticket ni à un rendez-vous. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void demanderPourVaccination(Tenant tenant, UUID vaccinationId,
			String telephoneE164, TypeNotification type, String corps) {
		demanderHorsFile(tenant, telephoneE164, type, corps,
				(id, cout) -> dao.creerPourVaccination(id, tenant.id(), vaccinationId, type,
						Canal.SMS, telephoneE164, corps, cout));
	}

	/**
	 * Le tronc commun des notifications qui ne viennent pas de la file.
	 *
	 * <p>Toujours en SMS : un rendez-vous se prend des semaines à l'avance et
	 * un rappel de vaccin des mois, alors que l'abonnement push d'un ticket
	 * est purgé le lendemain de la visite. Il n'y a rien à quoi pousser.
	 */
	private void demanderHorsFile(Tenant tenant, String telephoneE164, TypeNotification type,
			String corps, java.util.function.BiPredicate<UUID, BigDecimal> creation) {
		if (telephoneE164 == null || telephoneE164.isBlank()) {
			return;
		}
		int segments = CompteurSegments.segments(corps);
		BigDecimal cout = proprietes.coutCentimesParSegment()
				.multiply(BigDecimal.valueOf(segments));
		if (!creation.test(UUID.randomUUID(), cout)) {
			JOURNAL.debug("plafond atteint : {} déjà demandé", type);
		}
	}

	private void demander(Tenant tenant, Ticket ticket, TypeNotification type, String corps) {
		// EF-307 : si ce client a installé la PWA et autorisé les
		// notifications, on bascule sur le push. Gratuit, instantané, et il ne
		// dépend d'aucune validation opérateur — un ticket sans numéro de
		// mobile devient notifiable, ce qu'aucun SMS ne permettait.
		Optional<AbonnementPush> abonnement = push
				.flatMap(dao -> dao.pourTicket(tenant.id(), ticket.id()));
		if (abonnement.isPresent()) {
			BigDecimal gratuit = BigDecimal.ZERO;
			if (!dao.creer(UUID.randomUUID(), tenant.id(), ticket.id(), type, Canal.PUSH,
					abonnement.get().endpoint(), corps, gratuit)) {
				JOURNAL.debug("plafond atteint : {} déjà demandé pour le ticket {}", type,
						ticket.id());
			}
			return;
		}
		if (!ticket.joignable()) {
			return;
		}
		int segments = CompteurSegments.segments(corps);
		if (segments > 1) {
			JOURNAL.warn("message {} de {} caractères = {} segments facturés — le modèle "
					+ "économique suppose un segment ; vérifier le nom de la clinique "
					+ "« {} » et la base de lien", type, corps.length(), segments, tenant.nom());
		}
		BigDecimal cout = proprietes.coutCentimesParSegment()
				.multiply(BigDecimal.valueOf(segments));
		boolean cree = dao.creer(UUID.randomUUID(), tenant.id(), ticket.id(), type, Canal.SMS,
				ticket.telephoneE164(), corps, cout);
		if (!cree) {
			JOURNAL.debug("plafond atteint : {} déjà demandé pour le ticket {}", type,
					ticket.id());
		}
	}

	/**
	 * Vide la file d'envoi d'une clinique. Hors transaction de transition.
	 *
	 * <p>Une clinique dont l'expéditeur n'est pas encore enregistré auprès des
	 * opérateurs n'envoie rien, et ses notifications sont abandonnées tout de
	 * suite plutôt que réessayées : la validation opérateur prend des jours, et
	 * un « c'est presque à vous » délivré le lendemain serait pire que pas de
	 * SMS du tout. La file, l'écran et la page de suivi, eux, continuent de
	 * fonctionner.
	 */
	@Transactional
	public int envoyerEnAttente(Tenant tenant) {
		// L'absence d'expéditeur SMS ne condamne QUE les SMS : une notification
		// push ne dépend d'aucune validation opérateur, et l'abandonner avec
		// les autres priverait de notification les seuls clients qu'on pouvait
		// encore joindre gratuitement.
		if (!tenant.peutEnvoyerDesSms()) {
			long bloques = dao.enAttente(tenant.id(), LOT_ENVOI).stream()
					.filter(a -> a.canal() == Canal.SMS).count();
			if (bloques > 0) {
				JOURNAL.warn("clinique « {} » sans expéditeur SMS enregistré : {} "
						+ "notification(s) abandonnée(s). La file reste opérationnelle.",
						tenant.slug(), bloques);
				dao.abandonner(tenant.id(), Canal.SMS,
						"expéditeur SMS non enregistré pour cette clinique");
			}
		}

		List<ANotifier> lot = dao.enAttente(tenant.id(), LOT_ENVOI);
		int envoyes = 0;
		for (ANotifier aNotifier : lot) {
			ResultatEnvoi resultat;
			try {
				resultat = aNotifier.canal() == Canal.PUSH
						? envoyerParPush(tenant, aNotifier)
						: expediteur.envoyer(new MessageSms(aNotifier.id(),
								aNotifier.destinataire(), tenant.expediteurSms(),
								aNotifier.corps()));
			}
			catch (RuntimeException panne) {
				JOURNAL.warn("envoi {} en échec : {}", aNotifier.id(), panne.getMessage());
				resultat = ResultatEnvoi.echec(panne.getClass().getSimpleName() + " : "
						+ panne.getMessage());
			}
			if (resultat.reussi()) {
				dao.marquerEnvoye(aNotifier.id(), resultat.fournisseurId(),
						resultat.coutCentimes(), resultat.coutFournisseur(), horloge.instant());
				envoyes++;
			}
			else {
				dao.marquerEchec(aNotifier.id(), resultat.erreur());
			}
		}
		return envoyes;
	}

	/**
	 * Un push, et le ménage que la spécification impose.
	 *
	 * <p>Un abonnement que l'opérateur déclare mort (404 ou 410) est
	 * <strong>retiré</strong> plutôt que réessayé : le réessayer donnerait le
	 * même refus jusqu'à épuisement des tentatives, et le client retombe sur
	 * le SMS dès la notification suivante.
	 */
	private ResultatEnvoi envoyerParPush(Tenant tenant, ANotifier aNotifier) {
		if (expediteurPush.isEmpty() || push.isEmpty()) {
			return ResultatEnvoi.echec("push non configuré sur ce service");
		}
		Optional<AbonnementPush> abonnement = push.get()
				.pourTicket(tenant.id(), aNotifier.ticketId());
		if (abonnement.isEmpty()) {
			return ResultatEnvoi.echec("abonnement push retiré entre-temps");
		}
		ResultatPush resultat = expediteurPush.get().envoyer(abonnement.get(),
				aNotifier.corps());
		if (resultat.abonnementMort()) {
			push.get().retirer(tenant.id(), abonnement.get().endpoint());
			return ResultatEnvoi.echec(resultat.erreur());
		}
		// Coût nul, et c'est tout l'intérêt : le modèle économique suppose
		// deux SMS par visite, le push les remplace gratuitement.
		return resultat.reussi()
				? ResultatEnvoi.reussi(null, BigDecimal.ZERO, BigDecimal.ZERO)
				: ResultatEnvoi.echec(resultat.erreur());
	}
}
