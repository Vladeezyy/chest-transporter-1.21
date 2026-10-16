package dev.vladeezyy.chesttransporter;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Locale;
import java.util.function.Supplier;

/** Materials of the hand truck: how many moves it lasts and what repairs it */
public enum TransporterTier implements StringRepresentable {
  WOODEN(1, () -> Ingredient.of(ItemTags.PLANKS), false),
  COPPER(4, () -> Ingredient.of(Items.COPPER_INGOT), false),
  IRON(9, () -> Ingredient.of(Items.IRON_INGOT), false),
  GOLDEN(19, () -> Ingredient.of(Items.GOLD_INGOT), false),
  DIAMOND(79, () -> Ingredient.of(Items.DIAMOND), false),
  NETHERITE(159, () -> Ingredient.of(Items.NETHERITE_INGOT), true);

  /** Number of moves: the truck takes one point of damage each time it puts a block down */
  public final int moves;
  public final Supplier<Ingredient> repair;
  public final boolean fireResistant;

  TransporterTier(int moves, Supplier<Ingredient> repair, boolean fireResistant) {
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
