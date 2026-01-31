package fun.kribys.eom.components;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import fun.kribys.eom.EOMPlugin;
import fun.kribys.eom.core.Connector;

public class ConnectorComponent implements Component<ChunkStore> {
    public static String COMPONENT_ID = "MachineConnector";

    public static BuilderCodec<ConnectorComponent> CODEC;
    public static int ITEM_PER_SECOND = 1;

    @Nullable
    private String name;
    private Vector3i position;
    private ConnectorMode mode = ConnectorMode.UNDEFINED;
    @Nullable
    private Vector3i targetBlock;
    @Nullable
    private String linkedConnectorName;
    private float cumulatedTime = 0;

    @Override
    public Component<ChunkStore> clone() {
        ConnectorComponent copy = new ConnectorComponent();
        copy.name = this.name;
        copy.position = this.position;
        copy.mode = this.mode;
        copy.targetBlock = this.targetBlock;
        copy.linkedConnectorName = this.linkedConnectorName;
        return copy;
    }

    public void setMode(ConnectorMode mode) {
        this.mode = mode;
    }

    public ConnectorMode getMode() {
        return this.mode;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getName() {
        return this.name;
    }

    public void setPosition(Vector3i position) {
        this.position = position;
    }

    public Vector3i getPosition() {
        return this.position;
    }

    public void setTargetBlock(Vector3i targetBlock) {
        this.targetBlock = targetBlock;
    }

    @Nullable
    public Vector3i getTargetBlock() {
        return this.targetBlock;
    }

    public void setLinkedConnectorName(String connectorName) {
        this.linkedConnectorName = connectorName;
    }

    @Nullable
    public String getLinkedConnectorName() {
        return this.linkedConnectorName;
    }

    public boolean isValid(Store<ChunkStore> store) {
        if (this.mode == ConnectorMode.EMITTING) {
            if (this.linkedConnectorName == null || this.linkedConnectorName.isEmpty()) {
                return false;
            }
            Connector coreConnector = EOMPlugin.service().getConnectors().get(this.linkedConnectorName.toLowerCase());
            if (coreConnector == null) {
                return false;
            }
            ConnectorComponent linkedConnector = this.getLinkedConnector(store);
            if (linkedConnector == null || linkedConnector.getMode() != ConnectorMode.RECEIVING) {
                return false;
            }
        }
        return true;
    }

    public boolean processTick(float dt, Store<ChunkStore> store) {
        if (this.getMode() != ConnectorMode.EMITTING || !this.isValid(store)) {
            return false;
        }

        ConnectorComponent linkedConnector = this.getLinkedConnector(store);
        if (linkedConnector == null) {
            EOMPlugin.logger().atWarning().log("Could not find linked connector '%s'", this.linkedConnectorName);
            return false;
        }

        if (this.cumulatedTime > 1) {
            ConnectedContainers selfConnectedContainers = this.getConnectedContainers(store);
            if (selfConnectedContainers == null) {
                EOMPlugin.logger().atWarning().log("Could not retrieve self connected block state for '%s'", this.name);
                return false;
            }
            ItemContainer selfOutputContainer = selfConnectedContainers.output();

            ConnectedContainers linkedConnectedContainers = linkedConnector.getConnectedContainers(store);
            if (linkedConnectedContainers == null) {
                EOMPlugin.logger().atWarning().log("Could not retrieve linked connected block state for '%s'", this.linkedConnectorName);
                return false;
            }
            ItemContainer linkedInputContainer = linkedConnectedContainers.input();

            float rest = this.cumulatedTime % 1;
            int produceCount = Float.valueOf(this.cumulatedTime - rest).intValue() * ITEM_PER_SECOND;
            this.cumulatedTime = rest;

            int remaining = produceCount;
            for (short i = 0; i < selfOutputContainer.getCapacity() && remaining > 0; ++i) {
                ItemStack initialItemStack = selfOutputContainer.getItemStack(i);
                if (initialItemStack == null) {
                    continue;
                }

                int takeAmount = Math.min(remaining, initialItemStack.getQuantity());
                ItemStack stackToMove = initialItemStack.withQuantity(takeAmount);
                if (stackToMove == null) {
                    continue;
                }
                ItemStackTransaction tx = linkedInputContainer.addItemStack(stackToMove, false, false, true);

                int addedAmount = takeAmount - (tx.getRemainder() == null ? 0 : tx.getRemainder().getQuantity());
                if (addedAmount <= 0) {
                    continue;
                }

                ItemStack inputStackRemainder = initialItemStack.withQuantity(initialItemStack.getQuantity() - addedAmount);
                selfOutputContainer.setItemStackForSlot(i, inputStackRemainder == null ? ItemStack.EMPTY : inputStackRemainder, false);

                remaining -= addedAmount;
            }
        } else {
            this.cumulatedTime += dt;
        }

        return true;
    }

    public ConnectorComponent getLinkedConnector(Store<ChunkStore> store) {
        Connector coreConnector = EOMPlugin.service().getConnectors().get(this.linkedConnectorName.toLowerCase());
        if (coreConnector == null) {
            return null;
        }
        return get(store, coreConnector.getPosition());
    }

    @Nullable
    public ConnectedContainers getConnectedContainers(Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        BlockState blockState = world.getState(this.targetBlock.x, this.targetBlock.y, this.targetBlock.z, true);

        if ((blockState instanceof ItemContainerBlockState icState)) {
            ItemContainer inputContainer;
            ItemContainer outputContainer;

            if ((icState instanceof ProcessingBenchState benchState)) {
                CombinedItemContainer container = benchState.getItemContainer();
                inputContainer = new CombinedItemContainer(container.getContainer(0), container.getContainer(1));
                outputContainer = container.getContainer(2);
            } else {
                inputContainer = icState.getItemContainer();
                outputContainer = icState.getItemContainer();
            }

            return new ConnectedContainers(inputContainer, outputContainer, blockState);
        }

        return null;
    }

    public String toString() {
        return String.format("ConnectorComponent{ Name: %s, Mode: %s }", name, mode.toString());
    }

    @Nullable
    public static ConnectorComponent get(@Nonnull Store<ChunkStore> store, @Nonnull Vector3i position) {
        WorldChunk worldChunk = store.getExternalData().getWorld().getChunk(ChunkUtil.indexChunkFromBlock(position.x, position.z));
        if (worldChunk == null) {
            return null;
        }

        Ref<ChunkStore> linkedRef = worldChunk.getBlockComponentEntity(position.x, position.y, position.z);
        if (linkedRef == null) {
            return null;
        }

        return store.getComponent(linkedRef, ConnectorComponent.getComponentType());
    }

    public static ComponentType<ChunkStore, ConnectorComponent> getComponentType() {
        return EOMPlugin.getConnectorComponentType();
    }

    static {
        CODEC = BuilderCodec.builder(ConnectorComponent.class, ConnectorComponent::new)
                .append(new KeyedCodec<>("Name", Codec.STRING), (conn, val) -> conn.name = val, (conn) -> conn.name)
                .add()
                .append(new KeyedCodec<>("Position", Vector3i.CODEC), (conn, val) -> conn.position = val, (conn) -> conn.position)
                .add()
                .append(
                        new KeyedCodec<>("Direction", new EnumCodec<>(ConnectorMode.class)),
                        (conn, val) -> conn.mode = val,
                        (conn) -> conn.mode
                )
                .documentation("Defines whether the connector is receiving or emitting an signal")
                .add()
                .append(
                        new KeyedCodec<>("TargetBlock", Vector3i.CODEC),
                        (conn, val) -> conn.targetBlock = val,
                        (conn) -> conn.targetBlock
                )
                .add()
                .append(
                        new KeyedCodec<>("LinkedConnector", Codec.STRING),
                        (conn, val) -> conn.linkedConnectorName = val,
                        (conn) -> conn.linkedConnectorName
                )
                .add()
                .build();
    }

    public static enum ConnectorMode {
        RECEIVING(-1),
        UNDEFINED(0),
        EMITTING(1);

        public static final String STR_RECEIVING = "Receive";
        public static final String STR_EMITTING = "Emit";
        public static final String STR_UNDEFINED = "";

        private final int value;

        private ConnectorMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        public String toString() {
            return switch (this) {
                case RECEIVING -> STR_RECEIVING;
                case EMITTING -> STR_EMITTING;
                default -> STR_UNDEFINED;
            };
        }

        public static ConnectorMode fromString(String value) {
            return switch (value) {
                case STR_RECEIVING -> RECEIVING;
                case STR_EMITTING -> EMITTING;
                default -> UNDEFINED;
            };
        }
    }

    public static record ConnectedContainers(ItemContainer input, ItemContainer output, BlockState state) {
    }
}
