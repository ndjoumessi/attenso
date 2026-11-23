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

	/** Vide la file d'envoi d'un tenant. Hors transaction de transition. */
	@Transactional
	public int envoyerEnAttente(UUID tenantId) {
		List<ANotifier> lot = dao.enAttente(tenantId, LOT_ENVOI);
		int envoyes = 0;
		for (ANotifier aNotifier : lot) {
			ResultatEnvoi resultat;
			try {
				resultat = expediteur.envoyer(aNotifier.destinataire(), aNotifier.corps());
			}
			catch (RuntimeException panne) {
				JOURNAL.warn("envoi {} en échec : {}", aNotifier.id(), panne.getMessage());
				resultat = ResultatEnvoi.echec(panne.getClass().getSimpleName() + " : "
						+ panne.getMessage());
			}
			if (resultat.reussi()) {
				dao.marquerEnvoye(aNotifier.id(), resultat.fournisseurId(),
						resultat.coutCentimes(), horloge.instant());
				envoyes++;
			}
			else {
				dao.marquerEchec(aNotifier.id(), resultat.erreur());
			}
		}
		return envoyes;
	}
}
