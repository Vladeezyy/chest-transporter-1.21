package dev.vladeezyy.chesttransporter.neoforge;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class NeoForgePlatform implements Platform {
  /** Deferred registers per registry, filled by the common code before {@link #registerAll} */
  private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

  static Platform INSTANCE() {
    return Platform.INSTANCE;
  }

  static void registerAll(IEventBus modBus) {
    REGISTERS.values().forEach(register -> register.register(modBus));
  }

  @Override
  public String name() {
    return "neoforge";
  }

  @Override
  public Path configDir() {
    return FMLPaths.CONFIGDIR.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
    DeferredRegister<R> register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry,
      key -> DeferredRegister.create((ResourceKey<? extends Registry<R>>) key, ChestTransporter.MOD_ID));
    return register.register(name, factory);
  }

  @Override
  public boolean mayBreak(Level level, BlockPos pos, BlockState state, Player player) {
    return !NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, pos, state, player)).isCanceled();
  }

  @Override
  public PlaceGuard beforePlace(Level level, BlockPos pos) {
    BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
    return (against, player) -> {
      if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, against, player)).isCanceled()) {
        snapshot.restore(Block.UPDATE_ALL_IMMEDIATE);
        return false;
      }
      return true;
    };
  }

  @Override
  public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
    PacketDistributor.sendToPlayer(player, payload);
  }
}
