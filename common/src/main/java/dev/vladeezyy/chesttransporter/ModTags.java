package dev.vladeezyy.chesttransporter;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
  /** Containers the truck picks up: chests, trapped chests and barrels; packs can add other blocks with a block entity */
  public static final TagKey<Block> TRANSPORTABLE = TagKey.create(Registries.BLOCK, ChestTransporter.id("transportable"));
  /** Spawners the truck picks up when the server config allows it */
  public static final TagKey<Block> SPAWNERS = TagKey.create(Registries.BLOCK, ChestTransporter.id("spawners"));
  /** Never picked up, even if in one of the tags above */
  public static final TagKey<Block> NOT_TRANSPORTABLE = TagKey.create(Registries.BLOCK, ChestTransporter.id("not_transportable"));

  private ModTags() {}
}
