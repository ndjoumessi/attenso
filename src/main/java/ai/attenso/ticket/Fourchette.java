package ai.attenso.ticket;

/**
 * L'attente est annoncée en fourchette, jamais comme une promesse. Une minute
 * précise qui se révèle fausse coûte plus de confiance qu'une fourchette large
 * qui se révèle juste.
 */
public record Fourchette(long minMinutes, long maxMinutes) {
}
