package ai.attenso.web;

import ai.attenso.carnet.Animal;
import ai.attenso.carnet.CarnetDao;
import ai.attenso.carnet.ServiceCarnet;
import ai.attenso.carnet.Vaccination;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.Tenant;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carnet de santé, QR révocable et vaccins (EF-906).
 *
 * <p>Le carnet public ne rend <strong>que</strong> ce qui est imprimé sur un
 * carnet papier : le nom de l'animal, son espèce, ses vaccins et leurs dates.
 * Pas de numéro de téléphone, pas d'identifiant interne, pas de nom de
 * propriétaire — le QR est collé sur une médaille, et une médaille se perd.
 */
@RestController
public class ControleurCarnet {

	private final ServiceTenant cliniques;

	private final ServiceCarnet carnets;

	private final CarnetDao dao;

	private final JdbcTemplate jdbc;

	public ControleurCarnet(ServiceTenant cliniques, ServiceCarnet carnets, CarnetDao dao,
			JdbcTemplate jdbc) {
		this.cliniques = cliniques;
		this.carnets = carnets;
		this.dao = dao;
		this.jdbc = jdbc;
	}

	// --- Public : le QR de la médaille ----------------------------------

	@GetMapping("/api/carnet/{jeton}")
	public VueCarnet carnet(@PathVariable String jeton) {
		UUID tenantId = jdbc.queryForObject("SELECT attenso_tenant_de_carnet(?)", UUID.class,
				jeton);
		if (tenantId == null) {
			throw new CarnetInconnu();
		}
		Tenant clinique = cliniques.parId(tenantId);
		return ContexteTenant.avec(tenantId, () -> {
			Animal animal = dao.parJeton(tenantId, jeton).orElseThrow(CarnetInconnu::new);
			return new VueCarnet(clinique.nom(), animal.nom(), animal.espece(),
					dao.vaccinations(tenantId, animal.id()).stream()
							.map(v -> new VueVaccin(v.vaccin(), v.faitLe(), v.rappelLe(),
									v.note()))
							.toList());
		});
	}

	// --- Accueil ---------------------------------------------------------

	@GetMapping("/api/animaux")
	public List<Animal> animaux(@AuthenticationPrincipal PrincipalPersonnel principal) {
		return dao.tous(principal.tenantId());
	}

	@PostMapping("/api/animaux")
	@ResponseStatus(HttpStatus.CREATED)
	public Animal creer(@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandeAnimal demande) {
		return carnets.creer(principal.tenantId(), demande.nom(), demande.espece(),
				demande.telephone(), demande.avecCarnet());
	}

	@GetMapping("/api/animaux/{id}/vaccinations")
	public List<Vaccination> vaccinations(
			@AuthenticationPrincipal PrincipalPersonnel principal, @PathVariable UUID id) {
		return dao.vaccinations(principal.tenantId(), id);
	}

	@PostMapping("/api/animaux/{id}/vaccinations")
	@ResponseStatus(HttpStatus.CREATED)
	public List<Vaccination> ajouterVaccination(
			@AuthenticationPrincipal PrincipalPersonnel principal, @PathVariable UUID id,
			@Valid @RequestBody DemandeVaccination demande) {
		dao.ajouterVaccination(principal.tenantId(), id, demande.vaccin(), demande.faitLe(),
				demande.rappelLe(), demande.note());
		return dao.vaccinations(principal.tenantId(), id);
	}

	/**
	 * Révoque le QR du carnet (EF-906).
	 *
	 * <p>{@code reemettre=false} ferme le carnet au lieu d'en rouvrir un :
	 * c'est ce que demande un propriétaire qui ne veut plus d'accès par QR du
	 * tout, et le lui refuser viderait le mot « révocable » de son sens.
	 */
	@PostMapping("/api/animaux/{id}/carnet/revoquer")
	public Animal revoquer(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id, @RequestBody DemandeRevocation demande) {
		return carnets.revoquerLeCarnet(principal.tenantId(), id, demande.reemettre());
	}

	public record VueCarnet(String clinique, String animal, String espece,
			List<VueVaccin> vaccins) {
	}

	public record VueVaccin(String vaccin, LocalDate faitLe, LocalDate rappelLe, String note) {
	}
}
