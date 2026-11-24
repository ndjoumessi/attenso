package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.mesure.MesureDao;
import ai.attenso.mesure.MesureJour;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.Origine;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * L'instrumentation du pilote, contre un vrai PostgreSQL.
 *
 * <p>C'est le chiffre qui décide du projet en semaine 10 : il doit être juste
 * dans la base, pas seulement dans le calcul.
 */
class MesurePiloteIntegrationTest extends SocleIntegration {

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Autowired
	MesureDao mesures;

	private Tenant clinique;

	private LocalDate jour;

	private Tenant installer(String slug) {
		creerClinique(slug, "Clinique du Pilote", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		this.jour = file.jourCourant(clinique);
		ContexteTenant.lier(clinique.id());
		return clinique;
	}

	@Test
	@DisplayName("l'origine du ticket est persistée, sans quoi le taux "
			+ "d'auto-enregistrement n'est pas calculable")
	void lOrigineEstPersistee() {
		installer("origine");

		Ticket seul = creation.creerDepuisQr(clinique, jour, "0611111111");
		Ticket parLAsv = creation.creerPourClient(clinique, jour, "0622222222", null);

		// Relus depuis la base, pas depuis l'objet en mémoire.
		assertThat(file.exiger(clinique.id(), seul.id()).origine())
				.isEqualTo(Origine.COMPTOIR);
		assertThat(file.exiger(clinique.id(), parLAsv.id()).origine())
				.isEqualTo(Origine.ACCUEIL);
	}

	@Test
	@DisplayName("une urgence saisie par l'accueil ne compte pas dans le "
			+ "numérateur du taux")
	void lUrgenceCompteCommeSaisieParLAccueil() {
		installer("urgence-origine");
		creation.creerUrgence(clinique, jour, "0699999999", null);

		MesureJour mesure = mesures.duJour(clinique.id(), jour);

		assertThat(mesure.ticketsComptoir()).isZero();
		assertThat(mesure.ticketsAccueil()).isEqualTo(1);
	}

	@Test
	@DisplayName("un ticket sans numéro est accepté : un client sans téléphone "
			+ "doit exister dans la file")
	void leTicketSansNumeroEstAccepte() {
		installer("sans-numero");

		Ticket ticket = creation.creerPourClient(clinique, jour, null, null);

		assertThat(ticket.telephoneE164()).isNull();
		assertThat(file.position(ticket)).isEqualTo(1);
	}

	@Test
	@DisplayName("la saisie des arrivants est un remplacement, pas un cumul : "
			+ "l'ASV corrige son comptage dans la journée")
	void laSaisieDesArrivantsRemplace() {
		installer("arrivants");
		creation.creerDepuisQr(clinique, jour, "0611111111");

		mesures.poserArrivants(clinique.id(), jour, 10);
		mesures.poserArrivants(clinique.id(), jour, 4);

		MesureJour mesure = mesures.duJour(clinique.id(), jour);
		assertThat(mesure.arrivantsTotal()).isEqualTo(4);
		assertThat(mesure.tauxAutoEnregistrement()).isEqualTo(25);
		assertThat(mesure.zone()).isEqualTo("INVALIDE");
	}

	@Test
	@DisplayName("un jour sans saisie reste dans l'export, avec un taux vide "
			+ "plutôt qu'un zéro")
	void leJourSansSaisieResteDansLExport() {
		installer("export");
		creation.creerDepuisQr(clinique, jour, "0611111111");

		List<MesureJour> periode = mesures.surPeriode(clinique.id(), jour, jour);

		assertThat(periode).hasSize(1);
		assertThat(periode.get(0).arrivantsTotal()).isNull();
		assertThat(periode.get(0).tauxAutoEnregistrement()).isNull();
		assertThat(periode.get(0).ticketsComptoir()).isEqualTo(1);
	}

	@Test
	@DisplayName("le déplacement manuel échange deux rangs sans renuméroter "
			+ "les tickets")
	void leDeplacementEchangeDeuxRangs() {
		installer("deplacement");
		Ticket un = creation.creerDepuisQr(clinique, jour, "0611111111");
		Ticket deux = creation.creerDepuisQr(clinique, jour, "0622222222");

		file.deplacer(clinique, deux.id(), true, null);

		assertThat(file.position(file.exiger(clinique.id(), deux.id()))).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), un.id()))).isEqualTo(2);
		// Le numéro annoncé au client ne bouge jamais : c'est lui qui est
		// affiché au mur et écrit dans le SMS.
		assertThat(file.exiger(clinique.id(), deux.id()).numeroJour()).isEqualTo(2);
		assertThat(file.exiger(clinique.id(), un.id()).numeroJour()).isEqualTo(1);
	}

	@Test
	@DisplayName("déplacer le premier vers le haut ne fait rien, et ne lève pas")
	void leDeplacementEnBordDeFileEstSansEffet() {
		installer("bord-de-file");
		Ticket un = creation.creerDepuisQr(clinique, jour, "0611111111");
		creation.creerDepuisQr(clinique, jour, "0622222222");

		// Une erreur ici bloquerait l'ASV sur un geste sans conséquence.
		file.deplacer(clinique, un.id(), true, null);

		assertThat(file.position(file.exiger(clinique.id(), un.id()))).isEqualTo(1);
	}

	@Test
	@DisplayName("le déplacement ne fait pas franchir la barrière des priorités : "
			+ "une urgence reste devant")
	void leDeplacementNeDepassePasUneUrgence() {
		installer("barriere");
		Ticket normal = creation.creerDepuisQr(clinique, jour, "0611111111");
		Ticket urgence = creation.creerUrgence(clinique, jour, "0699999999", null);

		file.deplacer(clinique, normal.id(), true, null);

		assertThat(file.position(file.exiger(clinique.id(), urgence.id()))).isEqualTo(1);
		assertThat(file.position(file.exiger(clinique.id(), normal.id()))).isEqualTo(2);
	}

	@Test
	@DisplayName("une journée où l'accueil a compté des arrivants SANS qu'aucun "
			+ "ticket soit pris reste visible — c'est le 0 % le plus éloquent "
			+ "du pilote")
	void leJourSansAucunTicketResteVisible() {
		// Le QR code décroché, l'affiche tombée : zéro ticket pour vingt
		// personnes entrées. Une jointure partant des tickets effacerait cette
		// journée de l'export, et avec elle la preuve que le parcours a échoué.
		installer("qr-casse");

		mesures.poserArrivants(clinique.id(), jour, 20);

		MesureJour mesure = mesures.duJour(clinique.id(), jour);
		assertThat(mesure.arrivantsTotal()).isEqualTo(20);
		assertThat(mesure.ticketsTotal()).isZero();
		assertThat(mesure.tauxAutoEnregistrement()).isZero();
		assertThat(mesure.zone()).isEqualTo("INVALIDE");
		assertThat(mesures.surPeriode(clinique.id(), jour, jour)).hasSize(1);
	}

	@Test
	@DisplayName("la saisie du matin survit à l'ouverture de l'écran avant le "
			+ "premier client")
	void laSaisieDuMatinSurvitAUneFileVide() {
		// Sans ça, l'ASV compte, voit son chiffre disparaître au rechargement,
		// et cesse de compter — ce qui rend le pilote injugeable.
		installer("matin");
		mesures.poserArrivants(clinique.id(), jour, 3);

		assertThat(mesures.duJour(clinique.id(), jour).arrivantsTotal()).isEqualTo(3);

		creation.creerDepuisQr(clinique, jour, "0611111111");
		assertThat(mesures.duJour(clinique.id(), jour).arrivantsTotal()).isEqualTo(3);
	}
}
