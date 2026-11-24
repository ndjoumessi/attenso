package ai.attenso.web;

import ai.attenso.mesure.ExportCsv;
import ai.attenso.mesure.MesureDao;
import ai.attenso.mesure.MesureJour;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import ai.attenso.ticket.ServiceFile;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'instrumentation du pilote (§5.7).
 *
 * <p>C'est par ici que passe le chiffre qui décide du projet. Le critère
 * d'arrêt de la semaine 10 — moins de 30 % d'auto-enregistrement — ne peut
 * être constaté que si quelqu'un saisit le nombre d'arrivants : le système
 * voit les gens qui prennent un ticket, pas ceux qui franchissent la porte.
 */
@RestController
@RequestMapping("/api/mesure")
public class ControleurMesure {

	/** Trente jours : le pilote dure dix semaines, un export mensuel suffit. */
	private static final int JOURS_PAR_DEFAUT = 30;

	private final ServiceTenant cliniques;

	private final MesureDao mesures;

	private final ServiceFile file;

	public ControleurMesure(ServiceTenant cliniques, MesureDao mesures, ServiceFile file) {
		this.cliniques = cliniques;
		this.mesures = mesures;
		this.file = file;
	}

	@GetMapping
	public VueMesure duJour(@AuthenticationPrincipal PrincipalPersonnel principal) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		return vue(mesures.duJour(clinique.id(), file.jourCourant(clinique)));
	}

	@PutMapping("/arrivants")
	public VueMesure poserArrivants(@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandeArrivants demande) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		LocalDate jour = file.jourCourant(clinique);
		mesures.poserArrivants(clinique.id(), jour, demande.arrivants());
		return vue(mesures.duJour(clinique.id(), jour));
	}

	/**
	 * L'export du critère de recette R9. Téléchargé, pas affiché : il part chez
	 * le gérant par courriel, ou dans un tableur.
	 */
	@GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
	public ResponseEntity<String> export(
			@AuthenticationPrincipal PrincipalPersonnel principal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate debut,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate fin) {
		Tenant clinique = cliniques.parId(principal.tenantId());
		LocalDate aujourdhui = file.jourCourant(clinique);
		LocalDate depuis = debut != null ? debut : aujourdhui.minusDays(JOURS_PAR_DEFAUT);
		LocalDate jusqua = fin != null ? fin : aujourdhui;

		List<MesureJour> lignes = mesures.surPeriode(clinique.id(), depuis, jusqua);
		String nom = "attenso-%s-%s-%s.csv".formatted(clinique.slug(), depuis, jusqua);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + nom + "\"")
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.body(ExportCsv.depuis(lignes));
	}

	private VueMesure vue(MesureJour m) {
		return new VueMesure(m.jour(), m.arrivantsTotal(), m.ticketsComptoir(),
				m.ticketsAccueil(), m.ticketsTotal(), m.tauxAutoEnregistrement(), m.zone(),
				m.departs());
	}
}
