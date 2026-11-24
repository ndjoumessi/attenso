package ai.attenso.utilisateur;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String email;

	@Column(name = "mdp_hash", nullable = false)
	private String mdpHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean actif;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	/**
	 * Incrémenté à chaque changement de mot de passe. Le jeton porte cette
	 * valeur ; un jeton qui ne la porte plus est refusé, et c'est ainsi que
	 * changer son mot de passe ferme les sessions déjà ouvertes.
	 */
	@Column(name = "jeton_version", nullable = false)
	private int jetonVersion;

	protected Utilisateur() {
	}

	public Utilisateur(UUID id, UUID tenantId, String email, String mdpHash, Role role,
			Instant maintenant) {
		this.id = id;
		this.tenantId = tenantId;
		this.email = email;
		this.mdpHash = mdpHash;
		this.role = role;
		this.actif = true;
		this.creeLe = maintenant;
	}

	public UUID id() {
		return id;
	}

	public UUID tenantId() {
		return tenantId;
	}

	public String email() {
		return email;
	}

	public String mdpHash() {
		return mdpHash;
	}

	public Role role() {
		return role;
	}

	public boolean actif() {
		return actif;
	}

	public int jetonVersion() {
		return jetonVersion;
	}
}
