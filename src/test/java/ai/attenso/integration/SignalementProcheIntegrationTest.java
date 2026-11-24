package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Ticket;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** EF-205 : « je suis à quelques minutes », et ce que ça ne fait PAS. */
@AutoConfigureMockMvc
class SignalementProcheIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	private Tenant clinique;

	private LocalDate jour;

	private UUID installer(String slug) {
		UUID id = creerClinique(slug, "Clinique", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		this.jour = file.jourCourant(clinique);
		ContexteTenant.lier(id);
		return id;
	}

	@Test
	@DisplayName("le signalement NE CHANGE PAS l'ordre de la file")
	void leSignalementNeChangePasLOrdre() throws Exception {
		UUID id = installer("jarrive");
		Ticket premier = creation.creerDepuisQr(clinique, jour, "0611111111");
		Ticket second = creation.creerDepuisQr(clinique, jour, "0622222222");

		http.perform(post("/api/suivi/" + second.reference() + "/jarrive"))
				.andExpect(status().isOk());

		// Lecture dans un contexte explicite : le filtre d'authentification
		// délie le tenant en fin de requête, ce qui est juste en production
		// mais efface la liaison posée par le test sur le même fil.
		ContexteTenant.executer(id, () -> {
			// C'est tout l'enjeu : un bouton qui ferait passer devant donnerait
			// à chacun le moyen de doubler les autres depuis son téléphone.
			assertThat(file.position(file.exiger(id, premier.id()))).isEqualTo(1);
			assertThat(file.position(file.exiger(id, second.id()))).isEqualTo(2);
			assertThat(file.exiger(id, second.id()).priorite()).isZero();
		});
	}

	@Test
	@DisplayName("le signalement est visible sur le tableau de l'accueil")
	void leSignalementEstVisibleSurLeTableau() throws Exception {
		UUID id = installer("visible");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");

		http.perform(post("/api/suivi/" + ticket.reference() + "/jarrive"))
				.andExpect(status().isOk());

		ContexteTenant.executer(id, () -> {
			assertThat(file.exiger(id, ticket.id()).signaleProcheLe()).isNotNull();
			assertThat(file.tableauDuJour(id, jour))
					.anySatisfy(ligne -> assertThat(ligne.signaleProcheLe()).isNotNull());
		});
	}

	@Test
	@DisplayName("aucun SMS ne part : l'information est déjà sur le tableau")
	void aucunSmsNePart() throws Exception {
		UUID id = installer("sans-sms");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");
		Integer avant = proprietaire.queryForObject(
				"SELECT count(*)::int FROM notification WHERE tenant_id = ?", Integer.class, id);

		http.perform(post("/api/suivi/" + ticket.reference() + "/jarrive"))
				.andExpect(status().isOk());

		Integer apres = proprietaire.queryForObject(
				"SELECT count(*)::int FROM notification WHERE tenant_id = ?", Integer.class, id);
		assertThat(apres).isEqualTo(avant);
	}

	@Test
	@DisplayName("re-signaler rafraîchit l'horodatage au lieu de refuser")
	void resignalerRafraichitLHorodatage() throws Exception {
		UUID id = installer("repete");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");

		http.perform(post("/api/suivi/" + ticket.reference() + "/jarrive"));
		var premier = ContexteTenant.avec(id, () -> file.exiger(id, ticket.id())
				.signaleProcheLe());
		Thread.sleep(20);
		http.perform(post("/api/suivi/" + ticket.reference() + "/jarrive"));

		// « Il l'a dit il y a deux minutes » vaut mieux que « il y a une heure ».
		assertThat(ContexteTenant.avec(id, () -> file.exiger(id, ticket.id())
				.signaleProcheLe())).isAfter(premier);
	}

	@Test
	@DisplayName("une référence inconnue est refusée comme partout ailleurs")
	void uneReferenceInconnueEstRefusee() throws Exception {
		installer("inconnue");

		http.perform(post("/api/suivi/referenceFausse/jarrive"))
				.andExpect(status().isNotFound());
	}
}
