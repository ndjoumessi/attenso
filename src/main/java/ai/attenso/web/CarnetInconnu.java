package ai.attenso.web;

/**
 * Jeton de carnet inconnu, révoqué, ou animal désactivé.
 *
 * <p>Les trois rendent la même réponse : distinguer « n'a jamais existé » de
 * « a été révoqué » dirait à qui a photographié un QR que le propriétaire l'a
 * remarqué.
 */
public class CarnetInconnu extends RuntimeException {

	public CarnetInconnu() {
		super("carnet inconnu");
	}
}
