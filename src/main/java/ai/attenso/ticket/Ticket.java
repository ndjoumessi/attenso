package ai.attenso.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un passage dans la file. Aucune donnée sensible : un numéro de téléphone,
 * purgé à J+1, et un numéro d'ordre affiché au client.
 *
 * <p>Le rang n'est pas une colonne. L'ordre est {@code (priorite DESC, ordre
 * ASC)} et la position est comptée à la lecture — un rang stocké obligerait à
 * réécrire toute la file à chaque urgence, depuis une tablette au réseau
 * instable.
 */
@Entity
@Table(name = "ticket")
public class Ticket {

	/**
	 * Échelle de {@code ordre}. La valeur est l'instant de création en
	 * millisecondes multiplié par ce facteur : l'écart entre deux tickets
	 * laisse la place d'en réinsérer un entre eux, ce dont la réinsertion d'un
	 * absent a besoin.
	 */
	public static final long ECHELLE_ORDRE = 1_000_000L;

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private LocalDate jour;

	@Column(name = "numero_jour", nullable = false)
	private int numeroJour;

	/** Référence du lien SMS : courte et opaque, pas un jeton signé. */
	@Column(nullable = false, updatable = false)
	private String reference;

	@Column(name = "telephone_e164")
	private String telephoneE164;

	@Column(name = "telephone_valide")
	private Boolean telephoneValide;

	@Column(nullable = false)
	private short priorite;

	@Column(nullable = false)
	private long ordre;

	/**
	 * Le libellé du motif de priorité, copié au moment de la création
	 * (EF-407). {@code null} pour la file normale et pour l'urgence.
	 */
	@Column(name = "motif_priorite", updatable = false)
	private String motifPriorite;

	/**
	 * Quand le client a signalé qu'il approchait (EF-205). {@code null} tant
	 * qu'il ne l'a pas fait.
	 *
	 * <p>Ce champ ne pèse <strong>jamais</strong> sur l'ordre de la file : il
	 * s'affiche, il ne décide pas. Un bouton qui ferait passer devant donnerait
	 * à chacun le moyen de doubler les autres depuis son téléphone.
	 */
	@Column(name = "signale_proche_le")
	private Instant signaleProcheLe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Statut statut;

	/**
	 * Qui a créé ce ticket. Conservé parce que c'est le numérateur du taux
	 * d'auto-enregistrement, le chiffre qui décide du projet en semaine 10.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Origine origine;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	@Column(name = "appele_le")
	private Instant appeleLe;

	@Column(name = "grace_expire_le")
	private Instant graceExpireLe;

	@Column(name = "consultation_le")
	private Instant consultationLe;

	@Column(name = "clos_le")
	private Instant closLe;

	protected Ticket() {
	}

	public Ticket(UUID id, UUID tenantId, LocalDate jour, int numeroJour, String reference,
			Priorisation priorisation, long ordre, String telephoneE164, Boolean telephoneValide,
			Origine origine, Instant maintenant) {
		this.id = id;
		this.tenantId = tenantId;
		this.jour = jour;
		this.numeroJour = numeroJour;
		this.reference = reference;
		this.priorite = priorisation.rang();
		this.motifPriorite = priorisation.motif();
		this.ordre = ordre;
		this.telephoneE164 = telephoneE164;
		this.telephoneValide = telephoneValide;
		this.origine = origine;
		this.statut = Statut.EN_ATTENTE;
		this.creeLe = maintenant;
	}

	public String motifPriorite() {
		return motifPriorite;
	}

	public Instant signaleProcheLe() {
		return signaleProcheLe;
	}

	/**
	 * Le client dit qu'il arrive (EF-205).
	 *
	 * <p>Idempotent par écrasement : re-signaler rafraîchit l'horodatage, ce
	 * qui est le comportement utile — « il l'a dit il y a deux minutes » vaut
	 * mieux que « il l'a dit il y a une heure ». Aucune transition d'état, donc
	 * rien à valider : un client peut annoncer son approche quel que soit son
	 * état, y compris après avoir été marqué absent, et c'est précisément le
	 * cas où l'information a le plus de valeur.
	 */
	public void signalerProche(Instant maintenant) {
		this.signaleProcheLe = maintenant;
	}

	// --- Transitions ----------------------------------------------------

	/** Passage automatique sous le seuil de prévenance. */
	public void prevenir() {
		exiger(Statut.PREVENU);
		this.statut = Statut.PREVENU;
	}

	public void appeler(Instant maintenant, Duration grace) {
		exiger(Statut.APPELE);
		this.statut = Statut.APPELE;
		this.appeleLe = maintenant;
		this.graceExpireLe = maintenant.plus(grace);
	}

	public void present(Instant maintenant) {
		exiger(Statut.EN_CONSULTATION);
		this.statut = Statut.EN_CONSULTATION;
		this.consultationLe = maintenant;
		this.graceExpireLe = null;
	}

	public void termine(Instant maintenant) {
		exiger(Statut.TERMINE);
		this.statut = Statut.TERMINE;
		this.closLe = maintenant;
	}

	/** Appelé, pas là, mais revient : il reprend sa place juste après le suivant. */
	public void absent() {
		exiger(Statut.ABSENT);
		this.statut = Statut.ABSENT;
		this.graceExpireLe = null;
	}

	/** A renoncé et quitté les lieux. C'est le compteur de retour sur investissement. */
	public void parti(Instant maintenant) {
		exiger(Statut.PARTI);
		this.statut = Statut.PARTI;
		this.closLe = maintenant;
	}

	public void annule(Instant maintenant) {
		exiger(Statut.ANNULE);
		this.statut = Statut.ANNULE;
		this.closLe = maintenant;
	}

	public void deplacer(long nouvelOrdre) {
		this.ordre = nouvelOrdre;
	}

	public void purgerTelephone() {
		this.telephoneE164 = null;
	}

	private void exiger(Statut cible) {
		if (!this.statut.peutAllerVers(cible)) {
			throw new TransitionInterdite(this.id, this.statut, cible);
		}
	}

	// --- Lecture --------------------------------------------------------

	public UUID id() {
		return id;
	}

	public UUID tenantId() {
		return tenantId;
	}

	public LocalDate jour() {
		return jour;
	}

	public int numeroJour() {
		return numeroJour;
	}

	public String reference() {
		return reference;
	}

	public Origine origine() {
		return origine;
	}

	public String telephoneE164() {
		return telephoneE164;
	}

	public Boolean telephoneValide() {
		return telephoneValide;
	}

	public boolean joignable() {
		return telephoneE164 != null && !Boolean.FALSE.equals(telephoneValide);
	}

	public short priorite() {
		return priorite;
	}

	public long ordre() {
		return ordre;
	}

	public Statut statut() {
		return statut;
	}

	public Instant creeLe() {
		return creeLe;
	}

	public Instant appeleLe() {
		return appeleLe;
	}

	public Instant graceExpireLe() {
		return graceExpireLe;
	}

	public Instant consultationLe() {
		return consultationLe;
	}

	public Instant closLe() {
		return closLe;
	}
}
