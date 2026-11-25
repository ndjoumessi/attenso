package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.attenso.priorite.MotifDao;
import ai.attenso.priorite.MotifPriorite;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Priorisation;
import ai.attenso.ticket.Priorite;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** EF-407 : les priorités paramétrables, et la bande qu'elles ne franchissent pas. */
class PrioriteParametrableIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Autowired
	MotifDao motifs;

	private Tenant clinique;

	private LocalDate jour;

	private UUID installer(String slug) {
		UUID id = creerClinique(slug, "Clinique", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		this.jour = file.jourCourant(clinique);
		ContexteTenant.lier(id);
		motifs.poserLesDefauts(id);
		return id;
	}

	@Test
	@DisplayName("un motif fait passer devant la file normale, sans toucher aux numéros")
	void leMotifFaitPasserDevant() {
		installer("motifs");
		Ticket premier = creation.creerDepuisQr(clinique, jour, "0611111111");
		MotifPriorite age = motifs.actifs(clinique.id()).get(0);

		Ticket prioritaire = creation.creerPourClient(clinique, jour, "0622222222",
				Priorisation.motivee(age.rang(), age.libelle()), null);

		assertThat(file.position(prioritaire)).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), premier.id()))).isEqualTo(2);
		assertThat(prioritaire.numeroJour()).isEqualTo(2);
	}

	@Test
	@DisplayName("un rang intermédiaire autre que 5 est accepté et se place entre le 5 et la file normale")
	void unRangGradueEstAccepteEtOrdonne() {
		UUID id = installer("gradue");
		Ticket normal = creation.creerPourClient(clinique, jour, null, null);
		MotifPriorite age = motifs.actifs(id).get(0);
		Ticket rang5 = creation.creerPourClient(clinique, jour, null,
				Priorisation.motivee(age.rang(), age.libelle()), null);
		// Le rang 7 de « Chat stressé en caisse » : la contrainte de V1
		// n'admettait que 0, 5 et 10 et refusait tout motif gradué.
		Ticket rang7 = creation.creerPourClient(clinique, jour, null,
				Priorisation.motivee((short) 7, "Chat stressé en caisse"), null);

		assertThat(file.position(file.exiger(clinique.id(), rang7.id()))).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), rang5.id()))).isEqualTo(2);
		assertThat(file.position(file.exiger(clinique.id(), normal.id()))).isEqualTo(3);
	}

	@Test
	@DisplayName("le motif est COPIÉ sur le ticket : le renommer ne réécrit pas l'histoire")
	void leMotifEstCopieSurLeTicket() {
		UUID id = installer("copie");
		MotifPriorite age = motifs.actifs(id).get(0);
		Ticket ticket = creation.creerPourClient(clinique, jour, null,
				Priorisation.motivee(age.rang(), age.libelle()), null);

		motifs.desactiver(id, age.id());

		assertThat(file.exiger(id, ticket.id()).motifPriorite()).isEqualTo("Personne âgée");
		assertThat(motifs.actifs(id)).extracting(MotifPriorite::libelle)
				.doesNotContain("Personne âgée");
	}

	@Test
	@DisplayName("une urgence reste devant tous les prioritaires")
	void lUrgenceResteDevantLesPrioritaires() {
		UUID id = installer("bande");
		MotifPriorite age = motifs.actifs(id).get(0);
		Ticket prioritaire = creation.creerPourClient(clinique, jour, null,
				Priorisation.motivee(age.rang(), age.libelle()), null);

		Ticket urgence = creation.creerUrgence(clinique, jour, null, null);

		assertThat(file.position(urgence)).isEqualTo(1);
		assertThat(file.position(file.exiger(id, prioritaire.id()))).isEqualTo(2);
	}

	@Test
	@DisplayName("aucun réglage ne peut produire un motif au niveau d'une urgence")
	void aucunMotifNAtteintLeRangDUneUrgence() {
		UUID id = installer("plafond-rang");

		// La borne est en base et non dans le code : une clinique qui éditerait
		// ses motifs par un autre chemin ne doit pas pouvoir doubler une
		// urgence vitale.
		assertThatThrownBy(() -> motifs.ajouter(id, "Faux urgent", Priorite.URGENCE, (short) 9))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> motifs.ajouter(id, "Négatif", (short) 0, (short) 9))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("les motifs d'une clinique sont invisibles à une autre")
	void lesMotifsSontCloisonnes() {
		UUID a = creerClinique("motif-alpha", "Alpha", 2, 180);
		UUID b = creerClinique("motif-beta", "Beta", 2, 180);
		ContexteTenant.executer(a, () -> motifs.ajouter(a, "Éleveur en tournée", (short) 4,
				(short) 1));

		assertThat(ContexteTenant.avec(b, () -> motifs.tous(b))).isEmpty();
		assertThat(ContexteTenant.avec(a, () -> motifs.tous(a))).hasSize(1);
	}

	@Test
	@DisplayName("un prioritaire n'envoie AUCUN SMS correctif, contrairement à une urgence")
	void lePrioritaireNEnvoiePasDeCorrectif() {
		UUID id = installer("sans-correctif");
		Ticket attendu = creation.creerDepuisQr(clinique, jour, "0611111111");
		MotifPriorite age = motifs.actifs(id).get(0);

		creation.creerPourClient(clinique, jour, "0622222222",
				Priorisation.motivee(age.rang(), age.libelle()), null);

		// EF-302 ne prévoit le correctif que pour l'urgence : une personne âgée
		// décale la salle de quelques minutes, pas d'une demi-heure. Prévenir
		// tout le monde à chaque priorité coûterait un SMS par ticket prévenu.
		Integer correctifs = proprietaire.queryForObject("""
				SELECT count(*)::int FROM notification
				WHERE tenant_id = ? AND type = 'CORRECTIF'
				""", Integer.class, id);
		assertThat(correctifs).isZero();
		assertThat(attendu).isNotNull();
	}
}
