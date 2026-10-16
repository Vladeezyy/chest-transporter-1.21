package dev.vladeezyy.chesttransporter.neoforge;

import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ConfigSyncPayload;
import dev.vladeezyy.chesttransporter.MinecartTransport;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.Transport;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ChestTransporter.MOD_ID)
public final class ChestTransporterNeoForge {
  public ChestTransporterNeoForge(IEventBus modBus) {
    ChestTransporter.init();
    NeoForgePlatform.registerAll(modBus);
    modBus.addListener(ChestTransporterNeoForge::creativeTab);
    modBus.addListener(ChestTransporterNeoForge::payloads);
    modBus.addListener(DataGen::gather);
    NeoForge.EVENT_BUS.addListener(ChestTransporterNeoForge::useBlock);
    NeoForge.EVENT_BUS.addListener(ChestTransporterNeoForge::useEntity);
    NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> CarryEffects.tick(event.getEntity()));
    NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> TransporterConfig.loadServer());
    NeoForge.EVENT_BUS.addListener(ChestTransporterNeoForge::loggedIn);
    if (FMLEnvironment.dist.isClient()) {
      NeoForgeClient.init(modBus);
    }
  }

  /** Before the clicked block's own use, so a chest is picked up instead of opened */
  private static void useBlock(PlayerInteractEvent.RightClickBlock event) {
    InteractionResult result = Transport.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
    if (result != InteractionResult.PASS) {
      event.setCanceled(true);
      event.setCancellationResult(result);
    }
  }

  private static void useEntity(PlayerInteractEvent.EntityInteract event) {
    InteractionResult result = MinecartTransport.onUseEntity(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget());
    if (result != InteractionResult.PASS) {
      event.setCanceled(true);
      event.setCancellationResult(result);
    }
  }

  private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      NeoForgePlatform.INSTANCE().sendToPlayer(player, new ConfigSyncPayload(TransporterConfig.server()));
    }
  }

  private static void payloads(RegisterPayloadHandlersEvent event) {
    event.registrar("1").optional().playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC.cast(),
      (payload, context) -> TransporterConfig.receive(payload.values()));
  }

  private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
      for (TransporterTier tier : TransporterTier.values()) {
        event.accept(ModRegistry.truck(tier));
      }
    }
  }
}
