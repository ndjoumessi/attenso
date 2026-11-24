package ai.attenso.web;

import ai.attenso.equipe.MembreEquipe;
import ai.attenso.equipe.ServiceEquipe;
import ai.attenso.securite.PrincipalPersonnel;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.utilisateur.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les comptes du personnel. Réservé aux GÉRANTS.
 *
 * <p>La restriction est déclarée dans {@code ConfigurationSecurite} et non ici
 * : une règle d'accès dispersée dans les contrôleurs finit par avoir un trou,
 * et c'est le genre de trou qu'on ne voit pas en relisant.
 *
 * <p>Pas de suppression, seulement une désactivation. Un compte supprimé
 * emporterait avec lui le journal d'accès qui le désigne — or c'est
 * précisément quand quelqu'un part qu'on veut pouvoir relire ce qu'il a fait.
 */
@RestController
@RequestMapping("/api/equipe")
public class ControleurEquipe {

	private final ServiceEquipe equipe;

	private final ServiceTenant cliniques;

	public ControleurEquipe(ServiceEquipe equipe, ServiceTenant cliniques) {
		this.equipe = equipe;
		this.cliniques = cliniques;
	}

	@GetMapping
	public List<MembreEquipe> lister(@AuthenticationPrincipal PrincipalPersonnel principal) {
		return equipe.lister(principal.tenantId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MembreEquipe inviter(@AuthenticationPrincipal PrincipalPersonnel principal,
			@Valid @RequestBody DemandeInvitation demande) {
		return equipe.inviter(cliniques.parId(principal.tenantId()), demande.email(),
				demande.role());
	}

	@PostMapping("/{id}/role")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changerRole(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id, @Valid @RequestBody DemandeRole demande) {
		equipe.changerRole(principal.tenantId(), id, demande.role());
	}

	@PostMapping("/{id}/desactiver")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desactiver(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id) {
		equipe.changerActif(principal.tenantId(), id, false);
	}

	@PostMapping("/{id}/reactiver")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reactiver(@AuthenticationPrincipal PrincipalPersonnel principal,
			@PathVariable UUID id) {
		equipe.changerActif(principal.tenantId(), id, true);
	}

	public record DemandeInvitation(
			@NotBlank @Email @Size(max = 200) String email,
			@NotNull Role role) {
	}

	public record DemandeRole(@NotNull Role role) {
	}
}
