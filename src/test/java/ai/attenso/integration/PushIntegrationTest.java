package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.notification.ServiceNotification;
import ai.attenso.push.AbonnementPush;
import ai.attenso.push.PushDao;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.ServiceCreationTicket;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.TachePurge;
import ai.attenso.ticket.Ticket;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EF-307 : la bascule sur le push.
 *
 * <p>La clé VAPID est posée pour que {@code PushDao} et la route d'abonnement
 * existent. L'envoi réel, lui, n'est pas testé ici : il exige un opérateur de
 * push joignable. Ce qui est testé, c'est le <em>choix du canal</em>, qui est
 * tout l'enjeu économique de l'exigence.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		// Vraie paire P-256, générée pour ces tests et pour eux seuls : la
		// bibliothèque valide la courbe à la construction, une clé inventée
		// serait refusée. Elle ne protège rien — aucun envoi réel n'a lieu ici.
		"attenso.push.cle-publique=BPf0NDR2hEiA4xBaQoIoCZOh3mjLb5u8231ZH-uzjZm9sOn25K3J9QjJSgn6lqiQeYiZ73uc3TDBHcvtzz5OcQE",
		"attenso.push.cle-privee=kOSijtFkN-UoD-hcNXHYfG6C2NOd6CR1mGfDeMvRaTY",
		"attenso.push.sujet=mailto:test@attenso.invalid",
})
class PushIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	ServiceCreationTicket creation;

	@Autowired
	ServiceFile file;

	@Autowired
	PushDao push;

	@Autowired
	ServiceNotification notifications;

	@Autowired
	TachePurge purge;

	@Autowired
	PlatformTransactionManager transactions;

	/**
	 * {@code demanderPrevenir} est en propagation MANDATORY : elle n'existe
	 * qu'au sein de la transaction d'une transition. L'appeler sous ce modèle
	 * reproduit cette condition au lieu de l'affaiblir.
	 */
	private void dansUneTransaction(Runnable action) {
		new TransactionTemplate(transactions).executeWithoutResult(statut -> action.run());
	}

	private Tenant clinique;

	private LocalDate jour;

	private UUID installer(String slug) {
		UUID id = creerClinique(slug, "Clinique", 2, 180);
		this.clinique = cliniques.parSlug(slug);
		this.jour = file.jourCourant(clinique);
		ContexteTenant.lier(id);
		return id;
	}

	private String canalDe(UUID tenantId, UUID ticketId) {
		return proprietaire.queryForObject("""
				SELECT canal FROM notification WHERE tenant_id = ? AND ticket_id = ?
				ORDER BY cree_le LIMIT 1
				""", String.class, tenantId, ticketId);
	}

	@Test
	@DisplayName("sans abonnement, la notification part en SMS comme avant")
	void sansAbonnementCEstDuSms() {
		UUID id = installer("push-absent");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");

		assertThat(canalDe(id, ticket.id())).isEqualTo("SMS");
	}

	@Test
	@DisplayName("avec un abonnement, la notification bascule sur le push — et ne coûte rien")
	void avecAbonnementCEstDuPush() {
		UUID id = installer("push-present");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");
		push.poser(id, ticket.id(),
				new AbonnementPush("https://push.example/abc", "cle-p256", "cle-auth"));

		// La seconde notification du ticket, elle, voit l'abonnement.
		creation.creerDepuisQr(clinique, jour, "0622222222");
		dansUneTransaction(() -> notifications.demanderPrevenir(clinique,
				file.exiger(id, ticket.id())));

		String canalPush = proprietaire.queryForObject("""
				SELECT canal FROM notification WHERE tenant_id = ? AND ticket_id = ?
				ORDER BY cree_le DESC LIMIT 1
				""", String.class, id, ticket.id());
		assertThat(canalPush).isEqualTo("PUSH");

		java.math.BigDecimal cout = proprietaire.queryForObject("""
				SELECT cout_centimes FROM notification
				WHERE tenant_id = ? AND ticket_id = ? AND canal = 'PUSH' LIMIT 1
				""", java.math.BigDecimal.class, id, ticket.id());
		assertThat(cout).isEqualByComparingTo(java.math.BigDecimal.ZERO);
	}

	@Test
	@DisplayName("un client sans numéro de mobile devient notifiable par le push")
	void leClientSansNumeroDevientNotifiable() {
		UUID id = installer("push-sans-numero");
		Ticket ticket = creation.creerPourClient(clinique, jour, null, null);
		push.poser(id, ticket.id(),
				new AbonnementPush("https://push.example/xyz", "cle-p256", "cle-auth"));

		dansUneTransaction(() -> notifications.demanderPrevenir(clinique,
				file.exiger(id, ticket.id())));

		// Avant le push, ce ticket n'était joignable par AUCUN canal.
		assertThat(canalDe(id, ticket.id())).isEqualTo("PUSH");
	}

	@Test
	@DisplayName("le client s'abonne depuis sa page de suivi, et un seul abonnement survit")
	void unSeulAbonnementParTicket() throws Exception {
		UUID id = installer("push-route");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");

		for (String endpoint : new String[] { "https://push.example/1", "https://push.example/2" }) {
			http.perform(post("/api/suivi/" + ticket.reference() + "/push")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"endpoint\":\"" + endpoint
									+ "\",\"clePublique\":\"p\",\"cleAuth\":\"a\"}"))
					.andExpect(status().isOk());
		}

		// Deux abonnements enverraient deux notifications pour un seul tour.
		Integer combien = proprietaire.queryForObject(
				"SELECT count(*)::int FROM push_abonnement WHERE ticket_id = ?",
				Integer.class, ticket.id());
		assertThat(combien).isEqualTo(1);
		assertThat(ContexteTenant.avec(id, () -> push.pourTicket(id, ticket.id()))
				.orElseThrow().endpoint()).isEqualTo("https://push.example/2");
	}

	@Test
	@DisplayName("l'abonnement est purgé avec le ticket, comme le numéro de mobile")
	void lAbonnementEstPurgeAJPlusUn() {
		UUID id = installer("push-purge");
		Ticket hier = creation.creerDepuisQr(clinique, jour.minusDays(1), "0611111111");
		push.poser(id, hier.id(),
				new AbonnementPush("https://push.example/vieux", "p", "a"));

		purge.purgerTenant(clinique, jour);

		// Un abonnement push est un identifiant stable de navigateur : le
		// garder ferait de ce produit un traceur.
		Integer restants = proprietaire.queryForObject(
				"SELECT count(*)::int FROM push_abonnement WHERE tenant_id = ?",
				Integer.class, id);
		assertThat(restants).isZero();
	}

	@Test
	@DisplayName("les abonnements d'une clinique sont invisibles à une autre")
	void lesAbonnementsSontCloisonnes() {
		UUID a = installer("push-alpha");
		Ticket ticket = creation.creerDepuisQr(clinique, jour, "0611111111");
		push.poser(a, ticket.id(), new AbonnementPush("https://push.example/a", "p", "a"));
		UUID b = creerClinique("push-beta", "Beta", 2, 180);

		assertThat(ContexteTenant.avec(b, () -> push.pourTicket(b, ticket.id()))).isEmpty();
		assertThat(ContexteTenant.avec(a, () -> push.pourTicket(a, ticket.id()))).isPresent();
	}
}
