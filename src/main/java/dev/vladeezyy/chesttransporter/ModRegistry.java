package dev.vladeezyy.chesttransporter;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModRegistry {
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChestTransporter.MOD_ID);
  private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ChestTransporter.MOD_ID);

  /** The block on the truck */
  public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarriedBlock>> CARRIED = COMPONENTS.register("carried",
    () -> DataComponentType.<CarriedBlock>builder().persistent(CarriedBlock.CODEC).networkSynchronized(CarriedBlock.STREAM_CODEC).build());

  public static final Map<TransporterTier, DeferredItem<ChestTransporterItem>> TRANSPORTERS = new EnumMap<>(TransporterTier.class);

  static {
    for (TransporterTier tier : TransporterTier.values()) {
      TRANSPORTERS.put(tier, ITEMS.register(tier.itemName(), () -> {
        Item.Properties props = new Item.Properties().durability(tier.moves);
        if (tier.fireResistant) {
          props.fireResistant();
        }
        return new ChestTransporterItem(tier, props);
      }));
    }
  }

  private ModRegistry() {}

  static void register(IEventBus modBus) {
    ITEMS.register(modBus);
    COMPONENTS.register(modBus);
  }
}
