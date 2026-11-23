package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Le cloisonnement, et surtout sa <em>seconde</em> barrière.
 *
 * <p>La première barrière est le {@code tenant_id} passé dans chaque requête :
 * elle tient tant que personne n'oublie un {@code WHERE}. Ces tests vérifient
 * celle qui tient quand on l'oublie — les politiques RLS. Ils ne prouvent
 * quelque chose que parce que l'application tourne en rôle non propriétaire :
 * le propriétaire, lui, contourne les politiques.
 */
class CloisonnementIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Test
	@DisplayName("l'application ne tourne PAS en propriétaire — sans quoi tous "
			+ "les tests de cloisonnement seraient verts pour rien")
	void leRoleApplicatifNEstPasProprietaire() {
		String role = applicatif.queryForObject("SELECT current_user", String.class);
		assertThat(role).isEqualTo(ROLE_APPLICATIF);
		assertThat(role).isNotEqualTo(POSTGRES.getUsername());

		Boolean contourne = applicatif.queryForObject("""
				SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname = current_user
				""", Boolean.class);
		assertThat(contourne).as("un rôle qui contourne le RLS rendrait les "
				+ "politiques décoratives").isFalse();
	}

	@Test
	@DisplayName("une requête SANS filtre de tenant ne voit que le tenant lié")
	void uneRequeteSansFiltreNeVoitQueSonTenant() {
		UUID clinqueA = creerClinique("alpha", "Clinique Alpha", 2, 180);
		UUID cliniqueB = creerClinique("beta", "Clinique Beta", 2, 180);

		ContexteTenant.executer(clinqueA, () -> creation.creerDepuisQr(
				cliniques.parSlug("alpha"), file.jourCourant(cliniques.parSlug("alpha")),
				"0611111111"));
		ContexteTenant.executer(cliniqueB, () -> {
			creation.creerDepuisQr(cliniques.parSlug("beta"),
					file.jourCourant(cliniques.parSlug("beta")), "0622222222");
			creation.creerDepuisQr(cliniques.parSlug("beta"),
					file.jourCourant(cliniques.parSlug("beta")), "0633333333");
		});

		assertThat(proprietaire.queryForObject("SELECT count(*) FROM ticket", Long.class))
				.as("trois tickets existent en base").isEqualTo(3);

		ContexteTenant.executer(clinqueA, () ->
				assertThat(applicatif.queryForObject("SELECT count(*) FROM ticket", Long.class))
						.as("mais Alpha n'en voit qu'un").isEqualTo(1));

		ContexteTenant.executer(cliniqueB, () ->
				assertThat(applicatif.queryForObject("SELECT count(*) FROM ticket", Long.class))
						.as("et Beta en voit deux").isEqualTo(2));
	}

	@Test
	@DisplayName("sans tenant lié, rien n'est visible : l'oubli donne une liste "
			+ "vide, pas une fuite")
	void sansTenantLieRienNEstVisible() {
		UUID clinique = creerClinique("ferme", "Clinique Fermée", 2, 180);
		ContexteTenant.executer(clinique, () -> creation.creerDepuisQr(
				cliniques.parSlug("ferme"), file.jourCourant(cliniques.parSlug("ferme")),
				"0611111111"));

		ContexteTenant.delier();
		assertThat(applicatif.queryForObject("SELECT count(*) FROM ticket", Long.class))
				.isZero();
		assertThat(applicatif.queryForObject("SELECT count(*) FROM notification", Long.class))
				.isZero();
	}

	@Test
	@DisplayName("écrire une ligne au nom d'un autre tenant est refusé par la base")
	void ecrireChezUnAutreTenantEstRefuse() {
		UUID clinqueA = creerClinique("ecriture-a", "A", 2, 180);
		UUID cliniqueB = creerClinique("ecriture-b", "B", 2, 180);

		ContexteTenant.executer(clinqueA, () -> assertThatThrownBy(() ->
				applicatif.update("""
						INSERT INTO ticket (id, tenant_id, jour, numero_jour, reference,
						                    priorite, ordre, statut)
						VALUES (?, ?, current_date, 1, 'abcdefghijkl', 0, 1, 'EN_ATTENTE')
						""", UUID.randomUUID(), cliniqueB))
				.as("la clause WITH CHECK de la politique s'y oppose")
				// Spring enveloppe l'erreur PostgreSQL : le motif est dans la
				// cause, pas dans le message de surface.
				.hasStackTraceContaining("row-level security policy"));
	}

	@Test
	@DisplayName("la table tenant reste lisible sans tenant lié : il faut pouvoir "
			+ "résoudre le slug d'un QR avant de savoir de quelle clinique il s'agit")
	void laTableTenantResteLisible() {
		creerClinique("lisible", "Clinique Lisible", 2, 180);
		ContexteTenant.delier();
		assertThat(cliniques.parSlug("lisible").nom()).isEqualTo("Clinique Lisible");
	}
}
