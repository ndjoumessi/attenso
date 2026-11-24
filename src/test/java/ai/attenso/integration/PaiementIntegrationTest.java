package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.attenso.facturation.Palier;
import ai.attenso.inscription.DemandeInscription;
import ai.attenso.inscription.ReponseInscription;
import ai.attenso.inscription.ServiceInscription;
import ai.attenso.paiement.PasserelleFausse;
import ai.attenso.paiement.SouscriptionIndisponible;
import ai.attenso.tenant.ContexteTenant;
import com.stripe.net.Webhook;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La souscription payante, de l'inscription au webhook.
 *
 * <p>Deux choses sont vérifiées pour de bon et non simulées : la
 * <strong>signature</strong> des webhooks, seul rempart d'une route publique,
 * et le fait qu'un impayé <strong>ne coupe rien</strong>.
 */
@AutoConfigureMockMvc
class PaiementIntegrationTest extends SocleIntegration {

	@Autowired
	MockMvc http;

	@Autowired
	tools.jackson.databind.ObjectMapper json;

	@Autowired
	ServiceInscription inscriptions;

	@Autowired
	PasserelleFausse passerelle;

	@BeforeEach
	void reparerLaPasserelle() {
		passerelle.reparer();
	}

	private ReponseInscription inscrire(String nom) {
		return inscriptions.inscrire(new DemandeInscription(nom,
				"vet@" + nom.toLowerCase().replaceAll("[^a-z]", "") + ".fr",
				"un-mot-de-passe-assez-long", Palier.COMPTOIR.name(), false, true));
	}

	private UUID tenantDe(String slug) {
		return proprietaire.queryForObject("SELECT id FROM tenant WHERE slug = ?",
				UUID.class, slug);
	}

	/**
	 * Le dernier abonnement, résilié compris : une résiliation POSE une date de
	 * fin, donc filtrer sur « fin IS NULL » ferait disparaître la ligne qu'on
	 * veut justement inspecter.
	 */
	private String statut(UUID tenantId) {
		return proprietaire.queryForObject("""
				SELECT statut FROM abonnement WHERE tenant_id = ?
				ORDER BY debut DESC, cree_le DESC LIMIT 1
				""", String.class, tenantId);
	}

	private boolean actif(UUID tenantId) {
		return Boolean.TRUE.equals(proprietaire.queryForObject(
				"SELECT actif FROM tenant WHERE id = ?", Boolean.class, tenantId));
	}

	/** Un webhook signé pour de vrai, avec le secret des tests. */
	private void envoyer(String charge) throws Exception {
		envoyer(charge, signer(charge)).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions envoyer(String charge,
			String signature) throws Exception {
		return http.perform(post("/api/paiement/webhook")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", signature)
				.content(charge));
	}

	private static String signer(String charge) throws Exception {
		long horodatage = System.currentTimeMillis() / 1000;
		String signature = Webhook.Util.computeHmacSha256(SECRET_WEBHOOK,
				horodatage + "." + charge);
		return "t=" + horodatage + ",v1=" + signature;
	}

	private static String evenement(String id, String type, String objet) {
		return """
				{"id":"%s","object":"event","type":"%s","api_version":"%s",
				 "data":{"object":%s}}
				""".formatted(id, type, com.stripe.Stripe.API_VERSION, objet);
	}

	private static String session(UUID tenantId, String abonnement) {
		return """
				{"id":"cs_test","object":"checkout.session","mode":"subscription",
				 "client_reference_id":"%s","customer":"cus_test","subscription":"%s"}
				""".formatted(tenantId, abonnement);
	}

	private static String facture(String abonnement) {
		return """
				{"id":"in_test","object":"invoice","customer":"cus_test",
				 "parent":{"type":"subscription_details",
				 "subscription_details":{"subscription":"%s"}}}
				""".formatted(abonnement);
	}

	// --- Inscription ----------------------------------------------------

	@Test
	@DisplayName("l'inscription rend une adresse de paiement et n'ouvre pas la clinique")
	void laCliniqueNaitFermee() {
		ReponseInscription reponse = inscrire("Clinique du Pont");
		UUID tenantId = tenantDe(reponse.slug());

		assertThat(reponse.urlPaiement()).isEqualTo(PasserelleFausse.URL);
		// Le cœur de l'affaire : tant que personne n'a payé, la clinique
		// existe mais ne tourne pas.
		assertThat(actif(tenantId)).isFalse();
		assertThat(statut(tenantId)).isEqualTo("EN_ATTENTE");

		assertThat(passerelle.demandes()).singleElement()
				.satisfies(demande -> {
					assertThat(demande.tenantId()).isEqualTo(tenantId);
					assertThat(demande.palier()).isEqualTo(Palier.COMPTOIR);
				});
	}

	@Test
	@DisplayName("si le prestataire ne répond pas, aucune clinique n'est créée")
	void rienNestEcritSansPaiement() {
		passerelle.tomberEnPanne();

		assertThatThrownBy(() -> inscrire("Clinique Fantome"))
				.isInstanceOf(SouscriptionIndisponible.class);

		// Sans ce garde, un second essai créerait « clinique-fantome-2 » et
		// laisserait la première derrière, invisible et impayée.
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM tenant WHERE slug LIKE 'clinique-fantome%'",
				Integer.class)).isZero();
	}

	@Test
	@DisplayName("le prestataire absent rend un 503 lisible, et pas un 401")
	void leRefusEstLisible() throws Exception {
		passerelle.tomberEnPanne();

		// Sans gestionnaire dédié, l'exception repartait en 500, le renvoi vers
		// /error repassait par la chaîne de sécurité, et la page d'inscription
		// affichait « Erreur 401 » — un message qui envoie chercher un
		// problème de connexion là où il n'y en a pas. Constaté dans un
		// navigateur, pas déduit.
		http.perform(post("/api/inscription").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nom":"Clinique Sans Paiement","email":"a@sp.fr",
								 "motDePasse":"un-mot-de-passe-assez-long",
								 "palier":"COMPTOIR","avecEcran":false,"cgvAcceptees":true}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"contact@attenso.fr")));
	}

	// --- Gestion de l'abonnement ----------------------------------------

	/**
	 * Se connecte comme l'accueil de la clinique.
	 *
	 * <p>Par la vraie route de connexion : l'espace client est une route du
	 * personnel, et vérifier qu'elle l'est vraiment demande un jeton obtenu
	 * comme le ferait une ASV.
	 */
	private String connecter(String slug, String email, String motDePasse)
			throws Exception {
		String corps = http.perform(post("/api/connexion")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"slug":"%s","email":"%s","motDePasse":"%s"}
								""".formatted(slug, email, motDePasse)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(corps).get("jeton").asText();
	}

	@Test
	@DisplayName("une clinique abonnée ouvre son espace de gestion")
	void lEspaceClientSOuvre() throws Exception {
		ReponseInscription reponse = inscriptions.inscrire(new DemandeInscription(
				"Clinique Gerante", "gerante@c.fr", "un-mot-de-passe-assez-long",
				Palier.COMPTOIR.name(), false, true));
		UUID tenantId = tenantDe(reponse.slug());
		envoyer(evenement("evt_g1", "checkout.session.completed",
				session(tenantId, "sub_gerante")));
		String jeton = connecter(reponse.slug(), "gerante@c.fr",
				"un-mot-de-passe-assez-long");

		String corps = http.perform(post("/api/facturation/espace-client")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(corps).contains(PasserelleFausse.URL_ESPACE);
		// L'identifiant client passé au prestataire est bien celui que le
		// webhook avait retenu : sans ça, on ouvrirait l'espace de quelqu'un
		// d'autre — ou de personne.
		assertThat(passerelle.espacesOuverts()).containsExactly("cus_test");
	}

	@Test
	@DisplayName("sans abonnement en ligne, l'espace client est refusé proprement")
	void sansAbonnementPasDEspace() throws Exception {
		// Le cas d'une clinique créée avant la mise en vente, ou dont le
		// paiement n'a jamais abouti : il n'y a rien à gérer, et le dire vaut
		// mieux que de rediriger vers une page d'erreur du prestataire.
		UUID tenantId = creerClinique("sans-abonnement", "Sans abonnement", 2, 180);
		creerUtilisateur(tenantId, "a@sa.fr", "un-mot-de-passe-assez-long",
				ai.attenso.utilisateur.Role.ACCUEIL);
		String jeton = connecter("sans-abonnement", "a@sa.fr",
				"un-mot-de-passe-assez-long");

		http.perform(post("/api/facturation/espace-client")
						.header("Authorization", "Bearer " + jeton))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("l'espace client exige un jeton du personnel")
	void lEspaceClientExigeUnJeton() throws Exception {
		http.perform(post("/api/facturation/espace-client"))
				.andExpect(status().isUnauthorized());
	}

	// --- Webhook --------------------------------------------------------

	@Test
	@DisplayName("le paiement confirmé ouvre la clinique")
	void lePaiementOuvreLaClinique() throws Exception {
		ReponseInscription reponse = inscrire("Clinique des Lilas");
		UUID tenantId = tenantDe(reponse.slug());

		envoyer(evenement("evt_1", "checkout.session.completed",
				session(tenantId, "sub_lilas")));

		assertThat(actif(tenantId)).isTrue();
		assertThat(statut(tenantId)).isEqualTo("ACTIF");
	}

	@Test
	@DisplayName("une signature fausse est refusée, et rien ne bouge")
	void uneSignatureFausseEstRefusee() throws Exception {
		ReponseInscription reponse = inscrire("Clinique du Lac");
		UUID tenantId = tenantDe(reponse.slug());
		String charge = evenement("evt_faux", "checkout.session.completed",
				session(tenantId, "sub_lac"));

		// Sans cette vérification, n'importe qui s'offrirait un abonnement en
		// postant ce corps-là.
		envoyer(charge, "t=1,v1=0000").andExpect(status().isBadRequest());
		envoyer(charge, "").andExpect(status().isBadRequest());

		assertThat(actif(tenantId)).isFalse();
		assertThat(statut(tenantId)).isEqualTo("EN_ATTENTE");
	}

	@Test
	@DisplayName("un événement rejoué ne s'applique qu'une fois")
	void leRejeuNaPasDEffet() throws Exception {
		ReponseInscription reponse = inscrire("Clinique du Parc");
		UUID tenantId = tenantDe(reponse.slug());
		String charge = evenement("evt_rejeu", "checkout.session.completed",
				session(tenantId, "sub_parc"));

		envoyer(charge);
		envoyer(charge);

		// Stripe rejoue à la moindre réponse lente. Le compte d'événements
		// enregistrés est la preuve que le second passage a été écarté.
		assertThat(proprietaire.queryForObject(
				"SELECT count(*) FROM paiement_evenement WHERE id = 'evt_rejeu'",
				Integer.class)).isEqualTo(1);
		assertThat(statut(tenantId)).isEqualTo("ACTIF");
	}

	@Test
	@DisplayName("un impayé marque l'abonnement mais ne ferme PAS la clinique")
	void unImpayeNeFermeRien() throws Exception {
		ReponseInscription reponse = inscrire("Clinique de la Gare");
		UUID tenantId = tenantDe(reponse.slug());
		envoyer(evenement("evt_ok", "checkout.session.completed",
				session(tenantId, "sub_gare")));

		envoyer(evenement("evt_impaye", "invoice.payment_failed", facture("sub_gare")));

		assertThat(statut(tenantId)).isEqualTo("IMPAYE");
		// LA ligne qui compte. Couper une salle d'attente parce qu'une carte a
		// expiré laisserait de vraies personnes debout devant une clinique qui
		// ne peut plus les appeler.
		assertThat(actif(tenantId)).isTrue();
	}

	@Test
	@DisplayName("un paiement rattrapé rend l'abonnement actif")
	void leRattrapageRemetEnActif() throws Exception {
		ReponseInscription reponse = inscrire("Clinique du Moulin");
		UUID tenantId = tenantDe(reponse.slug());
		envoyer(evenement("evt_a", "checkout.session.completed",
				session(tenantId, "sub_moulin")));
		envoyer(evenement("evt_b", "invoice.payment_failed", facture("sub_moulin")));

		envoyer(evenement("evt_c", "invoice.paid", facture("sub_moulin")));

		assertThat(statut(tenantId)).isEqualTo("ACTIF");
	}

	@Test
	@DisplayName("un prélèvement qui se retourne referme la clinique qu'il avait ouverte")
	void lePrelevementRetourneRefermeLaClinique() throws Exception {
		ReponseInscription reponse = inscrire("Clinique du Sepa");
		UUID tenantId = tenantDe(reponse.slug());
		// Le cas réel : un prélèvement SEPA se dénoue en plusieurs jours. On
		// ouvre à la confirmation de session, sinon la clinique patienterait
		// une semaine devant une file fermée.
		envoyer(evenement("evt_s1", "checkout.session.completed",
				session(tenantId, "sub_sepa")));
		assertThat(actif(tenantId)).isTrue();

		envoyer(evenement("evt_s2", "checkout.session.async_payment_failed",
				session(tenantId, "sub_sepa")));

		// ICI, et ici seulement, un défaut de paiement referme : l'ouverture
		// reposait sur un paiement qui n'a jamais abouti. La règle « un impayé
		// ne coupe rien » protège un client qui a payé, pas un IBAN invalide.
		assertThat(actif(tenantId)).isFalse();
		assertThat(statut(tenantId)).isEqualTo("IMPAYE");
	}

	@Test
	@DisplayName("un abonnement Stripe inconnu ne fait rien, et n'échoue pas")
	void unAbonnementInconnuEstIgnore() throws Exception {
		// Le cas réel : un compte Stripe partagé avec un autre produit, ou une
		// base restaurée. Rendre une erreur ferait rejouer Stripe sans fin.
		envoyer(evenement("evt_inconnu", "invoice.payment_failed", facture("sub_ailleurs")));
	}

	@Test
	@DisplayName("un type d'événement qu'on ne traite pas est accusé quand même")
	void unTypeInconnuEstAccuse() throws Exception {
		envoyer(evenement("evt_autre", "customer.updated",
				"{\"id\":\"cus_test\",\"object\":\"customer\"}"));
	}

	@Test
	@DisplayName("la résiliation clôt l'abonnement sans fermer la clinique le jour même")
	void laResiliationLaisseLaPeriodePayee() throws Exception {
		ReponseInscription reponse = inscrire("Clinique du Vallon");
		UUID tenantId = tenantDe(reponse.slug());
		envoyer(evenement("evt_v1", "checkout.session.completed",
				session(tenantId, "sub_vallon")));

		envoyer(evenement("evt_v2", "customer.subscription.deleted",
				"{\"id\":\"sub_vallon\",\"object\":\"subscription\",\"status\":\"canceled\","
						+ "\"customer\":\"cus_test\"}"));

		assertThat(statut(tenantId)).isEqualTo("RESILIE");
		// La clinique a payé sa période : elle la garde. C'est la tâche de
		// fin d'abonnement qui ferme, le lendemain de la date de fin.
		assertThat(actif(tenantId)).isTrue();
	}

	@Test
	@DisplayName("le lendemain de la fin, la tâche ferme la clinique")
	void laTacheFermeApresLaFin() throws Exception {
		ReponseInscription reponse = inscrire("Clinique des Cedres");
		UUID tenantId = tenantDe(reponse.slug());
		envoyer(evenement("evt_c1", "checkout.session.completed",
				session(tenantId, "sub_cedres")));
		envoyer(evenement("evt_c2", "customer.subscription.deleted",
				"{\"id\":\"sub_cedres\",\"object\":\"subscription\",\"status\":\"canceled\","
						+ "\"customer\":\"cus_test\"}"));

		// Le jour même, rien ne se ferme : la période est payée.
		assertThat(ContexteTenant.avec(tenantId,
				() -> paiements.fermerSiEchu(tenantId, LocalDate.now()))).isFalse();
		assertThat(actif(tenantId)).isTrue();

		boolean ferme = ContexteTenant.avec(tenantId,
				() -> paiements.fermerSiEchu(tenantId, LocalDate.now().plusDays(1)));

		assertThat(ferme).isTrue();
		assertThat(actif(tenantId)).isFalse();
	}

	@Autowired
	ai.attenso.paiement.PaiementDao paiements;
}
