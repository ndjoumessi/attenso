package ai.attenso.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Le décor commun des tests de la commande de réinitialisation : une clinique
 * chargée de tout ce qu'une démonstration accumule en une semaine, pour que
 * chaque table à effacer soit réellement touchée — et que l'ordre des clés
 * étrangères soit réellement éprouvé.
 *
 * <p>Pas de {@code @Test} ici : chaque sous-classe porte SA configuration, et un
 * test hérité tournerait dans toutes.
 */
abstract class ReinitialisationBase extends SocleIntegration {

	static final LocalDate LE_MARQUEUR = LocalDate.of(2020, 1, 6);

	/** Ce que le semis ne produira jamais : un ticket d'un jour lointain, avec ses attaches. */
	record Marqueur(UUID ticket, UUID animal, UUID utilisateur) {
	}

	/**
	 * Remplit chaque table que la commande efface, y compris celles qui
	 * pointent vers un ticket, un rendez-vous ou une vaccination.
	 */
	Marqueur chargerUneClinique(UUID tenantId) {
		UUID utilisateur = creerUtilisateur(tenantId, "gerant@" + tenantId + ".test", "mdp-de-test",
				ai.attenso.utilisateur.Role.GERANT);
		proprietaire.update("""
				INSERT INTO motif_priorite (tenant_id, libelle, rang, ordre)
				VALUES (?, 'Motif propre à la clinique', 6, 9)
				""", tenantId);
		UUID ticket = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO ticket (id, tenant_id, jour, numero_jour, reference, priorite, ordre,
				                    statut, cree_le)
				VALUES (?, ?, ?, 1, ?, 0, 1, 'EN_ATTENTE', now())
				""", ticket, tenantId, Date.valueOf(LE_MARQUEUR), "ref-" + ticket.toString().substring(0, 12));
		proprietaire.update("""
				INSERT INTO notification (id, tenant_id, ticket_id, type, canal, destinataire,
				                          corps, statut)
				VALUES (?, ?, ?, 'TICKET', 'SMS', '+33600000000', 'corps', 'ENVOYE')
				""", UUID.randomUUID(), tenantId, ticket);
		proprietaire.update("""
				INSERT INTO push_abonnement (tenant_id, ticket_id, endpoint, cle_p256dh, cle_auth)
				VALUES (?, ?, 'https://push.test/x', 'p', 'a')
				""", tenantId, ticket);
		proprietaire.update("""
				INSERT INTO journal_acces (tenant_id, utilisateur_id, ticket_id, action)
				VALUES (?, ?, ?, 'LECTURE')
				""", tenantId, utilisateur, ticket);
		proprietaire.update("""
				INSERT INTO commande (id, tenant_id, ticket_id, action) VALUES (?, ?, ?, 'APPELER')
				""", UUID.randomUUID(), tenantId, ticket);
		proprietaire.update("INSERT INTO mesure_jour (tenant_id, jour, arrivants_total) VALUES (?, ?, 4)",
				tenantId, Date.valueOf(LE_MARQUEUR));

		UUID creneau = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO creneau (id, tenant_id, jour, heure, places, prises)
				VALUES (?, ?, ?, '09:00', 1, 1)
				""", creneau, tenantId, Date.valueOf(LE_MARQUEUR));
		UUID rendezVous = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO rendez_vous (id, tenant_id, creneau_id, jour, heure, reference)
				VALUES (?, ?, ?, ?, '09:00', ?)
				""", rendezVous, tenantId, creneau, Date.valueOf(LE_MARQUEUR),
				"rdv-" + rendezVous.toString().substring(0, 12));
		proprietaire.update("""
				INSERT INTO notification (id, tenant_id, rendez_vous_id, type, canal, destinataire,
				                          corps, statut)
				VALUES (?, ?, ?, 'RAPPEL_RDV', 'SMS', '+33600000000', 'corps', 'ENVOYE')
				""", UUID.randomUUID(), tenantId, rendezVous);
		proprietaire.update("""
				INSERT INTO plage_horaire (tenant_id, jour_semaine, debut, fin, duree_minutes, places)
				VALUES (?, 1, '08:00', '09:00', 30, 1)
				""", tenantId);

		UUID animal = UUID.randomUUID();
		proprietaire.update("INSERT INTO animal (id, tenant_id, nom, espece) VALUES (?, ?, 'Marqueur', 'Chat')",
				animal, tenantId);
		UUID vaccination = UUID.randomUUID();
		proprietaire.update("""
				INSERT INTO vaccination (id, tenant_id, animal_id, vaccin, fait_le)
				VALUES (?, ?, ?, 'Marqueur', ?)
				""", vaccination, tenantId, animal, Date.valueOf(LE_MARQUEUR));
		proprietaire.update("""
				INSERT INTO notification (id, tenant_id, vaccination_id, type, canal, destinataire,
				                          corps, statut)
				VALUES (?, ?, ?, 'RAPPEL_VACCIN', 'SMS', '+33600000000', 'corps', 'ENVOYE')
				""", UUID.randomUUID(), tenantId, vaccination);
		return new Marqueur(ticket, animal, utilisateur);
	}

	/** Les tables que la commande efface : elles doivent être vides de ce qui précède. */
	static final String[] TABLES_EFFACEES = {"notification", "push_abonnement", "ticket",
			"mesure_jour", "rendez_vous", "creneau", "plage_horaire", "vaccination", "animal"};

	long compter(String table, UUID tenantId) {
		Long total = proprietaire.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
		return total == null ? 0 : total;
	}

	UUID idDe(String slug) {
		return proprietaire.queryForObject("SELECT id FROM tenant WHERE slug = ?", UUID.class, slug);
	}

	boolean existe(String table, UUID id) {
		Long total = proprietaire.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?",
				Long.class, id);
		return total != null && total > 0;
	}

	/** Branche un collecteur sur le journal de la commande, pour lire ses refus. */
	ListAppender<ILoggingEvent> ecouterLeJournal() {
		Logger journal = (Logger) LoggerFactory
				.getLogger(ai.attenso.amorce.ReinitialisationDemonstration.class);
		ListAppender<ILoggingEvent> collecteur = new ListAppender<>();
		collecteur.start();
		journal.addAppender(collecteur);
		return collecteur;
	}

	void cesserDEcouter(ListAppender<ILoggingEvent> collecteur) {
		((Logger) LoggerFactory.getLogger(ai.attenso.amorce.ReinitialisationDemonstration.class))
				.detachAppender(collecteur);
	}

	List<String> messages(ListAppender<ILoggingEvent> collecteur) {
		return collecteur.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	/** Un compte par table à effacer, pour comparer avant et après. */
	java.util.Map<String, Long> instantane(UUID tenantId) {
		java.util.Map<String, Long> comptes = new java.util.LinkedHashMap<>();
		for (String table : TABLES_EFFACEES) {
			comptes.put(table, compter(table, tenantId));
		}
		return comptes;
	}
}
