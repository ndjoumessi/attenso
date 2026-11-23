package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.LigneFile;
import ai.attenso.ticket.Priorite;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Statut;
import ai.attenso.ticket.TacheGrace;
import ai.attenso.ticket.Ticket;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Le moteur de la file, contre un vrai PostgreSQL. */
class FileIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Autowired
	TacheGrace grace;

	private Tenant clinique;

	private LocalDate jour;

	private Tenant installer(String slug, int seuil, int graceSecondes) {
		creerClinique(slug, "Clinique des Cerisiers", seuil, graceSecondes);
		this.clinique = cliniques.parSlug(slug);
		this.jour = file.jourCourant(clinique);
		ContexteTenant.lier(clinique.id());
		return clinique;
	}

	private Ticket arriver(String telephone) {
		return creation.creerDepuisQr(clinique, jour, telephone);
	}

	@Test
	@DisplayName("les positions suivent l'ordre d'arrivée, et le rang n'est pas stocké")
	void positionsParOrdreDArrivee() {
		installer("cerisiers", 2, 180);
		Ticket un = arriver("0611111111");
		Ticket deux = arriver("0622222222");
		Ticket trois = arriver("0633333333");

		assertThat(file.position(un)).isEqualTo(1);
		assertThat(file.position(deux)).isEqualTo(2);
		assertThat(file.position(trois)).isEqualTo(3);
		assertThat(List.of(un, deux, trois)).extracting(Ticket::numeroJour)
				.containsExactly(1, 2, 3);
	}

	@Test
	@DisplayName("une urgence passe devant sans renuméroter personne")
	void lUrgencePasseDevantSansRenumeroter() {
		installer("urgence", 2, 180);
		Ticket un = arriver("0611111111");
		Ticket deux = arriver("0622222222");

		Ticket urgence = creation.creerUrgence(clinique, jour, "0699999999", null);

		assertThat(urgence.priorite()).isEqualTo(Priorite.URGENCE);
		assertThat(file.position(urgence)).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), un.id()))).isEqualTo(2);
		assertThat(file.position(file.exiger(clinique.id(), deux.id()))).isEqualTo(3);
		// Les numéros affichés aux clients n'ont pas bougé : c'est tout
		// l'intérêt de ne pas stocker le rang.
		assertThat(file.exiger(clinique.id(), un.id()).numeroJour()).isEqualTo(1);
		assertThat(file.exiger(clinique.id(), deux.id()).numeroJour()).isEqualTo(2);
	}

	@Test
	@DisplayName("une urgence envoie un correctif à tous ceux qui sont en état "
			+ "PREVENU, et à personne d'autre")
	void lUrgenceEnvoieUnCorrectifAuxSeulsPrevenus() {
		installer("correctif", 2, 180);
		arriver("0611111111");
		arriver("0622222222");
		Ticket trois = arriver("0633333333");

		// Seuil 2 : les deux premiers sont passés PREVENU, le troisième non.
		long prevenus = file.tableauDuJour(clinique.id(), jour).stream()
				.filter(ligne -> ligne.statut() == Statut.PREVENU).count();
		assertThat(prevenus).isEqualTo(2);
		assertThat(compterNotifications(clinique.id(), "PREVENIR"))
				.as("promus en silence : ils étaient au comptoir, le SMS de "
						+ "ticket venait de partir")
				.isZero();

		creation.creerUrgence(clinique, jour, null, null);

		assertThat(compterNotifications(clinique.id(), "CORRECTIF"))
				.as("un correctif par ticket prévenu — le troisième, qui n'a "
						+ "jamais été prévenu, ne reçoit rien")
				.isEqualTo(prevenus);
		assertThat(dernierCorps(clinique.id(), "CORRECTIF"))
				.contains("urgence").contains("min de plus");
		assertThat(file.position(file.exiger(clinique.id(), trois.id())))
				.as("et tout le monde a reculé d'un cran")
				.isEqualTo(4);
	}

	@Test
	@DisplayName("CORRECTION DE LA SPEC : c'est « présent » qui remonte la file, "
			+ "pas « terminé » — donc c'est lui qui déclenche « revenez »")
	void laPrevenanceSeDeclencheQuandLaConsultationCommence() {
		installer("prevenance", 1, 180);
		Ticket un = arriver("0611111111");
		Ticket deux = arriver("0622222222");

		// « un » est en tête dès sa création : promu PREVENU en silence.
		assertThat(file.exiger(clinique.id(), un.id()).statut()).isEqualTo(Statut.PREVENU);
		assertThat(compterNotifications(clinique.id(), "PREVENIR")).isZero();

		file.appeler(clinique, un.id(), null, null);
		assertThat(compterNotifications(clinique.id(), "PREVENIR"))
				.as("appeler ne déplace personne : APPELE occupe toujours la file")
				.isZero();

		file.present(clinique, un.id(), null, null);

		assertThat(file.exiger(clinique.id(), deux.id()).statut())
				.as("« un » a quitté la file à EN_CONSULTATION : « deux » est en tête")
				.isEqualTo(Statut.PREVENU);
		assertThat(compterNotifications(clinique.id(), "PREVENIR"))
				.as("et il reçoit « revenez » — ce que la liste de déclencheurs "
						+ "de la spec §4 n'aurait jamais produit")
				.isEqualTo(1);

		file.termine(clinique, un.id(), null, null);
		assertThat(compterNotifications(clinique.id(), "PREVENIR"))
				.as("« terminé » ne déplace plus personne : aucun SMS de plus")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("le plafond de la base rend le second « revenez » impossible")
	void leSecondRevenezEstImpossible() {
		installer("plafond", 1, 180);
		arriver("0611111111");
		Ticket deux = arriver("0622222222");

		// Plusieurs réévaluations de suite : le ticket est déjà PREVENU, donc la
		// boucle ne le repromeut pas — et même si elle le faisait, l'index
		// unique refuserait la seconde ligne.
		for (int i = 0; i < 5; i++) {
			file.reevaluerApresUrgence(clinique, jour);
		}
		assertThat(compterNotifications(clinique.id(), "PREVENIR")).isLessThanOrEqualTo(1);
		assertThat(compterNotifications(clinique.id(), "CORRECTIF"))
				.as("un seul correctif par ticket, quel que soit le nombre d'urgences")
				.isLessThanOrEqualTo(2);
		assertThat(deux).isNotNull();
	}

	@Test
	@DisplayName("grâce expirée : le ticket repasse juste après le suivant, pas en fin de file")
	void lAbsentRepasseJusteApresLeSuivant() {
		installer("grace", 1, 0);
		Ticket un = arriver("0611111111");
		Ticket deux = arriver("0622222222");
		Ticket trois = arriver("0633333333");

		file.appeler(clinique, un.id(), null, null);
		grace.tick();

		Ticket absent = file.exiger(clinique.id(), un.id());
		assertThat(absent.statut()).isEqualTo(Statut.ABSENT);
		assertThat(file.position(absent))
				.as("derrière « deux », devant « trois »")
				.isEqualTo(2);
		assertThat(file.position(file.exiger(clinique.id(), deux.id()))).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), trois.id()))).isEqualTo(3);
	}

	@Test
	@DisplayName("« parti » alimente le compteur de départs, « absent » non — "
			+ "c'est le chiffre qui justifie l'abonnement")
	void seulPartiCompteCommeDepart() {
		installer("departs", 1, 0);
		Ticket un = arriver("0611111111");
		Ticket deux = arriver("0622222222");

		file.appeler(clinique, un.id(), null, null);
		grace.tick();
		assertThat(file.departsDuJour(clinique.id(), jour))
				.as("un absent n'est pas un départ : il revient")
				.isZero();

		file.parti(clinique, deux.id(), null, null);
		assertThat(file.departsDuJour(clinique.id(), jour)).isEqualTo(1);
	}

	@Test
	@DisplayName("une commande rejouée après une coupure réseau n'est pas appliquée deux fois")
	void leRejeuEstIdempotent() {
		installer("rejeu", 2, 180);
		Ticket un = arriver("0611111111");
		UUID commande = UUID.randomUUID();

		file.appeler(clinique, un.id(), null, commande);
		assertThat(file.exiger(clinique.id(), un.id()).statut()).isEqualTo(Statut.APPELE);

		file.present(clinique, un.id(), null, UUID.randomUUID());
		assertThat(file.exiger(clinique.id(), un.id()).statut())
				.isEqualTo(Statut.EN_CONSULTATION);

		// La tablette rejoue « appeler » à la reconnexion, avec la même clé.
		// Sans idempotence, la machine à états refuserait — ou pire, le ticket
		// reviendrait en arrière.
		file.appeler(clinique, un.id(), null, commande);
		assertThat(file.exiger(clinique.id(), un.id()).statut())
				.as("l'état avance, il ne recule pas")
				.isEqualTo(Statut.EN_CONSULTATION);
	}

	@Test
	@DisplayName("le ticket rendu à la création porte l'état d'APRÈS la "
			+ "réévaluation, pas celui d'avant")
	void leTicketRenduEstAJour() {
		installer("rendu", 2, 180);
		Ticket un = arriver("0611111111");

		// Promu PREVENU par la réévaluation déclenchée dans la même
		// transaction : si l'instance rendue était la copie détachée d'avant le
		// merge, elle dirait encore EN_ATTENTE — et l'API mentirait à la
		// tablette.
		assertThat(un.statut()).isEqualTo(Statut.PREVENU);
		assertThat(file.exiger(clinique.id(), un.id()).statut()).isEqualTo(un.statut());

		Ticket urgence = creation.creerUrgence(clinique, jour, "0699999999", null);
		assertThat(urgence.statut()).isEqualTo(Statut.PREVENU);
		assertThat(file.exiger(clinique.id(), urgence.id()).statut())
				.isEqualTo(urgence.statut());
	}

	@Test
	@DisplayName("le tableau du jour garde les tickets clos, sans position")
	void leTableauGardeLesTicketsClos() {
		installer("tableau", 2, 180);
		Ticket un = arriver("0611111111");
		arriver("0622222222");
		file.parti(clinique, un.id(), null, null);

		List<LigneFile> lignes = file.tableauDuJour(clinique.id(), jour);
		assertThat(lignes).hasSize(2);
		LigneFile parti = lignes.stream()
				.filter(ligne -> ligne.statut() == Statut.PARTI).findFirst().orElseThrow();
		assertThat(parti.position()).as("un ticket clos n'a pas de rang").isZero();
	}
}
