package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Statut;
import ai.attenso.ticket.Ticket;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * L'état d'une clinique pendant son installation : l'expéditeur SMS à son nom
 * est déposé mais pas encore validé par les opérateurs, ce qui prend des jours.
 *
 * <p>Ce n'est pas un cas limite, c'est le premier jour de chaque nouveau
 * client. Ce que le produit doit faire dans cet état décide s'il est
 * installable progressivement ou pas du tout.
 */
class ExpediteurNonEnregistreIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Test
	@DisplayName("sans expéditeur validé, la file fonctionne entièrement — "
			+ "seuls les SMS sont abandonnés, et le motif est écrit")
	void laFileFonctionneSansSms() {
		UUID id = creerClinique("sans-expediteur", "Clinique Neuve", 2, 180, null);
		Tenant clinique = cliniques.parSlug("sans-expediteur");
		assertThat(clinique.peutEnvoyerDesSms()).isFalse();

		ContexteTenant.executer(id, () -> {
			Ticket un = creation.creerDepuisQr(clinique, file.jourCourant(clinique),
					"0611111111");
			Ticket deux = creation.creerDepuisQr(clinique, file.jourCourant(clinique),
					"0622222222");

			assertThat(file.position(un)).isEqualTo(1);
			assertThat(file.position(deux)).isEqualTo(2);
			assertThat(un.statut()).isEqualTo(Statut.PREVENU);
		});

		assertThat(compterNotifications(id, "TICKET"))
				.as("les notifications sont bien demandées…")
				.isEqualTo(2);
		assertThat(statutNotification(id, "TICKET"))
				.as("…mais abandonnées tout de suite, et pas réessayées pendant "
						+ "des jours en attendant la validation opérateur")
				.isEqualTo("ECHEC");
		assertThat(erreurNotification(id))
				.as("le motif doit être lisible dans la table six semaines plus tard")
				.contains("expéditeur SMS non enregistré");
	}

	@Test
	@DisplayName("une clinique validée envoie normalement, sa voisine non "
			+ "validée n'empêche rien")
	void uneCliniqueValideeNEstPasAffecteeParSaVoisine() {
		UUID neuve = creerClinique("neuve", "Clinique Neuve", 2, 180, null);
		UUID etablie = creerClinique("etablie", "Clinique Etablie", 2, 180, "Cerisiers");

		ContexteTenant.executer(neuve, () -> {
			Tenant c = cliniques.parSlug("neuve");
			creation.creerDepuisQr(c, file.jourCourant(c), "0611111111");
		});
		ContexteTenant.executer(etablie, () -> {
			Tenant c = cliniques.parSlug("etablie");
			creation.creerDepuisQr(c, file.jourCourant(c), "0622222222");
		});

		assertThat(statutNotification(neuve, "TICKET")).isEqualTo("ECHEC");
		assertThat(statutNotification(etablie, "TICKET")).isEqualTo("ENVOYE");
	}
}
