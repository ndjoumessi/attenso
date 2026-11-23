package ai.attenso.ticket;

/**
 * L'opérateur confirme que ce numéro ne recevra pas de SMS. Se traduit en
 * HTTP 400, avec un message qui invite à corriger — tout l'intérêt de vérifier
 * est que le client soit encore au comptoir pour le faire.
 */
public class NumeroInjoignable extends RuntimeException {

	public NumeroInjoignable(String detail) {
		super("numéro injoignable : " + detail);
	}
}
