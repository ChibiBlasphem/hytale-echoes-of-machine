package fun.kribys.eom;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import fun.kribys.eom.components.ConnectorComponent;
import fun.kribys.eom.components.ConnectorComponent.ConnectedContainers;
import fun.kribys.eom.components.ConnectorComponent.ConnectorMode;

public class EOMSystems {
    public static class OnConnectorAdded extends RefSystem<ChunkStore> {
        private static final Query<ChunkStore> QUERY;

        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> cmdBuf
        ) {
            BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            assert info != null;

            int localX = ChunkUtil.xFromBlockInColumn(info.getIndex());
            int localY = ChunkUtil.yFromBlockInColumn(info.getIndex());
            int localZ = ChunkUtil.zFromBlockInColumn(info.getIndex());

            WorldChunk wc = cmdBuf.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
            assert wc != null;

            ConnectorComponent connector = store.getComponent(ref, ConnectorComponent.getComponentType());
            assert connector != null;

            if (reason == AddReason.SPAWN) {
                UUID worldUuid = wc.getWorld().getWorldConfig().getUuid();
                int worldX = ChunkUtil.worldCoordFromLocalCoord(wc.getX(), localX);
                int worldY = localY;
                int worldZ = ChunkUtil.worldCoordFromLocalCoord(wc.getZ(), localZ);

                Vector3i connectorPosition = new Vector3i(worldX, worldY, worldZ);
                ConnectorService.Key key = new ConnectorService.Key(worldUuid, worldX, worldY, worldZ);
                ConnectorService.Value pendingConnector = EOMPlugin.connectorService().take(key);

                if (pendingConnector == null) {
                    EOMPlugin.logger().atWarning().log("No pending connector was found for block at %s", connectorPosition);
                    return;
                }

                connector.setTargetBlock(pendingConnector.targetPos());
                connector.setPosition(connectorPosition);

                info.markNeedsSaving();
            }

            Vector3i targetBlockPos = connector.getTargetBlock();
            if (targetBlockPos == null) {
                EOMPlugin.logger().atWarning().log("Couldn't find target position for '%s'", connector.getName());
                return;
            }
            
            long targetBlockChunkIndex = ChunkUtil.indexChunkFromBlock(targetBlockPos.x, targetBlockPos.z);
            WorldChunk targetBlockWc = wc.getWorld().getChunk(targetBlockChunkIndex);
            if (targetBlockWc == null) {
                EOMPlugin.logger().atWarning().log("Couldn't find target block's chunk for '%s', it will remain deactivated", connector.getName());
                return;
            }

            int worldX = ChunkUtil.worldCoordFromLocalCoord(wc.getX(), localX);
            int worldZ = ChunkUtil.worldCoordFromLocalCoord(wc.getZ(), localZ);
            
            wc.addKeepLoaded();
            wc.setTicking(worldX, localY, worldZ, true);
            targetBlockWc.addKeepLoaded();
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> cmdBuf
        ) {
            if (reason == RemoveReason.REMOVE) {
                BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
                assert info != null;

                ConnectorComponent connector = store.getComponent(ref, ConnectorComponent.getComponentType());
                assert connector != null;

                WorldChunk wc = cmdBuf.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
                assert wc != null;

                String connectorName = connector.getName();
                if (connectorName != null && !connectorName.isEmpty()) {
                    EOMPlugin.service().getConnectors().remove(connectorName.toLowerCase());
                    EOMPlugin.service().saveData();
                    connector.setName(null);
                }

                wc.removeKeepLoaded();
            }
        }

        static {
            QUERY = Query.and(BlockModule.BlockStateInfo.getComponentType(), ConnectorComponent.getComponentType());
        }
    }

    public static class Ticking extends EntityTickingSystem<ChunkStore> {
        public static final Query<ChunkStore> QUERY;

        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> archChunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> cmdBuf
        ) {
            BlockSection blocks = archChunk.getComponent(index, BlockSection.getComponentType());
            assert blocks != null;

            if (blocks.getTickingBlocksCountCopy() != 0) {
                ChunkSection section = archChunk.getComponent(index, ChunkSection.getComponentType());
                assert section != null;

                BlockComponentChunk blockComponentChunk = cmdBuf.getComponent(
                    section.getChunkColumnReference(),
                    BlockComponentChunk.getComponentType()
                );
                assert blockComponentChunk != null;

                blocks.forEachTicking(blockComponentChunk, cmdBuf, section.getY(), (bcc1, cb1, lx, ly, lz, blockId) -> {
                    Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(lx, ly, lz));
                    if (blockRef == null) {
                        return BlockTickStrategy.IGNORED;
                    }

                    ConnectorComponent connector = cb1.getComponent(blockRef, ConnectorComponent.getComponentType());
                    if (connector == null || connector.getMode() == ConnectorMode.UNDEFINED) {
                        return BlockTickStrategy.IGNORED;
                    }

                    WorldChunk worldChunk = cmdBuf.getComponent(section.getChunkColumnReference(), WorldChunk.getComponentType());
                    if (worldChunk == null) {
                        return BlockTickStrategy.IGNORED;
                    }

                    Vector3i targetBlockPos = connector.getTargetBlock();
                    if (targetBlockPos == null) {
                        EOMPlugin.logger().atWarning().log("Couldn't find target position for '%s'", connector.getName());
                        return BlockTickStrategy.IGNORED;
                    }

                    long targetBlockChunkIndex = ChunkUtil.indexChunkFromBlock(targetBlockPos.x, targetBlockPos.z);
                    WorldChunk targetBlockWc = worldChunk.getWorld().getChunk(targetBlockChunkIndex);
                    if (targetBlockWc == null) {
                        return BlockTickStrategy.IGNORED;
                    }                   

                    worldChunk.resetActiveTimer();
                    targetBlockWc.resetActiveTimer();

                    switch (connector.getMode()) {
                        case RECEIVING: {
                            this.processTickForReceiver(dt, store, connector);
                            return BlockTickStrategy.CONTINUE;
                        }
                        case EMITTING: {
                            boolean result = this.processTickFormEmitter(dt, store, connector);
                            String newState = result ? "Emit" : "Error";

                            BlockType blockType = worldChunk.getBlockType(lx, ly, lz);
                            if (blockType != null) {
                                String currentState = blockType.getStateForBlock(blockType);

                                if (currentState != null && !currentState.equals(newState)) {
                                    BlockType variantBlockType = blockType.getBlockForState(newState);
                                    if (variantBlockType != null) {
                                        worldChunk.setBlockInteractionState(lx, ly, lz, variantBlockType, newState, true);
                                    }
                                }
                            }

                            return BlockTickStrategy.CONTINUE;
                        }
                        default:
                            return BlockTickStrategy.IGNORED;
                    }
                });
            }
        }

        private boolean processTickForReceiver(
                float dt,
                @Nonnull Store<ChunkStore> store,
                @Nonnull ConnectorComponent connector
        ) {
            ConnectedContainers connectedContainer = connector.getConnectedContainers(store);
            if (connectedContainer == null || !(connectedContainer.state() instanceof ProcessingBenchState benchState)) {
                return false;
            }

            ItemContainer fuelContainer = benchState.getItemContainer().getContainer(0);
            if (fuelContainer.getCapacity() == 0) {
                return false;
            }

            ItemContainer inputContainer = benchState.getItemContainer().getContainer(1);
            if (!fuelContainer.isEmpty() && !inputContainer.isEmpty()) {
                benchState.setActive(true);
            }

            return true;
        }

        private boolean processTickFormEmitter(
                float dt,
                @Nonnull Store<ChunkStore> store,
                @Nonnull ConnectorComponent connector
        ) {
            return connector.processTick(dt, store);
        }

        static {
            QUERY = Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
        }
    }
}
