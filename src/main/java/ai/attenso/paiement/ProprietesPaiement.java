package ai.attenso.paiement;

import ai.attenso.facturation.Palier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les réglages Stripe.
 *
 * <p>Tout est vide par défaut, comme partout ailleurs dans ce produit : une
 * clé absente n'empêche pas l'application de démarrer, et surtout n'empêche
 * pas une clinique déjà installée de faire tourner sa salle d'attente. Ce
 * qu'elle empêche, c'est la souscription en ligne — et ça, c'est annoncé
 * plutôt que subi.
 *
 * @param cle           clé SECRÈTE Stripe. Jamais dans le dépôt.
 * @param secretWebhook secret de signature des webhooks. Sans lui, n'importe
 *        qui pourrait prétendre être Stripe et activer un abonnement sans
 *        payer : c'est le seul mécanisme d'authentification de cette route.
 * @param prix          l'identifiant de tarif Stripe de chaque palier. Les
 *        montants eux-mêmes vivent chez Stripe et non ici : les dupliquer
 *        ferait une quatrième copie des prix du §13, après l'énumération, la
 *        vitrine et la page d'inscription.
 */
@ConfigurationProperties("attenso.paiement")
public record ProprietesPaiement(String cle, String secretWebhook, Map<Palier, String> prix) {

	public ProprietesPaiement {
		prix = prix == null ? Map.of() : Map.copyOf(prix);
	}

	/** Vrai quand la souscription en ligne peut fonctionner de bout en bout. */
	public boolean complete() {
		return manquants().isEmpty();
	}

	/**
	 * Les réglages absents, nommés.
	 *
	 * <p>L'erreur typique n'est pas « j'ai tout oublié » mais « j'ai oublié le
	 * tarif d'un palier ». Un message générique ferait relire toute la
	 * configuration pour une ligne.
	 */
	public List<String> manquants() {
		List<String> absents = new ArrayList<>();
		if (!rempli(cle)) {
			absents.add("attenso.paiement.cle");
		}
		if (!rempli(secretWebhook)) {
			absents.add("attenso.paiement.secret-webhook");
		}
		for (Palier palier : Palier.values()) {
			if (!rempli(prix.get(palier))) {
				absents.add("attenso.paiement.prix." + palier.name().toLowerCase());
			}
		}
		return List.copyOf(absents);
	}

	/** Le tarif Stripe d'un palier, ou {@code null} s'il n'est pas configuré. */
	public String prixDe(Palier palier) {
		return prix.get(palier);
	}

	private static boolean rempli(String valeur) {
		return valeur != null && !valeur.isBlank();
	}
}
