package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.carnet.Animal;
import ai.attenso.carnet.CarnetDao;
import ai.attenso.carnet.ServiceCarnet;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** EF-906 : le carnet, son QR révocable et ses rappels de vaccins. */
@AutoConfigureMockMvc
class CarnetIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCarnet carnets;

	@Autowired
	CarnetDao dao;

	private Tenant clinique;

	private UUID installer(String slug) {
		UUID id = creerClinique(slug, "Clinique", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		ContexteTenant.lier(id);
		return id;
	}

	@Test
	@DisplayName("le carnet s'ouvre par son jeton et ne rend que ce qu'un carnet papier porte")
	void leCarnetNeRendQueCeQuUnCarnetPapierPorte() throws Exception {
		UUID id = installer("carnet");
		Animal chat = carnets.creer(id, "Mistigri", "Chat", "0611111111", true);
		dao.ajouterVaccination(id, chat.id(), "Typhus", LocalDate.of(2026, 3, 1),
				LocalDate.of(2027, 3, 1), null);

		String corps = http.perform(get("/api/carnet/" + chat.jetonCarnet()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(corps).contains("Mistigri").contains("Typhus");
		// Le QR est collé sur une médaille, et une médaille se perd : pas de
		// numéro de téléphone, pas d'identifiant interne.
		assertThat(corps).doesNotContain("0611111111").doesNotContain("+33")
				.doesNotContain(chat.id().toString());
	}

	@Test
	@DisplayName("révoquer le carnet invalide l'ancien QR et en donne un autre")
	void revoquerInvalideLAncienQr() throws Exception {
		UUID id = installer("carnet-revoque");
		Animal chien = carnets.creer(id, "Rex", "Chien", null, true);
		String ancien = chien.jetonCarnet();

		Animal apres = carnets.revoquerLeCarnet(id, chien.id(), true);

		assertThat(apres.jetonCarnet()).isNotNull().isNotEqualTo(ancien);
		// Un QR se photographie : l'ancien ne doit plus rien ouvrir.
		http.perform(get("/api/carnet/" + ancien)).andExpect(status().isNotFound());
		http.perform(get("/api/carnet/" + apres.jetonCarnet())).andExpect(status().isOk());
	}

	@Test
	@DisplayName("fermer le carnet ne laisse aucun accès par QR")
	void fermerLeCarnetNeLaisseAucunAcces() throws Exception {
		UUID id = installer("carnet-ferme");
		Animal chien = carnets.creer(id, "Rex", "Chien", null, true);
		String ancien = chien.jetonCarnet();

		Animal apres = carnets.revoquerLeCarnet(id, chien.id(), false);

		// C'est ce que demande un propriétaire qui ne veut plus d'accès par QR
		// du tout, et le lui refuser viderait « révocable » de son sens.
		assertThat(apres.jetonCarnet()).isNull();
		assertThat(apres.carnetOuvert()).isFalse();
		http.perform(get("/api/carnet/" + ancien)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("un animal créé sans carnet n'a pas de jeton du tout")
	void sansCarnetPasDeJeton() {
		UUID id = installer("carnet-absent");

		Animal chat = carnets.creer(id, "Felix", "Chat", null, false);

		assertThat(chat.jetonCarnet()).isNull();
		assertThat(chat.carnetOuvert()).isFalse();
	}

	@Test
	@DisplayName("le rappel de vaccin part une fois, sans nommer l'animal ni le vaccin")
	void leRappelDeVaccinNeNommeRien() {
		UUID id = installer("carnet-rappel");
		Animal chat = carnets.creer(id, "Mistigri", "Chat", "0611111111", true);
		// Échéance dans deux semaines : dans la fenêtre de trois semaines.
		dao.ajouterVaccination(id, chat.id(), "Leucose", aujourdhui().minusYears(1),
				aujourdhui().plusDays(14), null);

		int premier = carnets.rappelerPour(clinique);
		int second = carnets.rappelerPour(clinique);

		assertThat(premier).isEqualTo(1);
		assertThat(second).as("deux balayages ne doivent pas doubler le SMS").isZero();
		String corps = proprietaire.queryForObject("""
				SELECT corps FROM notification WHERE tenant_id = ? AND type = 'RAPPEL_VACCIN'
				""", String.class, id);
		// Le SMS s'affiche sur un écran verrouillé : le détail reste derrière
		// le lien, qui est lui-même révocable.
		assertThat(corps).doesNotContain("Mistigri").doesNotContain("Leucose");
		assertThat(corps).contains("/carnet/");
	}

	@Test
	@DisplayName("une vaccination sans date de rappel ne déclenche jamais rien")
	void sansDateDeRappelRienNePart() {
		UUID id = installer("carnet-sans-rappel");
		Animal chat = carnets.creer(id, "Felix", "Chat", "0611111111", true);
		dao.ajouterVaccination(id, chat.id(), "Primo-injection", aujourdhui(), null, null);

		// L'absence de date n'est pas « en retard » : la confondre enverrait
		// un SMS payant à chaque balayage pour un rappel qui n'existe pas.
		assertThat(carnets.rappelerPour(clinique)).isZero();
	}

	@Test
	@DisplayName("un rappel lointain n'est pas envoyé trop tôt")
	void unRappelLointainAttend() {
		UUID id = installer("carnet-lointain");
		Animal chat = carnets.creer(id, "Felix", "Chat", "0611111111", true);
		dao.ajouterVaccination(id, chat.id(), "Typhus", aujourdhui(),
				aujourdhui().plusMonths(6), null);

		assertThat(carnets.rappelerPour(clinique)).isZero();
	}

	@Test
	@DisplayName("un rappel déjà dépassé est quand même envoyé")
	void unRappelDepasseEstQuandMemeEnvoye() {
		UUID id = installer("carnet-retard");
		Animal chat = carnets.creer(id, "Felix", "Chat", "0611111111", true);
		dao.ajouterVaccination(id, chat.id(), "Typhus", aujourdhui().minusYears(2),
				aujourdhui().minusDays(10), null);

		// Une journée où la tâche n'a pas tourné ne doit pas faire sauter
		// définitivement le rappel.
		assertThat(carnets.rappelerPour(clinique)).isEqualTo(1);
	}

	@Test
	@DisplayName("le carnet d'une clinique est invisible à une autre")
	void lesCarnetsSontCloisonnes() {
		UUID a = installer("carnet-alpha");
		Animal chat = carnets.creer(a, "Mistigri", "Chat", null, true);
		UUID b = creerClinique("carnet-beta", "Beta", 2, 180);

		assertThat(ContexteTenant.avec(b, () -> dao.parJeton(b, chat.jetonCarnet())))
				.isEmpty();
		assertThat(ContexteTenant.avec(b, () -> dao.tous(b))).isEmpty();
	}
}
