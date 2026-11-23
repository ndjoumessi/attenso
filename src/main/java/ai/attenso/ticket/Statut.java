package ai.attenso.ticket;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * La machine à états du ticket (spec §3). Les transitions permises sont
 * déclarées ici et nulle part ailleurs : un service qui se trompe d'état se
 * heurte à {@link TransitionInterdite}, pas à une base incohérente.
 *
 * <p>{@code ABSENT} mène directement à {@code EN_CONSULTATION}, sans repasser
 * par {@code APPELE}, pour deux raisons qui pointent dans le même sens. En
 * salle, quelqu'un marqué absent qui se présente au comptoir entre en
 * consultation : lui faire refaire « j'appelle » puis « présent » est un geste
 * pour rien. Et après une panne plus longue que le délai de grâce, le tick
 * fait passer ABSENT un ticket que l'ASV avait vu arriver : sans cette
 * transition, son « présent » rejoué serait refusé et elle devrait tout
 * reprendre.
 *
 * <p>La distinction {@link #ABSENT} / {@link #PARTI} est le point le plus
 * important de cette machine, et elle n'est pas cosmétique :
 * <ul>
 *   <li>{@code ABSENT} — appelé, pas là, <em>revient</em> ;</li>
 *   <li>{@code PARTI} — a renoncé et quitté les lieux. C'est le compteur qui
 *       justifie le prix de l'abonnement.</li>
 * </ul>
 * Les confondre fausserait le seul chiffre présenté en rendez-vous de vente.
 */
public enum Statut {

	EN_ATTENTE,
	PREVENU,
	APPELE,
	ABSENT,
	EN_CONSULTATION,
	TERMINE,
	PARTI,
	ANNULE;

	/**
	 * Les états qui occupent une place dans la file. {@code EN_CONSULTATION}
	 * n'y est pas : le ticket quitte la file quand la consultation commence, et
	 * c'est ce moment-là qui remonte tout le monde d'un cran. Cette liste doit
	 * rester identique au prédicat de l'index partiel {@code ticket_file}.
	 */
	public static final Set<Statut> OUVERTS =
			Collections.unmodifiableSet(EnumSet.of(EN_ATTENTE, PREVENU, APPELE, ABSENT));

	public static final Set<Statut> TERMINAUX =
			Collections.unmodifiableSet(EnumSet.of(TERMINE, PARTI, ANNULE));

	private static final Map<Statut, Set<Statut>> SUIVANTS = new EnumMap<>(Statut.class);

	static {
		SUIVANTS.put(EN_ATTENTE, EnumSet.of(PREVENU, APPELE, PARTI, ANNULE));
		SUIVANTS.put(PREVENU, EnumSet.of(APPELE, PARTI));
		SUIVANTS.put(APPELE, EnumSet.of(EN_CONSULTATION, ABSENT, PARTI));
		SUIVANTS.put(ABSENT, EnumSet.of(APPELE, EN_CONSULTATION, PARTI));
		SUIVANTS.put(EN_CONSULTATION, EnumSet.of(TERMINE));
		SUIVANTS.put(TERMINE, EnumSet.noneOf(Statut.class));
		SUIVANTS.put(PARTI, EnumSet.noneOf(Statut.class));
		SUIVANTS.put(ANNULE, EnumSet.noneOf(Statut.class));
	}

	public boolean ouvert() {
		return OUVERTS.contains(this);
	}

	public boolean terminal() {
		return TERMINAUX.contains(this);
	}

	public boolean peutAllerVers(Statut cible) {
		return SUIVANTS.get(this).contains(cible);
	}

	public Set<Statut> suivants() {
		return Collections.unmodifiableSet(SUIVANTS.get(this));
	}
}
