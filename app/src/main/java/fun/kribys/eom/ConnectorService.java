package fun.kribys.eom;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;

public class ConnectorService {
    public static record Key(UUID worldId, int x, int y, int z) {
    }

    public static record Value(BlockFace face, Vector3i targetPos) {
    }

    private static ConnectorService instance;

    private ConcurrentHashMap<Key, Value> pendingConnectors = new ConcurrentHashMap<>();

    public ConnectorService() {
        instance = this;
    }

    public Value take(Key placed) {
        return this.pendingConnectors.remove(placed);
    }

    public void put(Key placed, Value value) {
        this.pendingConnectors.put(placed, value);
    }

    public static ConnectorService get() {
        return instance;
    }
}
