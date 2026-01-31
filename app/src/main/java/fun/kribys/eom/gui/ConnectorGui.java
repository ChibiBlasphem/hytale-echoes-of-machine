package fun.kribys.eom.gui;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import fun.kribys.eom.EOMPlugin;
import fun.kribys.eom.components.ConnectorComponent;
import fun.kribys.eom.components.ConnectorComponent.ConnectorMode;
import fun.kribys.eom.core.Connector;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ConnectorGui extends InteractiveCustomUIPage<ConnectorGui.Bindings> {
    private final Ref<ChunkStore> blockRef;

    public ConnectorGui(@Nonnull PlayerRef playerRef, Ref<ChunkStore> blockRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Bindings.CODEC);
        this.blockRef = blockRef;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder uiCmdBuilder,
            @Nonnull UIEventBuilder uiEventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        uiCmdBuilder.append("Pages/ConnectorUI.ui");

        ObjectArrayList<DropdownEntryInfo> modes = new ObjectArrayList<>();
        modes.add(new DropdownEntryInfo(LocalizableString.fromString("No mode"), ConnectorMode.STR_UNDEFINED));
        modes.add(new DropdownEntryInfo(LocalizableString.fromString("Receive"), ConnectorMode.STR_RECEIVING));
        modes.add(new DropdownEntryInfo(LocalizableString.fromString("Emit"), ConnectorMode.STR_EMITTING));

        uiCmdBuilder.set("#ModeField #Input.Entries", modes);

        ObjectArrayList<DropdownEntryInfo> outputConnectors = new ObjectArrayList<>();
        outputConnectors.add(new DropdownEntryInfo(LocalizableString.fromString("No connector"), ""));
        for (Connector coreConn : EOMPlugin.service().getConnectors().values()) {
            Store<ChunkStore> storeChunk = this.blockRef.getStore();
            ConnectorComponent conn = ConnectorComponent.get(storeChunk, coreConn.getPosition());

            if (conn != null && conn.getMode() == ConnectorMode.RECEIVING) {
                outputConnectors.add(new DropdownEntryInfo(LocalizableString.fromString(coreConn.getId()), coreConn.getId()));
            }
        }
        uiCmdBuilder.set("#OutputConnectorField #Input.Entries", outputConnectors);

        EventData saveEventData = new EventData()
                .append("Action", "Save")
                .append("@Name", "#NameField #Input.Value")
                .append("@Mode", "#ModeField #Input.Value")
                .append("@Output", "#OutputConnectorField #Input.Value");

        ConnectorComponent connector = this.blockRef.getStore().getComponent(this.blockRef, ConnectorComponent.getComponentType());
        if (connector == null) {
            return;
        }

        String name = connector.getName();
        ConnectorMode connectorMode = connector.getMode();
        String linkedConnectorName = connector.getLinkedConnectorName();
        uiCmdBuilder
                .set("#NameField #Input.Value", name != null ? name : "")
                .set("#ModeField #Input.Value", connectorMode.toString())
                .set("#OutputConnectorField #Input.Value", linkedConnectorName != null ? linkedConnectorName : "");
        if (connectorMode == ConnectorMode.EMITTING) {
            uiCmdBuilder.set("#OutputConnectorField.Visible", true);
            uiCmdBuilder.setObject("#Container.Anchor", getContainerAnchor(true));
        }

        uiEventBuilder
                .addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModeField #Input", EventData.of("@Mode", "#ModeField #Input.Value"), false)
                .addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", saveEventData, false)
                .addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", EventData.of("Action", "Cancel"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Bindings bindings) {
        UICommandBuilder uiCmdBuilder = new UICommandBuilder();

        if (bindings.action == null && bindings.mode != null) {
            boolean isVisible = bindings.mode.equals("Emit");
            uiCmdBuilder.set("#OutputConnectorField.Visible", isVisible);
            uiCmdBuilder.setObject("#Container.Anchor", getContainerAnchor(isVisible));

            sendUpdate(uiCmdBuilder, false);
            return;
        }

        if (bindings.action != null) {
            if (bindings.action.equals("Save")) {
                BlockModule.BlockStateInfo blockStateInfo = this.blockRef.getStore().getComponent(this.blockRef, BlockModule.BlockStateInfo.getComponentType());
                if (blockStateInfo == null) {
                    return;
                }

                ConnectorComponent connector = this.blockRef.getStore().getComponent(this.blockRef, ConnectorComponent.getComponentType());
                if (connector == null) {
                    return;
                }

                Store<ChunkStore> storeChunk = this.blockRef.getStore();
                WorldChunk worldChunk = storeChunk.getComponent(blockStateInfo.getChunkRef(), WorldChunk.getComponentType());
                if (worldChunk == null) {
                    return;
                }

                String oldName = connector.getName();
                ConnectorMode oldMode = connector.getMode();

                // If name is empty
                if (bindings.name == null || bindings.name.isEmpty()) {
                    uiCmdBuilder.set("#NameField #Error.Text", "Name is required");
                    uiCmdBuilder.set("#NameField #Error.Visible", true);
                    sendUpdate(uiCmdBuilder, false);
                    return;
                }

                // If there is a change
                if (!bindings.name.equalsIgnoreCase(oldName)) {
                    boolean alreadyExists = EOMPlugin.service().getConnectors().containsKey(bindings.name.toLowerCase());
                    if (alreadyExists) {
                        uiCmdBuilder.set("#NameField #Error.Text", "Name already exists");
                        uiCmdBuilder.set("#NameField #Error.Visible", true);
                        sendUpdate(uiCmdBuilder);
                        return;
                    }
                }

                if (oldName != null && !oldName.isEmpty()) {
                    EOMPlugin.service().getConnectors().remove(oldName.toLowerCase());
                }

                UUID worldId = worldChunk.getWorld().getWorldConfig().getUuid();

                ConnectorMode newMode = ConnectorMode.fromString(bindings.mode);
                connector.setName(bindings.name);
                connector.setMode(newMode);

                connector.setLinkedConnectorName(newMode != ConnectorMode.EMITTING || bindings.output.isEmpty() ? null : bindings.output);

                Connector coreConnector = new Connector(bindings.name, worldId.toString(), connector.getPosition());
                EOMPlugin.service().getConnectors().put(bindings.name.toLowerCase(), coreConnector);
                EOMPlugin.service().saveData();

                String newState = "default";
                if (connector.isValid(storeChunk)) {
                    String modeName = connector.getMode().toString();
                    newState = modeName.isEmpty() ? "Off" : modeName;
                } else {
                    newState = "Error";
                }

                int index = blockStateInfo.getIndex();
                int targetLocalX = ChunkUtil.xFromBlockInColumn(index);
                int targetLocalY = ChunkUtil.yFromBlockInColumn(index);
                int targetLocalZ = ChunkUtil.zFromBlockInColumn(index);

                BlockType blockType = worldChunk.getBlockType(targetLocalX, targetLocalY, targetLocalZ);
                if (blockType != null) {
                    String currentState = blockType.getStateForBlock(blockType);

                    if (currentState == null || !currentState.equals(newState)) {
                        BlockType variantBlockType = blockType.getBlockForState(newState);
                        if (variantBlockType != null) {
                            worldChunk.setBlockInteractionState(targetLocalX, targetLocalY, targetLocalZ, variantBlockType, newState, true);
                        }
                    }
                }

                if (oldMode != newMode && newMode != ConnectorMode.UNDEFINED) {
                    int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), targetLocalX);
                    int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), targetLocalZ);

                    worldChunk.getWorld().execute(() -> {
                        worldChunk.setTicking(worldX, targetLocalY, worldZ, true);
                    });
                }

                blockStateInfo.markNeedsSaving();
            }

            this.close();
        }
    }

    private Anchor getContainerAnchor(boolean isOutputConnectorFieldVisible) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(500));
        anchor.setHeight(Value.of(isOutputConnectorFieldVisible ? 330 : 280));
        return anchor;
    }

    public static class Bindings {
        public static final BuilderCodec<Bindings> CODEC;

        private String name;
        private String mode;
        private String output;
        private String action;

        static {
            CODEC = BuilderCodec.builder(Bindings.class, Bindings::new)
                    .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, (d) -> d.name).add()
                    .append(new KeyedCodec<>("@Mode", Codec.STRING), (d, v) -> d.mode = v, (d) -> d.mode).add()
                    .append(new KeyedCodec<>("@Output", Codec.STRING), (d, v) -> d.output = v, (d) -> d.output).add()
                    .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, (d) -> d.action).add()
                    .build();
        }
    }
}
