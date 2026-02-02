package fun.kribys.eom;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import fun.kribys.eom.components.ConnectorComponent;
import fun.kribys.eom.gui.ConnectorGui;

public class EOMInteractions {
    public static final class UseConnectorInteraction extends SimpleBlockInteraction {
        public static final BuilderCodec<UseConnectorInteraction> CODEC;

        @Override
        protected void interactWithBlock(
                @Nonnull World world,
                @Nonnull CommandBuffer<EntityStore> cmdBuf,
                @Nonnull InteractionType type,
                @Nonnull InteractionContext ctx,
                @Nullable ItemStack stack,
                @Nonnull Vector3i targetBlockPos,
                @Nonnull CooldownHandler cooldownHandler
        ) {
            int x = targetBlockPos.getX();
            int z = targetBlockPos.getZ();

            WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
            if (worldChunk == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Ref<ChunkStore> blockRef = worldChunk.getBlockComponentEntity(x, targetBlockPos.getY(), z);
            if (blockRef == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            ConnectorComponent connector = chunkStore.getComponent(blockRef, ConnectorComponent.getComponentType());
            if (connector == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> playerEntityRef = ctx.getEntity();
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
            if (playerRef == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            ConnectorGui connectorGui = new ConnectorGui(playerRef, blockRef, CustomPageLifetime.CanDismiss);
            player.getPageManager().openCustomPage(playerEntityRef, store, connectorGui);
        }

        @Override
        protected void simulateInteractWithBlock(
                @Nonnull InteractionType type,
                @Nonnull InteractionContext ctx,
                @Nullable ItemStack stack,
                @Nonnull World world,
                @Nonnull Vector3i targetBlockPos
        ) {
        }

        static {
            CODEC = BuilderCodec.builder(UseConnectorInteraction.class, UseConnectorInteraction::new, SimpleBlockInteraction.CODEC).build();
        }
    }

    public static class PlaceConnectorInteraction extends SimpleBlockInteraction {
        public static final BuilderCodec<PlaceConnectorInteraction> CODEC;

        private Vector3i getDirection(BlockFace face) {
            return switch (face) {
                case Up -> new Vector3i(0, 1, 0);
                case Down -> new Vector3i(0, -1, 0);
                case East -> new Vector3i(1, 0, 0);
                case West -> new Vector3i(-1, 0, 0);
                case North -> new Vector3i(0, 0, -1);
                case South -> new Vector3i(0, 0, 1);
                default -> new Vector3i(0, 0, 0);
            };
        }

        @Override
        protected void interactWithBlock(
                @Nonnull World world,
                @Nonnull CommandBuffer<EntityStore> cmdBuf,
                @Nonnull InteractionType type,
                @Nonnull InteractionContext ctx,
                @Nullable ItemStack stack,
                @Nonnull Vector3i targetBlockPos,
                @Nonnull CooldownHandler cooldownHandler
        ) {
            InteractionSyncData clientState = ctx.getClientState();
            if (clientState == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            BlockPosition blockPos = clientState.blockPosition;
            if (blockPos == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            BlockFace face = clientState.blockFace;
            UUID worldId = world.getWorldConfig().getUuid();
            Vector3i blockPos3i = new Vector3i(blockPos.x, blockPos.y, blockPos.z);
            Vector3i connectorPosition = blockPos3i.add(getDirection(face));

            ConnectorService.Key key = new ConnectorService.Key(worldId, connectorPosition.x, connectorPosition.y, connectorPosition.z);
            ConnectorService.Value value = new ConnectorService.Value(face, targetBlockPos);
            EOMPlugin.connectorService().put(key, value);

            ctx.getState().state = InteractionState.Finished;
        }

        @Override
        protected void simulateInteractWithBlock(
                @Nonnull InteractionType type,
                @Nonnull InteractionContext ctx,
                @Nullable ItemStack stack,
                @Nonnull World world,
                @Nonnull Vector3i targetBlockPos
        ) {}

        static {
            CODEC = BuilderCodec.builder(PlaceConnectorInteraction.class, PlaceConnectorInteraction::new, SimpleBlockInteraction.CODEC).build();
        }
    }
}
