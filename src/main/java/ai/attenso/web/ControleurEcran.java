package ai.attenso.web;

import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.LigneFile;
import ai.attenso.ticket.ServiceEstimation;
import ai.attenso.ticket.ServiceFile;
import ai.attenso.ticket.Statut;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * L'écran du mur : qui est appelé, qui vient après.
 *
 * <p>C'est lui qui reprend la main quand le réseau tombe — avec la voix. Les
 * SMS ne partent pas pendant une panne, c'est assumé et dit à la clinique.
 */
@RestController
public class ControleurEcran {

	private static final int PROCHAINS_AFFICHES = 3;

	private final ServiceTenant cliniques;

	private final ServiceFile file;

	private final ServiceEstimation estimation;

	private final DiffusionFile diffusion;

	public ControleurEcran(ServiceTenant cliniques, ServiceFile file,
			ServiceEstimation estimation, DiffusionFile diffusion) {
		this.cliniques = cliniques;
		this.file = file;
		this.estimation = estimation;
		this.diffusion = diffusion;
	}

	/**
	 * L'état affiché au mur, derrière le jeton d'appairage (EF-601).
	 *
	 * <p>La route par slug qui existait avant était publique : qui devinait le
	 * slug — le nom de la clinique, le plus souvent — lisait sa file en direct.
	 * Rien de nominatif n'y figure, mais l'affluence d'un cabinet est une
	 * donnée commerciale, et elle n'a pas à être servie à qui la demande.
	 */
	@GetMapping("/api/ecran/jeton/{jeton}")
	public VueEcran etat(@PathVariable String jeton) {
		Tenant clinique = cliniques.parJetonEcran(jeton);
		LocalDate jour = file.jourCourant(clinique);
		return ContexteTenant.avec(clinique.id(), () -> {
			List<LigneFile> lignes = file.tableauDuJour(clinique.id(), jour);
			List<Integer> appeles = lignes.stream()
					.filter(ligne -> ligne.statut() == Statut.APPELE)
					.map(LigneFile::numeroJour)
					.toList();
			List<Integer> prochains = lignes.stream()
					.filter(ligne -> ligne.statut() == Statut.EN_ATTENTE
							|| ligne.statut() == Statut.PREVENU
							|| ligne.statut() == Statut.ABSENT)
					.sorted((a, b) -> Integer.compare(a.position(), b.position()))
					.limit(PROCHAINS_AFFICHES)
					.map(LigneFile::numeroJour)
					.toList();
			return new VueEcran(clinique.nom(), appeles, prochains,
					estimation.dureeMedianeMinutes(clinique.id()));
		});
	}

	/** Le flux de l'écran du mur, appairé comme sa donnée. */
	@GetMapping(value = "/api/ecran/jeton/{jeton}/flux",
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter fluxEcran(@PathVariable String jeton) {
		Tenant clinique = cliniques.parJetonEcran(jeton);
		return diffusion.abonner(clinique.id(), file.jourCourant(clinique));
	}

	/**
	 * Le même flux, pour le tableau de l'accueil, résolu par slug.
	 *
	 * <p>Deux routes et non une, parce que les deux abonnés n'ont pas le même
	 * justificatif : le tableau est authentifié par JWT, mais {@code
	 * EventSource} ne sait pas poser d'en-tête {@code Authorization}, et un
	 * jeton passé en paramètre d'URL finirait dans les journaux d'accès du
	 * serveur et des proxys. Surtout, une clinique SANS écran n'a pas de jeton
	 * d'appairage du tout, et son tableau doit quand même se rafraîchir.
	 *
	 * <p>Ce flux ne transporte <strong>aucune donnée</strong> : il dit « ça a
	 * bougé », et l'abonné relit par une route qui, elle, contrôle l'accès.
	 * C'est ce qui rend acceptable de le laisser ouvert sur un slug.
	 */
	@GetMapping(value = "/api/ecran/{slug}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter flux(@PathVariable String slug) {
		Tenant clinique = cliniques.parSlug(slug);
		return diffusion.abonner(clinique.id(), file.jourCourant(clinique));
	}
}
