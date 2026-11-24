package ai.attenso.push;

/** Ce qu'un navigateur fournit pour recevoir des notifications poussées. */
public record AbonnementPush(String endpoint, String clePublique, String cleAuth) {
}
