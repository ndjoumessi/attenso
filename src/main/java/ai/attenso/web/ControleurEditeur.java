package ai.attenso.web;

import ai.attenso.editeur.ProprietesEditeur;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'identité de l'éditeur, pour les pages légales.
 *
 * <p>Publique : c'est précisément ce que la loi exige de publier. Les champs
 * absents sont rendus tels quels — les pages affichent alors « à compléter »,
 * ce qui est préférable à une page qui aurait l'air en règle sans l'être.
 */
@RestController
public class ControleurEditeur {

	private final ProprietesEditeur editeur;

	public ControleurEditeur(ProprietesEditeur editeur) {
		this.editeur = editeur;
	}

	@GetMapping("/api/editeur")
	public ProprietesEditeur editeur() {
		return editeur;
	}
}
