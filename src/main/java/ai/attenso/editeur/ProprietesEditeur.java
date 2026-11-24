package ai.attenso.editeur;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * L'identité de l'éditeur, exigée par la loi pour vendre en ligne.
 *
 * <p><strong>Pourquoi en configuration et non dans les pages.</strong> Ce sont
 * des données réelles et personnelles — raison sociale, siège, SIRET, nom du
 * directeur de publication — et ce dépôt est PUBLIC. Les écrire dans du HTML
 * versionné les publierait sur GitHub avant même la mise en ligne du site, et
 * les rendrait quasi ineffaçables de l'historique. Elles arrivent donc par
 * l'environnement, comme les secrets, à la différence près qu'elles ne sont pas
 * secrètes : simplement, ce n'est pas au dépôt de les porter.
 *
 * <p>Les pages légales les affichent telles quelles, et marquent « à compléter »
 * ce qui manque — un champ vide dans des mentions légales est une infraction,
 * pas un détail d'habillage.
 *
 * @param formeJuridique SAS, SARL, entreprise individuelle… Vide pour une
 *        personne physique sans forme sociétaire.
 * @param capital        sans objet pour une entreprise individuelle, d'où son
 *        absence de la liste des champs obligatoires.
 * @param hebergeur      exigé par la LCEN : le visiteur doit pouvoir joindre
 *        l'hébergeur si l'éditeur ne répond pas.
 */
@ConfigurationProperties("attenso.editeur")
public record ProprietesEditeur(String denomination, String formeJuridique, String capital,
		String siege, String siret, String rcs, String tva, String directeurPublication,
		String email, String telephone, String hebergeur, String hebergeurAdresse) {

	/**
	 * Les champs sans lesquels les mentions légales sont incomplètes.
	 *
	 * <p>Le capital et la forme juridique n'y sont pas : une entreprise
	 * individuelle n'en a pas, et les exiger rendrait le produit invendable
	 * pour un vétérinaire qui s'installe seul.
	 */
	public List<String> manquants() {
		List<String> absents = new ArrayList<>();
		ajouterSiVide(absents, "denomination", denomination);
		ajouterSiVide(absents, "siege", siege);
		ajouterSiVide(absents, "siret", siret);
		ajouterSiVide(absents, "directeur-publication", directeurPublication);
		ajouterSiVide(absents, "email", email);
		ajouterSiVide(absents, "hebergeur", hebergeur);
		ajouterSiVide(absents, "hebergeur-adresse", hebergeurAdresse);
		return List.copyOf(absents);
	}

	public boolean complet() {
		return manquants().isEmpty();
	}

	private static void ajouterSiVide(List<String> absents, String nom, String valeur) {
		if (valeur == null || valeur.isBlank()) {
			absents.add("attenso.editeur." + nom);
		}
	}
}
