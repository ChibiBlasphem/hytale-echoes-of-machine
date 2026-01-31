package fun.kribys.eom;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import fun.kribys.eom.components.ConnectorComponent;
import fun.kribys.eom.service.EOMService;

public class EOMPlugin extends JavaPlugin {
    private static EOMPlugin instance;
    private ComponentType<ChunkStore, ConnectorComponent> connectorComponentType;
    private ConnectorService connectorService;
    private EOMService service;

    public EOMPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        connectorService = new ConnectorService();
        service = new EOMService();
    }

    @Override
    public void setup() {
        this.getCodecRegistry(Interaction.CODEC)
                .register("PlaceConnector", EOMInteractions.PlaceConnectorInteraction.class, EOMInteractions.PlaceConnectorInteraction.CODEC)
                .register("UseConnector", EOMInteractions.UseConnectorInteraction.class, EOMInteractions.UseConnectorInteraction.CODEC);

        this.connectorComponentType = this.getChunkStoreRegistry().registerComponent(ConnectorComponent.class, ConnectorComponent.COMPONENT_ID, ConnectorComponent.CODEC);

        this.getChunkStoreRegistry().registerSystem(new EOMSystems.OnConnectorAdded());
        this.getChunkStoreRegistry().registerSystem(new EOMSystems.Ticking());

        this.getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> this.service.loadData());
    }

    public static EOMPlugin get() {
        return instance;
    }

    public static HytaleLogger logger() {
        return instance.getLogger();
    }

    public static EOMService service() {
        return instance.service;
    }

    public static ConnectorService connectorService() {
        return instance.connectorService;
    }

    public static ComponentType<ChunkStore, ConnectorComponent> getConnectorComponentType() {
        return instance.connectorComponentType;
    }
}
