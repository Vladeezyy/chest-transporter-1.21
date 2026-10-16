package dev.vladeezyy.chesttransporter.client;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ClientSetup {
  private ClientSetup() {}

  public static void init(IEventBus modBus) {
    modBus.addListener(ClientSetup::additionalModels);
    modBus.addListener(ClientSetup::itemExtensions);
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
    Item[] items = ModRegistry.TRANSPORTERS.values().stream().map(holder -> (Item) holder.get()).toArray(Item[]::new);
    event.registerItem(extensions, items);
  }
}
