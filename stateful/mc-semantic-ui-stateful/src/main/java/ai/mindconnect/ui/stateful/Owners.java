package ai.mindconnect.ui.stateful;

/**
 * How an instance's owner is spelled: the principal's name when there is
 * one, else a marker built from the HTTP session so anonymous users still
 * cannot reach each other's instances.
 */
public final class Owners {

    public static final String SESSION_PREFIX = "session:";

    private Owners() {
    }

    public static String of(String principalName, String sessionId) {
        if (principalName != null && !principalName.isBlank()) return principalName;
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("an anonymous owner needs a session id");
        }
        return SESSION_PREFIX + sessionId;
    }
}
