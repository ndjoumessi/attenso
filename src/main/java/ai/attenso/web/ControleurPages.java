package ai.attenso.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Les trois écrans du V0, servis en statique. Le routage est côté serveur
 * parce que les URL doivent être lisibles sur un QR imprimé et courtes dans un
 * SMS — pas parce qu'il y a du rendu à faire.
 */
@Controller
public class ControleurPages {

	/** Le lien du SMS. */
	@GetMapping("/s/{reference}")
	String suivi() {
		return "forward:/app/suivi.html";
	}

	/** Le QR du comptoir. */
	@GetMapping("/t/{slug}")
	String comptoir() {
		return "forward:/app/comptoir.html";
	}

	/**
	 * L'écran du mur, appairé par jeton et non par slug (EF-601).
	 *
	 * <p>L'URL est collée une fois dans le navigateur du téléviseur, puis
	 * oubliée : sa longueur n'a aucune importance, contrairement au lien du
	 * SMS. Rien n'est vérifié ici — la page est statique ; c'est l'appel de
	 * données qu'elle fait ensuite qui porte le contrôle.
	 */
	@GetMapping("/ecran/{jeton}")
	String ecran() {
		return "forward:/app/ecran.html";
	}

	/** Le carnet de santé, ouvert par le QR de la médaille (EF-906). */
	@GetMapping("/carnet/{jeton}")
	String carnet() {
		return "forward:/app/carnet.html";
	}

	/** La réservation en ligne (EF-905). */
	@GetMapping("/rdv/{slug}")
	String reservation() {
		return "forward:/app/reservation.html";
	}

	/** Le rendez-vous d'un client : consulter, annuler (EF-905). */
	@GetMapping("/r/{reference}")
	String rendezVous() {
		return "forward:/app/rendez-vous.html";
	}

	/**
	 * L'affiche à imprimer pour le comptoir (EF-101).
	 *
	 * <p>Page statique comme les autres : ce qu'elle montre lui est servi par
	 * {@code /api/affiche}, qui exige le jeton. Sans jeton elle renvoie au
	 * tableau, et il n'y a rien à protéger dans son balisage.
	 */
	@GetMapping("/affiche")
	String affiche() {
		return "forward:/app/affiche.html";
	}

	/** L'inscription autonome (EF-901). */
	@GetMapping("/inscription")
	String inscription() {
		return "forward:/app/inscription.html";
	}

	/** La vitrine publique. */
	/**
	 * Les pages légales. Obligatoires pour vendre, donc servies au même titre
	 * que la vitrine — et non reléguées dans un coin du pied de page qui
	 * renverrait un 404 le jour où quelqu'un les cherche vraiment.
	 */
	/** Le lien reçu par courriel. Le jeton vit dans l'URL, lu par la page. */
	@GetMapping("/reinitialisation/{jeton}")
	String reinitialisation(@PathVariable String jeton) {
		return "forward:/app/reinitialisation.html";
	}

	@GetMapping("/mentions-legales")
	String mentionsLegales() {
		return "forward:/app/mentions.html";
	}

	@GetMapping("/cgv")
	String conditionsGenerales() {
		return "forward:/app/cgv.html";
	}

	@GetMapping("/confidentialite")
	String confidentialite() {
		return "forward:/app/confidentialite.html";
	}

	@GetMapping("/")
	String vitrine() {
		return "forward:/app/vitrine.html";
	}

	/**
	 * Le tableau de l'accueil, déplacé de la racine pour laisser la place à la
	 * vitrine. L'ASV le met en favori une fois ; le client, lui, n'arrive
	 * jamais par là.
	 */
	@GetMapping("/accueil")
	String accueil() {
		return "forward:/app/accueil.html";
	}
}
