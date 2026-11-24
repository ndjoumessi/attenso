package ai.attenso.inscription;

/** Palier d'abonnement inexistant : la liste est fermée, et courte. */
public class PalierInconnu extends RuntimeException {

	public PalierInconnu(String demande) {
		super("palier inconnu : " + demande);
	}
}
