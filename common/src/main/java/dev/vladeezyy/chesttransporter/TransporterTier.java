package dev.vladeezyy.chesttransporter;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.function.UnaryOperator;

/** Materials of the hand truck: how many moves it lasts and what repairs it */
public enum TransporterTier implements StringRepresentable {
  WOODEN(1, props -> props.repairable(ItemTags.PLANKS), false),
  COPPER(4, props -> props.repairable(Items.COPPER_INGOT), false),
  IRON(9, props -> props.repairable(Items.IRON_INGOT), false),
  GOLDEN(19, props -> props.repairable(Items.GOLD_INGOT), false),
  DIAMOND(79, props -> props.repairable(Items.DIAMOND), false),
  NETHERITE(159, props -> props.repairable(Items.NETHERITE_INGOT), true);

  /** Number of moves: the truck takes one point of damage each time it puts a block down */
  public final int moves;
  /** Sets the repair item (anvil) on the item properties */
  public final UnaryOperator<Item.Properties> repair;
  public final boolean fireResistant;

  TransporterTier(int moves, UnaryOperator<Item.Properties> repair, boolean fireResistant) {
    this.moves = moves;
    this.repair = repair;
    this.fireResistant = fireResistant;
  }

  @Override
  public String getSerializedName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Registry name of the item */
  public String itemName() {
    return getSerializedName() + "_chest_transporter";
  }
}
