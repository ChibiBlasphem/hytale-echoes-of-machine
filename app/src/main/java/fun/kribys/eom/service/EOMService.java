package fun.kribys.eom.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

import org.bson.BsonArray;
import org.bson.BsonDocument;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.BsonUtil;

import fun.kribys.eom.EOMPlugin;
import fun.kribys.eom.core.Connector;

/**
 * Service class to manage shared EOM plugin data (like connectors)
 */
public class EOMService {
    @Nonnull
    private final AtomicBoolean loaded = new AtomicBoolean();
    @Nonnull
    private final ReentrantLock saveLock = new ReentrantLock();
    @Nonnull
    private final AtomicBoolean postSaveRedo = new AtomicBoolean(false);

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();

    public void loadData() {
        BsonDocument document = null;
        Path dataPath = getFilePath();

        if (Files.exists(dataPath)) {
            document = BsonUtil.readDocument(dataPath).join();
        }

        if (document != null) {
            BsonArray bsonConnectors = document.getArray("Connectors");
            this.loadConnectors(bsonConnectors);

            EOMPlugin.logger().atInfo().log("Loaded %d connectors", connectors.size());
        } else {
            EOMPlugin.logger().atInfo().log("No data loaded (no eom.json found)");
        }

        this.loaded.set(true);
    }

    public void saveData() {
        if (this.saveLock.tryLock()) {
            try {
                this._saveData();
            } catch (Throwable t) {
                EOMPlugin.logger().atSevere().withCause(t).log("Failed to save data:");
            } finally {
                this.saveLock.unlock();
            }

            if (this.postSaveRedo.getAndSet(false)) {
                this.saveData();
            }
        } else {
            this.postSaveRedo.set(true);
        }
    }

    public Map<String, Connector> getConnectors() {
        return this.connectors;
    }

    private Path getFilePath() {
        return Universe.get().getPath().resolve("eom.json");
    }

    private void _saveData() {
        Connector[] connectors = this.connectors.values().toArray(Connector[]::new);
        BsonDocument document = new BsonDocument("Connectors", Connector.ARRAY_CODEC.encode(connectors));
        Path dataPath = getFilePath();

        BsonUtil.writeDocument(dataPath, document).join();
        EOMPlugin.logger().atInfo().log("Data saved! Connectors: %d", connectors.length);
    }

    private void loadConnectors(BsonArray bsonConnectors) {
        this.connectors.clear();

        for (Connector connector : Connector.ARRAY_CODEC.decode(bsonConnectors, new ExtraInfo())) {
            this.connectors.put(connector.getId().toLowerCase(), connector);
        }
    }
}
