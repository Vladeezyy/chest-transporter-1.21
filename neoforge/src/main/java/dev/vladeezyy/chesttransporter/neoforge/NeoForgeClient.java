package dev.vladeezyy.chesttransporter.neoforge;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import dev.vladeezyy.chesttransporter.client.TransporterRenderer;
import dev.vladeezyy.chesttransporter.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class NeoForgeClient {
  private NeoForgeClient() {}

  public static void init(IEventBus modBus) {
    ClientPlatform.set(tier -> Minecraft.getInstance().getModelManager().getModel(frameModel(tier)));
    modBus.addListener(NeoForgeClient::additionalModels);
    modBus.addListener(NeoForgeClient::itemExtensions);
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TransporterConfig.resetSynced());
    modBus.addListener((ModelEvent.BakingCompleted event) -> TransporterRenderer.modelsBaked());
  }

  /** The truck without its load, rendered by {@link TransporterRenderer} */
  public static ModelResourceLocation frameModel(TransporterTier tier) {
    return ModelResourceLocation.standalone(ChestTransporter.id("item/" + tier.itemName() + "_frame"));
  }

  private static void additionalModels(ModelEvent.RegisterAdditional event) {
    for (TransporterTier tier : TransporterTier.values()) {
      event.register(frameModel(tier));
    }
  }

  private static void itemExtensions(RegisterClientExtensionsEvent event) {
    IClientItemExtensions extensions = new IClientItemExtensions() {
      @Override
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return TransporterRenderer.get();
      }
    };
    Item[] items = java.util.Arrays.stream(TransporterTier.values()).map(tier -> (Item) ModRegistry.truck(tier)).toArray(Item[]::new);
    event.registerItem(extensions, items);
  }
}
