package ai.attenso.web;

import ai.attenso.config.ProprietesReseau;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Adresse de l'appelant, pour le limiteur de débit et le journal d'accès.
 *
 * <p><strong>La dernière entrée de {@code X-Forwarded-For}, pas la
 * première.</strong> L'en-tête est une liste à laquelle chaque relais ajoute
 * l'adresse qu'il a vue, en fin. Les entrées de gauche viennent donc du
 * client : il suffit d'envoyer soi-même {@code X-Forwarded-For: 1.2.3.4} pour
 * se donner une adresse neuve à chaque requête, et le quota par adresse ne
 * limite plus rien. Celle de droite est ajoutée par notre propre relais.
 *
 * <p>Ceci suppose <em>un seul</em> relais de confiance devant l'application,
 * ce qui est le cas du V0. Avec deux relais en cascade, il faudrait prendre la
 * deuxième en partant de la droite — d'où le réglage
 * {@code attenso.reseau.relais-de-confiance}.
 */
@Component
public class AdresseAppelant {

	private final ProprietesReseau proprietes;

	public AdresseAppelant(ProprietesReseau proprietes) {
		this.proprietes = proprietes;
	}

	public String de(HttpServletRequest requete) {
		if (!proprietes.derriereProxy()) {
			return requete.getRemoteAddr();
		}
		String transmise = requete.getHeader("X-Forwarded-For");
		if (transmise == null || transmise.isBlank()) {
			return requete.getRemoteAddr();
		}
		String[] relais = transmise.split(",");
		int rang = relais.length - Math.max(1, proprietes.relaisDeConfiance());
		if (rang < 0) {
			// Moins d'entrées que de relais annoncés : l'en-tête ne vient pas
			// de la chaîne attendue. On ne lui fait pas confiance.
			return requete.getRemoteAddr();
		}
		String adresse = relais[rang].trim();
		return adresse.isEmpty() ? requete.getRemoteAddr() : adresse;
	}
}
