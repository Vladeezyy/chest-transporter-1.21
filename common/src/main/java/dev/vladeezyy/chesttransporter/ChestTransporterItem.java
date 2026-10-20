package dev.vladeezyy.chesttransporter;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;
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
  public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
    CarriedBlock carried = stack.get(ModRegistry.carried());
    if (carried == null) {
      tooltip.accept(Component.translatable("item.chest_transporter.empty").withStyle(ChatFormatting.GRAY));
    } else {
      BlockState state = carried.state();
      tooltip.accept(Component.translatable("item.chest_transporter.carrying", state.getBlock().getName().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
      CompoundTag data = carried.data();
      data.getList("Items").ifPresent(items -> tooltip.accept(Component.translatable("item.chest_transporter.stacks", items.size()).withStyle(ChatFormatting.GRAY)));
      if (data.getString("LootTable").isPresent()) {
        tooltip.accept(Component.translatable("item.chest_transporter.loot").withStyle(ChatFormatting.GRAY));
      }
      entityOf(data).ifPresent(type -> tooltip.accept(Component.translatable("item.chest_transporter.spawner", type).withStyle(ChatFormatting.GRAY)));
    }
    int left = stack.getMaxDamage() - stack.getDamageValue();
    tooltip.accept(Component.translatable("item.chest_transporter.moves_left", left).withStyle(ChatFormatting.DARK_GRAY));
  }

  /** The mob of a carried spawner */
  private static Optional<Component> entityOf(CompoundTag data) {
    return data.getCompound("SpawnData").flatMap(spawn -> spawn.getCompound("entity")).flatMap(entity -> entity.getString("id"))
      .map(Identifier::tryParse).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional).map(type -> type.getDescription());
  }
}
