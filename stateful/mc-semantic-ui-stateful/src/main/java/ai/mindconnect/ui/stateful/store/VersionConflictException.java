package ai.mindconnect.ui.stateful.store;

/** An {@link ViewStateStore#update} found a different version than the caller expected. */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String instanceId, long expected, long actual) {
        super("view instance " + instanceId + ": expected version " + expected + " but found " + actual);
    }
}
