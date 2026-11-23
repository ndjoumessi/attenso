package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.PlafondJournalierAtteint;
import ai.attenso.ticket.Priorite;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import ai.attenso.utilisateur.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Le plafond de dépense par clinique et par jour. Réglé à deux ici pour que le
 * test soit court ; en production il est très au-dessus d'une journée réelle.
 */
@TestPropertySource(properties = "attenso.file.tickets-max-par-jour=2")
class PlafondJournalierIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Test
	@DisplayName("au-delà du plafond, l'enregistrement est refusé et AUCUN SMS "
			+ "n'est demandé — c'est tout l'objet du garde-fou")
	void auDelaDuPlafondAucunSmsNEstDemande() {
		UUID id = creerClinique("plafond-jour", "Clinique", 2, 180);
		Tenant clinique = cliniques.parSlug("plafond-jour");

		ContexteTenant.executer(id, () -> {
			creation.creerDepuisQr(clinique, file.jourCourant(clinique), "0611111111");
			creation.creerDepuisQr(clinique, file.jourCourant(clinique), "0622222222");

			assertThatThrownBy(() -> creation.creerDepuisQr(clinique,
					file.jourCourant(clinique), "0633333333"))
					.isInstanceOf(PlafondJournalierAtteint.class);
		});

		assertThat(compterNotifications(id, "TICKET"))
				.as("deux tickets, deux SMS, et pas un de plus")
				.isEqualTo(2);
	}

	@Test
	@DisplayName("le plafond ne barre JAMAIS la route à l'accueil : une urgence "
			+ "passe même quand l'enregistrement public est fermé")
	void lUrgenceDeLAccueilPasseMalgreLePlafond() {
		UUID id = creerClinique("plafond-urgence", "Clinique", 2, 180);
		Tenant clinique = cliniques.parSlug("plafond-urgence");
		// Un vrai compte : journal_acces porte une clé étrangère vers utilisateur.
		UUID asv = creerUtilisateur(id, "asv@clinique.fr", "mot-de-passe-solide",
				Role.ACCUEIL);

		ContexteTenant.executer(id, () -> {
			creation.creerDepuisQr(clinique, file.jourCourant(clinique), "0611111111");
			creation.creerDepuisQr(clinique, file.jourCourant(clinique), "0622222222");

			// Le comptoir est fermé pour la journée…
			assertThatThrownBy(() -> creation.creerDepuisQr(clinique,
					file.jourCourant(clinique), "0633333333"))
					.isInstanceOf(PlafondJournalierAtteint.class);

			// …mais un chien qui saigne doit pouvoir entrer. Un plafond de
			// dépense qui empêche de soigner coûte plus cher que la facture
			// qu'il évite.
			Ticket urgence = creation.creerUrgence(clinique, file.jourCourant(clinique),
					"0644444444", asv);
			assertThat(urgence.priorite()).isEqualTo(Priorite.URGENCE);
			assertThat(file.position(urgence)).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("le plafond est par clinique : celui d'une clinique n'épuise "
			+ "pas celui de sa voisine")
	void lePlafondEstParClinique() {
		UUID alpha = creerClinique("plafond-alpha", "Alpha", 2, 180);
		UUID beta = creerClinique("plafond-beta", "Beta", 2, 180);

		ContexteTenant.executer(alpha, () -> {
			Tenant a = cliniques.parSlug("plafond-alpha");
			creation.creerDepuisQr(a, file.jourCourant(a), "0611111111");
			creation.creerDepuisQr(a, file.jourCourant(a), "0622222222");
		});

		ContexteTenant.executer(beta, () -> {
			Tenant b = cliniques.parSlug("plafond-beta");
			assertThat(creation.creerDepuisQr(b, file.jourCourant(b), "0633333333")
					.numeroJour()).isEqualTo(1);
		});
	}
}
