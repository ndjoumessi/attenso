package ai.attenso.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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

	/** L'écran du mur. */
	@GetMapping("/ecran/{slug}")
	String ecran() {
		return "forward:/app/ecran.html";
	}

	@GetMapping("/")
	String accueil() {
		return "forward:/app/accueil.html";
	}
}
