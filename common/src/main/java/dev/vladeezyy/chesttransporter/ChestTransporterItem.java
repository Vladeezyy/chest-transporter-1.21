package dev.vladeezyy.chesttransporter;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * The hand truck. Right click a chest, barrel or spawner to load it with everything inside, right click a block to put
 * it down next to it ({@link Transport#onUseBlock}, called by the loaders before the block's own use). Each put down
 * costs one point of durability.
 */
public class ChestTransporterItem extends Item {
  private final TransporterTier tier;

  public ChestTransporterItem(TransporterTier tier, Properties props) {
    super(props);
    this.tier = tier;
  }

  public TransporterTier tier() {
    return tier;
  }

  @Override
  public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
    return tier.repair.get().test(repair);
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    CarriedBlock carried = stack.get(ModRegistry.carried());
    if (carried == null) {
      tooltip.add(Component.translatable("item.chest_transporter.empty").withStyle(ChatFormatting.GRAY));
    } else {
      BlockState state = carried.state();
      tooltip.add(Component.translatable("item.chest_transporter.carrying", state.getBlock().getName().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
      CompoundTag data = carried.data();
      if (data.contains("Items", Tag.TAG_LIST)) {
        tooltip.add(Component.translatable("item.chest_transporter.stacks", data.getList("Items", Tag.TAG_COMPOUND).size()).withStyle(ChatFormatting.GRAY));
      }
      if (data.contains("LootTable", Tag.TAG_STRING)) {
        tooltip.add(Component.translatable("item.chest_transporter.loot").withStyle(ChatFormatting.GRAY));
      }
      entityOf(data).ifPresent(type -> tooltip.add(Component.translatable("item.chest_transporter.spawner", type).withStyle(ChatFormatting.GRAY)));
    }
    int left = stack.getMaxDamage() - stack.getDamageValue();
    tooltip.add(Component.translatable("item.chest_transporter.moves_left", left).withStyle(ChatFormatting.DARK_GRAY));
  }

  /** The mob of a carried spawner */
  private static Optional<Component> entityOf(CompoundTag data) {
    if (!data.contains("SpawnData", Tag.TAG_COMPOUND)) {
      return Optional.empty();
    }
    String id = data.getCompound("SpawnData").getCompound("entity").getString("id");
    ResourceLocation key = ResourceLocation.tryParse(id);
    return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key).map(type -> type.getDescription());
  }

  @Override
  public boolean isEnchantable(ItemStack stack) {
    return false;
  }
}
