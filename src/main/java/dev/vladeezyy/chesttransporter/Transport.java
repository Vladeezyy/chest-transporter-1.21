package dev.vladeezyy.chesttransporter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.Optional;

/** Picking blocks up with their block entity data and putting them down again */
public final class Transport {
  private Transport() {}

  /** Outcome of a pick up: the carried block, or why not */
  public record Result(@Nullable CarriedBlock carried, Component message) {
    static Result refuse(String key) {
      return new Result(null, Component.translatable("message.chest_transporter." + key));
    }
  }

  /** Cheap check for the client: the block may be picked up (the server decides) */
  public static boolean looksTransportable(BlockState state) {
    return (state.is(ModTags.TRANSPORTABLE) || state.is(ModTags.SPAWNERS)) && !state.is(ModTags.NOT_TRANSPORTABLE);
  }

  public static Result pickUp(Level level, BlockPos pos, Player player, TransporterTier tier) {
    BlockState state = level.getBlockState(pos);
    if (!looksTransportable(state)) {
      return Result.refuse("not_transportable");
    }
    if (state.is(ModTags.SPAWNERS) && !state.is(ModTags.TRANSPORTABLE) && !TransporterConfig.canTakeSpawner(tier)) {
      return Result.refuse(TransporterConfig.get(TransporterConfig.PICKUP_SPAWNERS) ? "spawner_tier" : "spawner_disabled");
    }
    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (blockEntity == null) {
      return Result.refuse("not_transportable");
    }
    // spawn protection, adventure mode, claim mods (they cancel the break event)
    if (!player.mayBuild() || !level.mayInteract(player, pos)
        || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) {
      return Result.refuse("protected");
    }
    HolderLookup.Provider registries = level.registryAccess();
    CompoundTag data = blockEntity.saveWithoutMetadata(registries);
    if (isLocked(data) && !player.getAbilities().instabuild) {
      return Result.refuse("locked");
    }
    if (data.sizeInBytes() > TransporterConfig.maxDataBytes()) {
      return Result.refuse("too_heavy");
    }

    // a half of a double chest leaves as a single chest, the other half turns single by itself
    BlockState carriedState = state.hasProperty(ChestBlock.TYPE) ? state.setValue(ChestBlock.TYPE, ChestType.SINGLE) : state;
    // empty the block first, so removing it drops nothing
    if (blockEntity instanceof Container container) {
      container.clearContent();
    }
    level.removeBlock(pos, false);
    level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
    SoundType sound = state.getSoundType(level, pos, player);
    level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
    closeStaleMenus(level);
    return new Result(new CarriedBlock(carriedState, data), Component.empty());
  }

  /** The lock of a container (vanilla `Lock`, set with commands) */
  private static boolean isLocked(CompoundTag data) {
    return data.contains("Lock", Tag.TAG_STRING) && !data.getString("Lock").isEmpty();
  }

  /** Players who still had the removed container open lose the screen now, not on their next tick */
  private static void closeStaleMenus(Level level) {
    if (level instanceof ServerLevel serverLevel) {
      for (ServerPlayer player : serverLevel.players()) {
        if (player.containerMenu != player.inventoryMenu && !player.containerMenu.stillValid(player)) {
          player.closeContainer();
        }
      }
    }
  }

  /** Puts the carried block down where a block item would go; empty when done, else why not */
  public static Optional<Component> putDown(UseOnContext context, Player player, CarriedBlock carried) {
    Level level = context.getLevel();
    BlockPlaceContext place = new BlockPlaceContext(context);
    BlockPos pos = place.getClickedPos();
    Block block = carried.state().getBlock();
    if (!place.canPlace() || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()) || !level.mayInteract(player, pos)) {
      return Optional.of(refusal("no_room"));
    }
    BlockState state = block.getStateForPlacement(place);
    if (state == null) {
      state = carried.state();
    }
    if (!state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, CollisionContext.of(player))) {
      return Optional.of(refusal("no_room"));
    }
    BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
    if (!level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE)) {
      return Optional.of(refusal("no_room"));
    }
    BlockState against = level.getBlockState(context.getClickedPos());
    if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, against, player)).isCanceled()) {
      snapshot.restore(Block.UPDATE_ALL_IMMEDIATE);
      return Optional.of(refusal("protected"));
    }
    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (blockEntity != null) {
      blockEntity.loadWithComponents(carried.data(), level.registryAccess());
      blockEntity.setChanged();
    }
    BlockState placed = level.getBlockState(pos);
    level.sendBlockUpdated(pos, placed, placed, Block.UPDATE_ALL);
    level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
    SoundType sound = placed.getSoundType(level, pos, player);
    level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
    return Optional.empty();
  }

  static Component refusal(String key) {
    return Component.translatable("message.chest_transporter." + key);
  }
}
