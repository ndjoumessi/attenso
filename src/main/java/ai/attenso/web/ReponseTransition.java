package ai.attenso.web;

import java.util.UUID;

public record ReponseTransition(UUID id, int numero, String statut, int position) {
}
