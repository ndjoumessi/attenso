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

	@GetMapping("/api/ecran/{slug}")
	public VueEcran etat(@PathVariable String slug) {
		Tenant clinique = cliniques.parSlug(slug);
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

	@GetMapping(value = "/api/ecran/{slug}/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter flux(@PathVariable String slug) {
		Tenant clinique = cliniques.parSlug(slug);
		return diffusion.abonner(clinique.id(), file.jourCourant(clinique));
	}
}
