package ai.attenso.securite;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Référence du lien envoyé par SMS.
 *
 * <p>Douze caractères sur un alphabet de 62, soit environ 71 bits : indevinable,
 * et surtout <em>court</em>. C'est la contrainte qui mène : le lien doit tenir
 * dans un SMS d'un seul segment, ce qu'un jeton signé ne permet pas.
 *
 * <p>Elle sert de justificatif d'accès à la page de suivi. C'est suffisant
 * parce qu'elle ne donne accès qu'à une position dans une file, qu'elle est
 * cloisonnée à un tenant et qu'elle meurt avec la journée. Le QR <em>imprimé</em>
 * du comptoir, lui, est durable et sera stocké haché — ce n'est pas le même
 * objet et ce n'est pas la même règle.
 */
@Component
public class GenerateurReference {

	public static final int LONGUEUR = 12;

	private static final char[] ALPHABET =
			"abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final SecureRandom alea = new SecureRandom();

	public String suivante() {
		char[] tirage = new char[LONGUEUR];
		for (int i = 0; i < LONGUEUR; i++) {
			tirage[i] = ALPHABET[alea.nextInt(ALPHABET.length)];
		}
		return new String(tirage);
	}
}
