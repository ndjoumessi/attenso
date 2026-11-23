package ai.attenso.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Une clinique. Porte sa propre configuration de file : le délai de grâce et
 * le seuil de prévenance ne sont pas des constantes du code, parce que la
 * bonne valeur dépend de la durée des consultations (spec §8).
 */
@Entity
@Table(name = "tenant")
public class Tenant {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private String nom;

	@Column(nullable = false)
	private String fuseau;

	@Column(name = "delai_grace_secondes", nullable = false)
	private int delaiGraceSecondes;

	@Column(name = "seuil_prevenir", nullable = false)
	private int seuilPrevenir;

	/**
	 * Nom affiché comme expéditeur des SMS de cette clinique, 3 à 11
	 * caractères alphanumériques. {@code null} tant qu'il n'est pas enregistré
	 * auprès des opérateurs — auquel cas aucun SMS ne part pour elle.
	 */
	@Column(name = "expediteur_sms")
	private String expediteurSms;

	@Column(nullable = false)
	private boolean actif;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	protected Tenant() {
	}

	public Tenant(UUID id, String slug, String nom) {
		this.id = id;
		this.slug = slug;
		this.nom = nom;
		this.fuseau = "Europe/Paris";
		this.delaiGraceSecondes = 180;
		this.seuilPrevenir = 2;
		this.actif = true;
		this.creeLe = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public String slug() {
		return slug;
	}

	public String nom() {
		return nom;
	}

	public ZoneId zone() {
		return ZoneId.of(fuseau);
	}

	public Duration delaiGrace() {
		return Duration.ofSeconds(delaiGraceSecondes);
	}

	public int seuilPrevenir() {
		return seuilPrevenir;
	}

	public String expediteurSms() {
		return expediteurSms;
	}

	public boolean peutEnvoyerDesSms() {
		return NomExpediteur.valide(expediteurSms);
	}

	public boolean actif() {
		return actif;
	}
}
