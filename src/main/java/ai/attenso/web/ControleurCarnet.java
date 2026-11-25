package ai.attenso.web;

import ai.attenso.carnet.Animal;
import ai.attenso.carnet.CarnetDao;
import ai.attenso.carnet.ServiceCarnet;
import ai.attenso.carnet.Vaccination;
import ai.attenso.affiche.GenerateurQr;
import ai.attenso.config.ProprietesLien;
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

	private final GenerateurQr qr;

	private final ProprietesLien lien;

	public ControleurCarnet(ServiceTenant cliniques, ServiceCarnet carnets, CarnetDao dao,
			JdbcTemplate jdbc, GenerateurQr qr, ProprietesLien lien) {
		this.cliniques = cliniques;
		this.carnets = carnets;
		this.dao = dao;
		this.jdbc = jdbc;
		this.qr = qr;
		this.lien = lien;
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

	/**
	 * Le QR du carnet d'un animal (EF-906).
	 *
	 * <p>À LA DEMANDE, et non dans `/api/animaux` : encoder un QR par animal à
	 * chaque lecture de la liste ferait payer la grille entière pour un geste
	 * qui porte sur un seul animal.
	 *
	 * <p>La même base de lien que les SMS et que l'affiche. Deux sources pour
	 * une seule adresse publique, c'est la garantie qu'un jour l'une des deux
	 * sera fausse.
	 *
	 * <p>{@code 404} quand le carnet est fermé ou révoqué : il n'y a alors
	 * aucune adresse à encoder, et en rendre une serait promettre un accès qui
	 * ne s'ouvrira pas.
	 */
	@GetMapping("/api/animaux/{id}/carnet/qr")
	public VueQrCarnet qrDuCarnet(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id) {
		Animal animal = ContexteTenant.avec(principal.tenantId(),
				() -> dao.parId(principal.tenantId(), id).orElseThrow(CarnetInconnu::new));
		if (!animal.carnetOuvert()) {
			throw new CarnetInconnu();
		}
		String url = lien.base() + "/carnet/" + animal.jetonCarnet();
		return new VueQrCarnet(url, qr.encoder(url));
	}

	public record VueQrCarnet(String url, ai.attenso.affiche.Qr qr) {
	}

	public record VueCarnet(String clinique, String animal, String espece,
			List<VueVaccin> vaccins) {
	}

	public record VueVaccin(String vaccin, LocalDate faitLe, LocalDate rappelLe, String note) {
	}
}
