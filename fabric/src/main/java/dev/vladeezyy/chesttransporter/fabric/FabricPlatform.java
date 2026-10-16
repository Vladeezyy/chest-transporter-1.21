package dev.vladeezyy.chesttransporter.fabric;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.platform.Platform;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.function.Supplier;

public final class FabricPlatform implements Platform {
  @Override
  public String name() {
    return "fabric";
  }

  @Override
  public Path configDir() {
    return FabricLoader.getInstance().getConfigDir();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
    Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.get(registry.location());
    T value = Registry.register(target, ChestTransporter.id(name), factory.get());
    return () -> value;
  }

  /** Claim mods on Fabric cancel this event */
  @Override
  public boolean mayBreak(Level level, BlockPos pos, BlockState state, Player player) {
    return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
  }

  /** Fabric has no place event: the vanilla build checks (mayUseItemAt, mayInteract) done before decide */
  @Override
  public PlaceGuard beforePlace(Level level, BlockPos pos) {
    return (against, player) -> true;
  }

  @Override
  public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
    ServerPlayNetworking.send(player, payload);
  }
}
