package dev.vladeezyy.chesttransporter;

import dev.vladeezyy.chesttransporter.platform.Platform;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModRegistry {
  private static Supplier<DataComponentType<CarriedBlock>> carried;
  private static final Map<TransporterTier, Supplier<ChestTransporterItem>> TRANSPORTERS = new EnumMap<>(TransporterTier.class);

  private ModRegistry() {}

  /** The block on the truck */
  public static DataComponentType<CarriedBlock> carried() {
    return carried.get();
  }

  public static ChestTransporterItem truck(TransporterTier tier) {
    return TRANSPORTERS.get(tier).get();
  }

  static void register() {
    carried = Platform.INSTANCE.register(Registries.DATA_COMPONENT_TYPE, "carried",
      () -> DataComponentType.<CarriedBlock>builder().persistent(CarriedBlock.CODEC).networkSynchronized(CarriedBlock.STREAM_CODEC).build());
    for (TransporterTier tier : TransporterTier.values()) {
      TRANSPORTERS.put(tier, Platform.INSTANCE.register(Registries.ITEM, tier.itemName(), () -> {
        Item.Properties props = tier.repair.apply(new Item.Properties()
          .setId(ResourceKey.create(Registries.ITEM, ChestTransporter.id(tier.itemName()))).durability(tier.moves));
        if (tier.fireResistant) {
          props.fireResistant();
        }
        return new ChestTransporterItem(tier, props);
      }));
    }
  }
}
