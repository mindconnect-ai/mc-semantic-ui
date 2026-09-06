package ai.mindconnect.ui.stateful.store;

import java.time.Instant;
import java.util.Optional;

/**
 * Where view instances live between requests. Implementations are expected
 * to be shared by every node of a cluster (a database, Redis); the in-memory
 * one is for development and single-node deployments.
 *
 * <p>Concurrency contract: {@link #update} is compare-and-set on
 * {@link ViewInstance#getVersion()}. The engine serialises events per
 * instance within one JVM and relies on this check across JVMs.
 */
public interface ViewStateStore {

    Optional<ViewInstance> load(String id);

    /** Stores a new instance. The id must not exist yet. */
    void insert(ViewInstance instance);

    /**
     * Replaces the stored instance when its version still equals
     * {@code expectedVersion}; the instance passed in carries the new
     * version. Throws {@link VersionConflictException} otherwise.
     */
    void update(ViewInstance instance, long expectedVersion);

    /** Refreshes {@link ViewInstance#getTouchedAt()} without changing anything else. */
    void touch(String id, Instant at);

    void delete(String id);

    /** Removes instances not touched since {@code idleBefore}; returns how many. */
    int expireIdle(Instant idleBefore);

    /**
     * Keeps the {@code keep} most recently touched instances of {@code owner}
     * and removes the rest; returns how many went. Bounds what one user can
     * cost by opening tabs.
     */
    int trimOwner(String owner, int keep);
}
