package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.attenso.agenda.AgendaDao;
import ai.attenso.agenda.CreneauComplet;
import ai.attenso.agenda.CreneauPasse;
import ai.attenso.agenda.RendezVous;
import ai.attenso.agenda.ServiceAgenda;
import ai.attenso.agenda.TacheRappelRendezVous;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** EF-905 : l'agenda, son quota et son rappel J-1. */
class AgendaIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceAgenda agenda;

	@Autowired
	AgendaDao dao;

	@Autowired
	TacheRappelRendezVous rappels;

	private Tenant clinique;

	private UUID installer(String slug) {
		UUID id = creerClinique(slug, "Clinique", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		ContexteTenant.lier(id);
		return id;
	}

	/** Une plage sur le jour voulu, pour que des créneaux existent à coup sûr. */
	private LocalDate ouvrirPour(UUID id, LocalDate jour, short places) {
		dao.ajouterPlage(id, jour.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0),
				(short) 30, places);
		agenda.genererPour(clinique);
		return jour;
	}

	@Test
	@DisplayName("une plage hebdomadaire produit des créneaux à la bonne durée")
	void unePlageProduitDesCreneaux() {
		UUID id = installer("agenda");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(id, lundi, (short) 1);

		List<ai.attenso.agenda.Creneau> creneaux = dao.creneaux(id, lundi, lundi);

		// 9h00-10h00 par tranches de 30 min = deux créneaux, pas trois : le
		// dernier doit TENIR dans la plage, pas la déborder.
		assertThat(creneaux).hasSize(2);
		assertThat(creneaux).extracting(c -> c.heure().toString())
				.containsExactly("09:00", "09:30");
	}

	@Test
	@DisplayName("regénérer ne duplique pas les créneaux ni n'efface les réservations")
	void regenererNeDupliquePas() {
		UUID id = installer("agenda-regen");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(id, lundi, (short) 2);
		agenda.reserver(clinique, lundi, LocalTime.of(9, 0), "0611111111", null);

		agenda.genererPour(clinique);

		assertThat(dao.creneaux(id, lundi, lundi)).hasSize(2);
		assertThat(dao.creneaux(id, lundi, lundi).get(0).prises()).isEqualTo((short) 1);
	}

	@Test
	@DisplayName("le quota tient sous réservations simultanées")
	void leQuotaTientSousConcurrence() throws Exception {
		UUID id = installer("agenda-quota");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(id, lundi, (short) 3);

		// Compter puis insérer laisserait passer ensemble toutes les demandes
		// ayant lu le compteur avant qu'aucune ne l'incrémente : deux
		// personnes auraient le même créneau dans une clinique à une salle.
		ExecutorService fils = Executors.newFixedThreadPool(8);
		List<Callable<Boolean>> demandes = IntStream.range(0, 20)
				.<Callable<Boolean>>mapToObj(i -> () -> ContexteTenant.avec(id, () -> {
					try {
						agenda.reserver(clinique, lundi, LocalTime.of(9, 0), null, null);
						return true;
					}
					catch (CreneauComplet complet) {
						return false;
					}
				}))
				.toList();
		List<Future<Boolean>> resultats = fils.invokeAll(demandes);
		fils.shutdown();

		long acceptees = resultats.stream().filter(f -> {
			try {
				return f.get();
			}
			catch (Exception e) {
				return false;
			}
		}).count();
		assertThat(acceptees).as("exactement le quota, ni plus ni moins").isEqualTo(3);
		assertThat(dao.creneaux(id, lundi, lundi).get(0).prises()).isEqualTo((short) 3);
	}

	@Test
	@DisplayName("annuler rend la place, sinon l'agenda se vide tout seul")
	void annulerRendLaPlace() {
		UUID id = installer("agenda-annule");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(id, lundi, (short) 1);
		RendezVous rdv = agenda.reserver(clinique, lundi, LocalTime.of(9, 0), null, null);
		assertThatThrownBy(() -> agenda.reserver(clinique, lundi, LocalTime.of(9, 0), null,
				null)).isInstanceOf(CreneauComplet.class);

		agenda.annuler(clinique, rdv.reference());

		assertThat(dao.creneaux(id, lundi, lundi).get(0).prises()).isZero();
		// La place est réellement reprenable, pas seulement décomptée.
		assertThat(agenda.reserver(clinique, lundi, LocalTime.of(9, 0), null, null))
				.isNotNull();
	}

	@Test
	@DisplayName("annuler deux fois ne rend pas deux places")
	void annulerDeuxFoisNeRendPasDeuxPlaces() {
		UUID id = installer("agenda-double-annule");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(id, lundi, (short) 2);
		RendezVous rdv = agenda.reserver(clinique, lundi, LocalTime.of(9, 0), null, null);

		agenda.annuler(clinique, rdv.reference());
		agenda.annuler(clinique, rdv.reference());

		// Sans le garde « statut = CONFIRME », le compteur partirait en
		// négatif et le créneau accepterait plus que son quota.
		assertThat(dao.creneaux(id, lundi, lundi).get(0).prises()).isZero();
	}

	@Test
	@DisplayName("un créneau déjà passé n'est plus proposé")
	void unCreneauPasseNEstPlusPropose() {
		UUID id = installer("agenda-passe");
		LocalDate hier = aujourdhui().minusDays(1);
		dao.materialiser(id, hier, LocalTime.of(9, 0), (short) 5);

		assertThat(agenda.disponibles(clinique, hier, hier.plusDays(30))).isEmpty();
	}

	@Test
	@DisplayName("réserver un créneau passé est refusé, et ne prend aucune place")
	void reserverUnCreneauPasseEstRefuse() {
		UUID id = installer("agenda-passe-api");
		LocalDate hier = aujourdhui().minusDays(1);
		dao.materialiser(id, hier, LocalTime.of(9, 0), (short) 5);

		// Le test voisin ne couvre que l'AFFICHAGE. Ne pas le rejouer ici
		// laisserait le chemin d'écriture sans garde : il suffisait de poster
		// directement sur l'API pour réserver une heure révolue.
		assertThatThrownBy(() -> agenda.reserver(clinique, hier, LocalTime.of(9, 0),
				"0612345678", "vaccin")).isInstanceOf(CreneauPasse.class);

		assertThat(dao.creneaux(id, hier, hier).get(0).prises()).isZero();
	}

	@Test
	@DisplayName("le rappel J-1 part une seule fois, même si la tâche repasse")
	void leRappelPartUneSeuleFois() {
		UUID id = installer("agenda-rappel");
		LocalDate demain = aujourdhui().plusDays(1);
		ouvrirPour(id, demain, (short) 2);
		agenda.reserver(clinique, demain, LocalTime.of(9, 0), "0611111111", null);

		int premier = rappels.rappelerPour(clinique);
		int second = rappels.rappelerPour(clinique);

		assertThat(premier).isEqualTo(1);
		assertThat(second).as("deux instances ne doivent pas doubler le SMS").isZero();
		Integer envoyes = proprietaire.queryForObject("""
				SELECT count(*)::int FROM notification
				WHERE tenant_id = ? AND type = 'RAPPEL_RDV'
				""", Integer.class, id);
		assertThat(envoyes).isEqualTo(1);
	}

	@Test
	@DisplayName("un rendez-vous sans numéro ne déclenche aucun rappel")
	void sansNumeroPasDeRappel() {
		UUID id = installer("agenda-sans-numero");
		LocalDate demain = aujourdhui().plusDays(1);
		ouvrirPour(id, demain, (short) 2);
		agenda.reserver(clinique, demain, LocalTime.of(9, 0), null, null);

		assertThat(rappels.rappelerPour(clinique)).isZero();
	}

	@Test
	@DisplayName("un rendez-vous annulé ne reçoit pas son rappel")
	void unRendezVousAnnuleNeRecoitPasDeRappel() {
		UUID id = installer("agenda-annule-rappel");
		LocalDate demain = aujourdhui().plusDays(1);
		ouvrirPour(id, demain, (short) 2);
		RendezVous rdv = agenda.reserver(clinique, demain, LocalTime.of(9, 0), "0611111111",
				null);
		agenda.annuler(clinique, rdv.reference());

		assertThat(rappels.rappelerPour(clinique)).isZero();
	}

	@Test
	@DisplayName("les créneaux d'une clinique sont invisibles à une autre")
	void lesCreneauxSontCloisonnes() {
		UUID a = installer("agenda-alpha");
		LocalDate lundi = aujourdhui().with(DayOfWeek.MONDAY).plusWeeks(1);
		ouvrirPour(a, lundi, (short) 2);
		UUID b = creerClinique("agenda-beta", "Beta", 2, 180);

		assertThat(ContexteTenant.avec(b, () -> dao.creneaux(b, lundi, lundi))).isEmpty();
	}
}
