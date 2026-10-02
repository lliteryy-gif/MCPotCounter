package ru.litery.potioncounter;

import java.util.*;

/** Client-thread session data. A reset must NOT forget observed projectile UUIDs. */
public final class CounterStore {
    private final Map<UUID, Integer> counts = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> seen = new HashSet<>();
    public void remember(UUID player, String name) { names.put(player, name); }
    public boolean record(UUID projectile, UUID player, String name) {
        remember(player, name);
        if (!seen.add(projectile)) return false;
        counts.merge(player, 1, Integer::sum);
        return true;
    }
    public int count(UUID player) { return counts.getOrDefault(player, 0); }
    public void reset(UUID player) { counts.remove(player); }
    public void resetAll() { counts.clear(); }
    public Set<String> names() { return new TreeSet<>(names.values()); }
    public List<UUID> find(String name) {
        return names.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name))
            .map(Map.Entry::getKey).toList();
    }
    public void endSession() { counts.clear(); names.clear(); seen.clear(); }
}
