package ai.attenso.notification;

import ai.attenso.config.ProprietesSms;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Ticket;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
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

	public ServiceNotification(NotificationDao dao, ExpediteurSms expediteur, FabriqueLien liens,
			ProprietesSms proprietes, Clock horloge) {
		this.dao = dao;
		this.expediteur = expediteur;
		this.liens = liens;
		this.proprietes = proprietes;
		this.horloge = horloge;
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

	private void demander(Tenant tenant, Ticket ticket, TypeNotification type, String corps) {
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
		if (!tenant.peutEnvoyerDesSms()) {
			List<ANotifier> bloques = dao.enAttente(tenant.id(), LOT_ENVOI);
			if (!bloques.isEmpty()) {
				JOURNAL.warn("clinique « {} » sans expéditeur SMS enregistré : {} "
						+ "notification(s) abandonnée(s). La file reste opérationnelle.",
						tenant.slug(), bloques.size());
				dao.abandonner(tenant.id(), "expéditeur SMS non enregistré pour cette clinique");
			}
			return 0;
		}

		List<ANotifier> lot = dao.enAttente(tenant.id(), LOT_ENVOI);
		int envoyes = 0;
		for (ANotifier aNotifier : lot) {
			ResultatEnvoi resultat;
			try {
				resultat = expediteur.envoyer(new MessageSms(aNotifier.id(),
						aNotifier.destinataire(), tenant.expediteurSms(), aNotifier.corps()));
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
}
