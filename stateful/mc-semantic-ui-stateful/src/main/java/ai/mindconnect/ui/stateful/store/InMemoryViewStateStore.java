package ai.mindconnect.ui.stateful.store;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ViewStateStore} in a map. Fine for development and for a single
 * node; every instance is lost on restart and invisible to other JVMs.
 */
public class InMemoryViewStateStore implements ViewStateStore {

    private final Map<String, ViewInstance> instances = new ConcurrentHashMap<>();

    @Override
    public Optional<ViewInstance> load(String id) {
        ViewInstance v = instances.get(id);
        return v == null ? Optional.empty() : Optional.of(v.copy());
    }

    @Override
    public void insert(ViewInstance instance) {
        ViewInstance previous = instances.putIfAbsent(instance.getId(), instance.copy());
        if (previous != null) {
            throw new IllegalStateException("view instance " + instance.getId() + " exists already");
        }
    }

    @Override
    public void update(ViewInstance instance, long expectedVersion) {
        instances.compute(instance.getId(), (id, current) -> {
            long actual = current == null ? -1 : current.getVersion();
            if (actual != expectedVersion) {
                throw new VersionConflictException(id, expectedVersion, actual);
            }
            return instance.copy();
        });
    }

    @Override
    public void touch(String id, Instant at) {
        instances.computeIfPresent(id, (k, v) -> {
            v.setTouchedAt(at);
            return v;
        });
    }

    @Override
    public void delete(String id) {
        instances.remove(id);
    }

    @Override
    public int expireIdle(Instant idleBefore) {
        int before = instances.size();
        instances.values().removeIf(v -> v.getTouchedAt() != null && v.getTouchedAt().isBefore(idleBefore));
        return before - instances.size();
    }

    @Override
    public int trimOwner(String owner, int keep) {
        if (owner == null || keep < 0) return 0;
        List<ViewInstance> mine = instances.values().stream()
                .filter(v -> owner.equals(v.getOwner()))
                .sorted(Comparator.comparing(ViewInstance::getTouchedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        int removed = 0;
        for (int i = keep; i < mine.size(); i++) {
            if (instances.remove(mine.get(i).getId()) != null) removed++;
        }
        return removed;
    }

    /** How many instances are held; for tests and diagnostics. */
    public int size() {
        return instances.size();
    }
}
