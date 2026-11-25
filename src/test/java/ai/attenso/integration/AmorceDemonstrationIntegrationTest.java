package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.attenso.agenda.AgendaDao;
import ai.attenso.amorce.AmorceClinique;
import ai.attenso.amorce.ProprietesAmorce;
import ai.attenso.carnet.CarnetDao;
import ai.attenso.mesure.MesureDao;
import ai.attenso.priorite.MotifDao;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.TenantRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Sans données, huit vues sur neuf sont vides le jour de la démonstration. Mais
 * des données invraisemblables sont pires que pas de données : un vétérinaire
 * reconnaît tout de suite une file qui n'a pas de sens. Ces tests tiennent les
 * deux bouts.
 *
 * <p>Construit à la main comme {@link AmorceMotifsDemoIntegrationTest}, avec le
 * même contexte Spring, plutôt que par une propriété d'activation qui en
 * démarrerait un de plus.
 */
class AmorceDemonstrationIntegrationTest extends SocleIntegration {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	// Mars 2027, loin de la date réelle : un LocalDate.now() nu ne coïncide jamais
	// avec ces instants, donc le rougit tous les jours.

	/** Mercredi 10 mars 2027, 11 h à Paris. */
	private static final Instant MI_JOURNEE = Instant.parse("2027-03-10T10:00:00Z");

	/** Mercredi 10 mars 2027, 8 h 30 à Paris : avant l'ouverture. */
	private static final Instant AVANT_OUVERTURE = Instant.parse("2027-03-10T07:30:00Z");

	/** Mercredi 10 mars 2027, 13 h à Paris : la pause de midi. */
	private static final Instant PAUSE_DE_MIDI = Instant.parse("2027-03-10T12:00:00Z");

	/**
	 * 23 h 30 UTC : il est déjà 0 h 30 à Paris, le mercredi 10. Un calcul de date
	 * en UTC croit encore au mardi 9, et « hier » glisse d'un jour.
	 */
	private static final Instant APRES_MINUIT_A_PARIS = Instant.parse("2027-03-09T23:30:00Z");

	/** Samedi 13 mars 2027 : le lendemain est un dimanche, clinique fermée. */
	private static final Instant SAMEDI = Instant.parse("2027-03-13T10:00:00Z");

	/** Lundi 15 mars 2027 : la veille est un dimanche, le dernier jour ouvré un vendredi. */
	private static final Instant LUNDI = Instant.parse("2027-03-15T10:00:00Z");

	@Autowired
	TenantRepository tenants;

	@Autowired
	GenerateurReference jetons;

	@Autowired
	MotifDao motifs;

	@Autowired
	AgendaDao agenda;

	@Autowired
	CarnetDao carnets;

	@Autowired
	MesureDao mesure;

	private AmorceClinique amorce(Instant instant, boolean active) {
		ProprietesAmorce proprietes = new ProprietesAmorce(active, "demo",
				"Clinique de démonstration", "accueil@demo.attenso.test",
				"mdp-de-demonstration-de-test", null, null, null, false);
		return new AmorceClinique(proprietes, tenants, applicatif, motsDePasse, jetons, motifs,
				agenda, carnets, Clock.fixed(instant, PARIS));
	}

	private void installer(Instant instant) {
		amorce(instant, true).run(new DefaultApplicationArguments(new String[0]));
	}

	private UUID clinique() {
		return proprietaire.queryForObject("SELECT id FROM tenant WHERE slug = 'demo'",
				UUID.class);
	}

	private long compter(String table) {
		Long total = proprietaire.queryForObject("SELECT count(*) FROM " + table, Long.class);
		return total == null ? 0 : total;
	}

	@Test
	@DisplayName("sème une file, des rendez-vous et des départs d'hier")
	void semeUnJeuDeDemonstration() {
		installer(MI_JOURNEE);

		// Sans données, huit vues sur neuf sont vides le jour de la démonstration.
		assertThat(compter("ticket")).isGreaterThanOrEqualTo(4);
		assertThat(compter("rendez_vous")).isGreaterThanOrEqualTo(2);
		assertThat(compter("animal")).isGreaterThanOrEqualTo(1);
		assertThat(compter("vaccination")).isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("la file en cours est dans un état que le produit peut atteindre")
	void fileCoherenteAvecLeSeuilDePrevenance() {
		installer(MI_JOURNEE);

		int seuil = proprietaire.queryForObject(
				"SELECT seuil_prevenir FROM tenant WHERE slug = 'demo'", Integer.class);
		List<String> statuts = proprietaire.queryForList("""
				SELECT statut FROM ticket
				WHERE jour = ? AND statut IN ('EN_ATTENTE','PREVENU','APPELE','ABSENT')
				ORDER BY priorite DESC, ordre ASC
				""", String.class, Date.valueOf(LocalDate.of(2027, 3, 10)));

		// Les trois états que montrent les vues sont présents...
		assertThat(statuts).contains("EN_ATTENTE", "PREVENU", "APPELE");
		// ... et placés comme la machine à états les place : sous le seuil, on est
		// appelé ou prévenu ; au-delà, on attend. Un EN_ATTENTE en tête de file est
		// un état que ServiceFile.reevaluer ne laisserait jamais subsister.
		assertThat(statuts.subList(0, seuil)).doesNotContain("EN_ATTENTE");
		assertThat(statuts.subList(seuil, statuts.size())).containsOnly("EN_ATTENTE");
		assertThat(statuts.get(0)).isEqualTo("APPELE");
	}

	@Test
	@DisplayName("la chronologie d'un ticket se tient : arrivée, appel, consultation de 10 à 30 min")
	void chronologieVraisemblable() {
		installer(MI_JOURNEE);

		List<Map<String, Object>> tickets = proprietaire.queryForList("""
				SELECT statut, cree_le, appele_le, grace_expire_le, consultation_le, clos_le,
				       extract(epoch FROM (clos_le - consultation_le)) / 60 AS minutes
				FROM ticket
				""");

		assertThat(tickets).isNotEmpty();
		for (Map<String, Object> t : tickets) {
			Instant cree = instant(t, "cree_le");
			assertThat(cree).isBeforeOrEqualTo(MI_JOURNEE);
			if (t.get("appele_le") != null) {
				assertThat(instant(t, "appele_le")).isAfterOrEqualTo(cree);
				assertThat(instant(t, "grace_expire_le")).isAfter(instant(t, "appele_le"));
			}
			if (t.get("consultation_le") != null) {
				// Dans le produit, on n'entre en consultation qu'après avoir été appelé :
				// sans appele_le, le tableau de bord ne verrait aucun retard.
				assertThat(t.get("appele_le")).as("appele_le d'un ticket en consultation")
						.isNotNull();
				assertThat(instant(t, "appele_le")).isBeforeOrEqualTo(instant(t, "consultation_le"));
			}
			if ("TERMINE".equals(t.get("statut"))) {
				assertThat(((Number) t.get("minutes")).doubleValue()).isBetween(10.0, 30.0);
			}
		}
	}

	@Test
	@DisplayName("la grâce des tickets appelés est celle de la clinique")
	void graceDeLaClinique() {
		installer(MI_JOURNEE);

		Long ecarts = proprietaire.queryForObject("""
				SELECT count(*) FROM ticket t JOIN tenant c ON c.id = t.tenant_id
				WHERE t.appele_le IS NOT NULL
				  AND t.grace_expire_le <> t.appele_le + make_interval(secs => c.delai_grace_secondes)
				""", Long.class);

		assertThat(ecarts).isZero();
		assertThat(compter("ticket")).isPositive();
	}

	@Test
	@DisplayName("les carnets sont ceux d'un vétérinaire : sigle complet, rage triennale dite")
	void carnetsVraisemblables() {
		installer(MI_JOURNEE);

		assertThat(proprietaire.queryForList("SELECT vaccin FROM vaccination", String.class))
				.anyMatch(v -> v.startsWith("CHPPi") && v.contains("parainfluenza"));
		assertThat(proprietaire.queryForObject(
				"SELECT note FROM vaccination WHERE vaccin = 'Rage'", String.class))
				.contains("triennal");
	}

	@Test
	@DisplayName("personne ne renonce devant une salle vide : une consultation couvre chaque départ")
	void chaqueDepartAUneConsultationEnCours() {
		installer(MI_JOURNEE);

		Long sansConsultation = proprietaire.queryForObject("""
				SELECT count(*) FROM ticket p
				WHERE p.statut = 'PARTI' AND NOT EXISTS (
				  SELECT 1 FROM ticket c
				  WHERE c.consultation_le IS NOT NULL
				    AND c.consultation_le <= p.clos_le
				    AND (c.clos_le IS NULL OR c.clos_le >= p.clos_le))
				""", Long.class);

		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM ticket WHERE statut = 'PARTI'", Long.class)).isEqualTo(2);
		assertThat(sansConsultation).isZero();
	}

	@Test
	@DisplayName("aucun numéro de téléphone réel ou en cours : aucun SMS ne peut partir")
	void aucunNumeroPourAucunSms() {
		installer(MI_JOURNEE);

		assertThat(proprietaire.queryForObject("""
				SELECT count(*) FROM ticket
				WHERE telephone_e164 IS NOT NULL AND statut NOT IN ('TERMINE','PARTI')
				""", Long.class)).isZero();
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM rendez_vous WHERE telephone_e164 IS NOT NULL",
				Long.class)).isZero();
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM animal WHERE telephone_e164 IS NOT NULL",
				Long.class)).isZero();
		// S'il en reste un, il est dans la plage que l'ARCEP réserve à la fiction.
		assertThat(proprietaire.queryForObject("""
				SELECT count(*) FROM ticket
				WHERE telephone_e164 IS NOT NULL AND telephone_e164 NOT LIKE '+3363998____'
				""", Long.class)).isZero();
	}

	@Test
	@DisplayName("avant l'ouverture comme pendant la pause, la file reste dans les horaires")
	void laFileRespecteLesHoraires() {
		for (Instant moment : List.of(AVANT_OUVERTURE, PAUSE_DE_MIDI, MI_JOURNEE)) {
			proprietaire.execute("TRUNCATE ticket, utilisateur, tenant CASCADE");
			installer(moment);

			List<Map<String, Object>> tickets = proprietaire.queryForList("""
					SELECT cree_le, appele_le, consultation_le, clos_le FROM ticket WHERE jour = ?
					""", Date.valueOf(LocalDate.of(2027, 3, 10)));
			assertThat(tickets).as("file installée à %s", moment).isNotEmpty();
			for (Map<String, Object> t : tickets) {
				for (String colonne : List.of("cree_le", "appele_le", "consultation_le",
						"clos_le")) {
					if (t.get(colonne) == null) {
						continue;
					}
					java.time.LocalTime heure = instant(t, colonne).atZone(PARIS).toLocalTime();
					assertThat(heure).as("%s installée à %s", colonne, moment)
							.isBetween(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(12, 0));
				}
			}
		}
	}

	@Test
	@DisplayName("un lundi, la veille est le vendredi : jamais un dimanche")
	void laVeilleEstUnJourOuvre() {
		installer(LUNDI);

		LocalDate vendredi = LocalDate.of(2027, 3, 12);
		UUID tenantId = clinique();
		assertThat(ContexteTenant.avec(tenantId, () -> mesure.duJour(tenantId, vendredi))
				.departs()).isEqualTo(2);
		assertThat(compterTicketsLe(LocalDate.of(2027, 3, 14))).isZero();
	}

	private static Instant instant(Map<String, Object> ligne, String colonne) {
		return ((java.sql.Timestamp) ligne.get(colonne)).toInstant();
	}

	@Test
	@DisplayName("le passage prioritaire porte un motif que la clinique connaît")
	void motifDuTicketPrioritaire() {
		installer(MI_JOURNEE);

		List<String> motifsPosesSurLesTickets = proprietaire.queryForList(
				"SELECT DISTINCT motif_priorite FROM ticket WHERE motif_priorite IS NOT NULL",
				String.class);

		assertThat(motifsPosesSurLesTickets).isNotEmpty();
		List<String> motifsDeLaClinique = proprietaire.queryForList(
				"SELECT libelle FROM motif_priorite WHERE tenant_id = ?", String.class,
				clinique());
		assertThat(motifsDeLaClinique).containsAll(motifsPosesSurLesTickets);
	}

	@Test
	@DisplayName("la priorité est graduée dans la file : 7 devant 5 devant la file normale")
	void prioriteGraduee() {
		installer(MI_JOURNEE);

		List<Short> rangs = proprietaire.queryForList("""
				SELECT priorite FROM ticket
				WHERE jour = ? AND statut IN ('EN_ATTENTE','PREVENU','APPELE','ABSENT')
				ORDER BY priorite DESC, ordre ASC
				""", Short.class, Date.valueOf(LocalDate.of(2027, 3, 10)));

		assertThat(rangs).startsWith((short) 7, (short) 5).contains((short) 0);
	}

	@Test
	@DisplayName("les départs d'hier se comptent depuis l'horloge de Paris, pas depuis UTC")
	void departsDHierSuiventLHorlogeDeLaClinique() {
		installer(APRES_MINUIT_A_PARIS);

		UUID tenantId = clinique();
		// À 0 h 30 le 25 à Paris, « hier » est le 24 — et non le 23, où un calcul
		// en UTC placerait la veille.
		LocalDate hier = LocalDate.of(2027, 3, 9);
		assertThat(ContexteTenant.avec(tenantId, () -> mesure.duJour(tenantId, hier)).departs())
				.isEqualTo(2);
		assertThat(compterTicketsLe(LocalDate.of(2027, 3, 10))).isPositive();
		assertThat(compterTicketsLe(LocalDate.of(2027, 3, 8))).isZero();
		// La mesure a aussi son dénominateur, et le taux tombe franchement du côté
		// valide (8 tickets de comptoir pour 13 arrivants), pas en zone grise.
		var mesureDeHier = ContexteTenant.avec(tenantId, () -> mesure.duJour(tenantId, hier));
		assertThat(mesureDeHier.ticketsComptoir()).isEqualTo(8);
		assertThat(mesureDeHier.ticketsAccueil()).isEqualTo(2);
		assertThat(mesureDeHier.zone()).isEqualTo("VALIDE");
	}

	private long compterTicketsLe(LocalDate jour) {
		Long total = proprietaire.queryForObject("SELECT count(*) FROM ticket WHERE jour = ?",
				Long.class, Date.valueOf(jour));
		return total == null ? 0 : total;
	}

	@Test
	@DisplayName("les rendez-vous sont à venir, jamais un dimanche, et occupent leur créneau")
	void rendezVousAVenirUnJourOuvre() {
		installer(SAMEDI);

		List<Map<String, Object>> rendezVous = proprietaire.queryForList("""
				SELECT r.jour, c.prises FROM rendez_vous r JOIN creneau c ON c.id = r.creneau_id
				""");
		assertThat(rendezVous).hasSizeGreaterThanOrEqualTo(2);
		for (Map<String, Object> rdv : rendezVous) {
			LocalDate jour = ((Date) rdv.get("jour")).toLocalDate();
			assertThat(jour).isAfter(LocalDate.of(2027, 3, 13));
			assertThat(jour.getDayOfWeek()).isNotEqualTo(DayOfWeek.SUNDAY);
			assertThat(((Number) rdv.get("prises")).intValue()).isGreaterThanOrEqualTo(1);
		}
	}

	@Test
	@DisplayName("sans demande de démonstration, la clinique est créée mais reste vide")
	void sansDemandeRienNEstSeme() {
		amorce(MI_JOURNEE, false).run(new DefaultApplicationArguments(new String[0]));

		assertThat(compter("tenant")).isEqualTo(1);
		assertThat(compter("ticket")).isZero();
		assertThat(compter("rendez_vous")).isZero();
		assertThat(compter("animal")).isZero();
	}

	@Test
	@DisplayName("ne sème pas deux fois : la garde par tickets, seule défense à une autre date")
	void semisDirectDeuxFoisNeDoublePas() {
		AmorceClinique amorce = amorce(MI_JOURNEE, true);
		amorce.run(new DefaultApplicationArguments(new String[0]));
		long tickets = compter("ticket");
		long rendezVous = compter("rendez_vous");
		long animaux = compter("animal");
		assertThat(tickets).isPositive();

		// Appel DIRECT, qui court-circuite le garde-fou du slug de run(), sous une
		// horloge décalée d'une semaine : aucun jour, aucun numéro de ticket ni
		// créneau ne se recoupe, donc aucune contrainte d'unicité ne peut venir
		// au secours de la garde. Si elle manque, les compteurs doublent.
		UUID tenantId = clinique();
		amorce(MI_JOURNEE.plus(java.time.Duration.ofDays(7)), true)
				.semerLaDemonstration(tenantId);
		assertThat(compter("ticket")).isEqualTo(tickets);
		assertThat(compter("rendez_vous")).isEqualTo(rendezVous);
		assertThat(compter("animal")).isEqualTo(animaux);

		// Et à la même date : la base refuserait, mais le semis ne doit même pas essayer.
		assertThatCode(() -> amorce.semerLaDemonstration(tenantId)).doesNotThrowAnyException();
		assertThat(compter("ticket")).isEqualTo(tickets);
	}

	@Test
	@DisplayName("relancer run ne touche à rien : le garde-fou du slug, pas la garde par tickets")
	void relancerRunNeTouchePasALaFile() {
		AmorceClinique amorce = amorce(MI_JOURNEE, true);
		amorce.run(new DefaultApplicationArguments(new String[0]));
		long avant = compter("ticket");

		amorce.run(new DefaultApplicationArguments(new String[0]));

		// Ce test garde le garde-fou du slug de run(), et lui seul : il resterait
		// vert si la garde par tickets de semerLaDemonstration disparaissait.
		assertThat(compter("ticket")).isEqualTo(avant);
	}
}
