package ai.attenso.web;

import ai.attenso.affiche.GenerateurQr;
import ai.attenso.config.ProprietesLien;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'affiche du comptoir, celle qui porte le QR code (EF-101).
 *
 * <p>Sans elle, le produit ne démarre pas : le cahier des charges fait entrer
 * le client par un QR affiché au comptoir, et une clinique qui s'inscrit seule
 * n'a personne pour venir le lui poser. Jusqu'ici elle aurait dû trouver un
 * générateur de QR sur le web et y coller son URL — c'est-à-dire confier
 * l'adresse de son comptoir à un tiers quelconque, et imprimer un résultat que
 * personne n'aurait vérifié.
 *
 * <p>Ouvert aux deux rôles. Réimprimer une affiche décollée est un geste de
 * comptoir, pas une décision d'acheteur.
 */
@RestController
public class ControleurAffiche {

	private final ServiceTenant cliniques;

	private final GenerateurQr qr;

	private final ProprietesLien lien;

	public ControleurAffiche(ServiceTenant cliniques, GenerateurQr qr, ProprietesLien lien) {
		this.cliniques = cliniques;
		this.qr = qr;
		this.lien = lien;
	}

	@GetMapping("/api/affiche")
	public VueAffiche affiche(@AuthenticationPrincipal PrincipalPersonnel principal) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		// La même base que les liens des SMS : deux sources pour une seule
		// adresse publique, c'est la garantie qu'un jour l'une des deux sera
		// fausse — et celle imprimée sur l'affiche serait la pire des deux.
		String url = lien.base() + "/t/" + clinique.slug();
		// La MÊME base, pour la même raison : l'affiche est imprimée, et une
		// adresse fausse sur du papier se corrige au massicot.
		String rendezVous = lien.base() + "/rdv/" + clinique.slug();
		return new VueAffiche(clinique.nom(), url, rendezVous,
				clinique.peutEnvoyerDesSms(), qr.encoder(url));
	}
}
