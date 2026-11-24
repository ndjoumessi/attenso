package ai.attenso.facturation;

/**
 * Les paliers du §13 du cahier des charges (EF-902).
 *
 * <p>Les prix sont en <strong>centimes entiers</strong> et non en flottants :
 * un prix en {@code double} finit toujours par produire une facture à 68,99 €,
 * ce qui n'est pas un arrondi acceptable quand le client la lit.
 *
 * <p>SMS inclus, facture fixe : c'est l'argument commercial, et c'est aussi ce
 * qui fait porter le risque de volume à l'éditeur. D'où le dépassement, qui le
 * borne sans surprendre personne.
 *
 * @param visitesIncluses au-delà, chaque visite est facturée
 *        {@link #DEPASSEMENT_CENTIMES}.
 */
public enum Palier {

	COMPTOIR(300, 6900),

	CLINIQUE(800, 14900),

	CENTRE(1500, 23900);

	/** 0,15 € la visite au-delà du palier (§13). */
	public static final int DEPASSEMENT_CENTIMES = 15;

	private final int visitesIncluses;

	private final int prixCentimes;

	Palier(int visitesIncluses, int prixCentimes) {
		this.visitesIncluses = visitesIncluses;
		this.prixCentimes = prixCentimes;
	}

	public int visitesIncluses() {
		return visitesIncluses;
	}

	public int prixCentimes() {
		return prixCentimes;
	}

	public int depassementCentimes(int visites) {
		return Math.max(0, visites - visitesIncluses) * DEPASSEMENT_CENTIMES;
	}

	public int totalCentimes(int visites) {
		return prixCentimes + depassementCentimes(visites);
	}

	/**
	 * Le palier le moins cher qui couvre ce volume.
	 *
	 * <p>Sert à l'inscription autonome (EF-901) et au conseil : une clinique
	 * qui dépasse systématiquement paie plus cher en dépassement qu'en
	 * changeant de palier, et le lui cacher serait un piège commercial.
	 * Au-delà du plus grand palier, c'est CENTRE plus dépassement — il n'y a
	 * rien au-dessus.
	 */
	public static Palier pour(int visitesParMois) {
		for (Palier palier : values()) {
			if (visitesParMois <= palier.visitesIncluses) {
				return palier;
			}
		}
		return CENTRE;
	}
}
