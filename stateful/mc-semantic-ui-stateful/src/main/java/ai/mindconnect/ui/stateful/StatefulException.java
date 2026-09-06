package ai.mindconnect.ui.stateful;

/** Base of the failures the engine reports; the web layer maps each to a status. */
public class StatefulException extends RuntimeException {

    public StatefulException(String message) {
        super(message);
    }

    public StatefulException(String message, Throwable cause) {
        super(message, cause);
    }

    /** No such instance for this owner — unknown, expired, or somebody else's. Web: 404 / a fresh instance. */
    public static class UnknownInstance extends StatefulException {
        private final String route;

        public UnknownInstance(String instanceId, String route) {
            super("no view instance " + instanceId);
            this.route = route;
        }

        /** The route the client should reopen, when the address told us; else {@code null}. */
        public String route() {
            return route;
        }
    }

    /** The instance changed under us twice in a row; the client should reload. Web: 409. */
    public static class Stale extends StatefulException {
        public Stale(String instanceId) {
            super("view instance " + instanceId + " was modified concurrently");
        }
    }

    /** The event names a node/event that the last render did not bind. Web: 400. */
    public static class UnboundEvent extends StatefulException {
        public UnboundEvent(String nodeId, String event) {
            super("no handler bound for " + nodeId + "/" + event);
        }
    }

    /** No view answers to this route. Web: 404. */
    public static class UnknownRoute extends StatefulException {
        public UnknownRoute(String route) {
            super("no view registered for route " + route);
        }
    }

    /** A handler threw. Web: 500 with an error toast; the state is unchanged. */
    public static class HandlerFailed extends StatefulException {
        public HandlerFailed(String nodeId, String event, Throwable cause) {
            super("handler for " + nodeId + "/" + event + " failed: " + cause.getMessage(), cause);
        }
    }
}
