package ai.attenso.ticket;

/**
 * Une urgence prend {@link #URGENCE} et passe donc devant tout le monde sans
 * qu'on renumérote quoi que ce soit : c'est tout l'intérêt de ne pas stocker
 * le rang.
 */
public final class Priorite {

	public static final short NORMALE = 0;

	public static final short PRIORITAIRE = 5;

	public static final short URGENCE = 10;

	private Priorite() {
	}
}
