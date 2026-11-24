package ai.attenso.paiement;

import ai.attenso.facturation.Palier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stripe, en local et sans réseau.
 *
 * <p>Les tests ne doivent ni compte, ni clé, ni connexion : ils vérifient ce
 * que le produit FAIT de la réponse du prestataire, pas que le prestataire
 * répond. La vérification de signature, elle, est testée pour de bon — avec
 * un vrai secret et un vrai HMAC — parce que c'est le seul rempart de la route
 * du webhook.
 */
public class PasserelleFausse implements PasserellePaiement {

	public static final String URL = "https://paiement.test/session/factice";

	/** Ce qu'on lui a demandé d'ouvrir, pour que les tests puissent le lire. */
	public record Demande(UUID tenantId, Palier palier, String email, String clinique) {
	}

	private final List<Demande> demandes = new ArrayList<>();

	private final List<String> espacesOuverts = new ArrayList<>();

	private boolean enPanne;

	@Override
	public Souscription ouvrir(UUID tenantId, Palier palier, String email,
			String nomClinique) {
		if (enPanne) {
			throw new SouscriptionIndisponible("panne simulée du prestataire");
		}
		demandes.add(new Demande(tenantId, palier, email, nomClinique));
		return new Souscription(URL, "cus_factice");
	}

	public static final String URL_ESPACE = "https://paiement.test/espace/factice";

	@Override
	public String ouvrirEspaceClient(String clientStripe) {
		if (enPanne) {
			throw new SouscriptionIndisponible("panne simulée du prestataire");
		}
		espacesOuverts.add(clientStripe);
		return URL_ESPACE;
	}

	public List<String> espacesOuverts() {
		return List.copyOf(espacesOuverts);
	}

	@Override
	public boolean disponible() {
		return !enPanne;
	}

	public List<Demande> demandes() {
		return List.copyOf(demandes);
	}

	public void tomberEnPanne() {
		this.enPanne = true;
	}

	public void reparer() {
		this.enPanne = false;
		this.demandes.clear();
		this.espacesOuverts.clear();
	}

	@TestConfiguration
	public static class Config {

		@Bean
		@Primary
		PasserelleFausse passerelleFausse() {
			return new PasserelleFausse();
		}
	}
}
