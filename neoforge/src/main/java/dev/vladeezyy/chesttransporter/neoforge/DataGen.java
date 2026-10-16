package dev.vladeezyy.chesttransporter.neoforge;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.ModTags;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

public final class DataGen {
  private DataGen() {}

  public static void gather(GatherDataEvent event) {
    PackOutput output = event.getGenerator().getPackOutput();
    CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
    event.getGenerator().addProvider(event.includeServer(), new Recipes(output, lookup));
    event.getGenerator().addProvider(event.includeServer(), new Tags(output, lookup, event.getExistingFileHelper()));
  }

  static final class Recipes extends RecipeProvider {
    Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
      super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
      for (TransporterTier tier : TransporterTier.values()) {
        if (tier == TransporterTier.NETHERITE) {
          continue;
        }
        // two rails of sticks and the plate of the material
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModRegistry.truck(tier))
          .pattern("S S")
          .pattern("S S")
          .pattern("MMM")
          .define('S', Items.STICK)
          .define('M', tier.repair.get())
          .unlockedBy("has_material", has(tier == TransporterTier.WOODEN ? Items.CHEST : tier.repair.get().getItems()[0].getItem()))
          .save(output, ChestTransporter.id(tier.itemName()));
      }
      SmithingTransformRecipeBuilder.smithing(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
          Ingredient.of(ModRegistry.truck(TransporterTier.DIAMOND)), Ingredient.of(Items.NETHERITE_INGOT),
          RecipeCategory.TOOLS, ModRegistry.truck(TransporterTier.NETHERITE))
        .unlocks("has_netherite_ingot", has(Items.NETHERITE_INGOT))
        .save(output, ChestTransporter.id(TransporterTier.NETHERITE.itemName() + "_smithing"));
    }
  }

  static final class Tags extends BlockTagsProvider {
    Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
      super(output, lookup, ChestTransporter.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
      tag(ModTags.TRANSPORTABLE).add(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL);
      tag(ModTags.SPAWNERS).add(Blocks.SPAWNER);
      tag(ModTags.NOT_TRANSPORTABLE);
    }
  }
}
