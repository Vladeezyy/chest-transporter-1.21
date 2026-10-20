package dev.vladeezyy.chesttransporter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

/** The chest of a minecart with chest goes onto the truck (a plain minecart stays), and back into a plain minecart */
public final class MinecartTransport {
  private static final int SLOTS = 27;

  private MinecartTransport() {}

  /** Right click on an entity, called by the loader before the entity's own interaction; PASS when not handled */
  public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target) {
    ItemStack stack = player.getItemInHand(hand);
    if (!(stack.getItem() instanceof ChestTransporterItem) || player.isSpectator()) {
      return InteractionResult.PASS;
    }
    CarriedBlock carried = stack.get(ModRegistry.carried());
    if (carried == null && target instanceof MinecartChest cart && cart.isAlive()) {
      if (!level.isClientSide()) {
        CarriedBlock taken = takeChest(cart, level.registryAccess());
        if (taken.data().sizeInBytes() > TransporterConfig.server().maxDataBytes()) {
          player.displayClientMessage(Transport.refusal("too_heavy"), true);
        } else {
          stack.set(ModRegistry.carried(), taken);
          CarryEffects.invalidate(player);
          replace(cart, new Minecart(EntityType.MINECART, level));
        }
      }
      return InteractionResult.SUCCESS;
    } else if (carried != null && carried.state().is(Blocks.CHEST) && target instanceof Minecart cart
               && cart.getType() == EntityType.MINECART && !cart.isVehicle() && cart.isAlive()) {
      if (!level.isClientSide()) {
        MinecartChest chestCart = new MinecartChest(EntityType.CHEST_MINECART, level);
        fill(chestCart, carried.data(), level.registryAccess());
        replace(cart, chestCart);
        stack.remove(ModRegistry.carried());
        CarryEffects.invalidate(player);
        stack.hurtAndBreak(1, player, hand);
      }
      return InteractionResult.SUCCESS;
    }
    return InteractionResult.PASS;
  }

  /** Data as a chest block entity saves it: items, loot table, name */
  private static CarriedBlock takeChest(MinecartChest cart, HolderLookup.Provider registries) {
    TagValueOutput data = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
    ContainerHelper.saveAllItems(data, cart.getItemStacks());
    ResourceKey<LootTable> lootTable = cart.getContainerLootTable();
    if (lootTable != null) {
      data.putString("LootTable", lootTable.identifier().toString());
      if (cart.getContainerLootTableSeed() != 0) {
        data.putLong("LootTableSeed", cart.getContainerLootTableSeed());
      }
    }
    data.storeNullable("CustomName", ComponentSerialization.CODEC, cart.getCustomName());
    // emptied and without loot, so removing the minecart drops and generates nothing
    cart.setContainerLootTable(null);
    cart.clearContent();
    return new CarriedBlock(Blocks.CHEST.defaultBlockState(), data.buildResult());
  }

  private static void fill(MinecartChest cart, CompoundTag tag, HolderLookup.Provider registries) {
    ValueInput data = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
    NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(data, items);
    for (int i = 0; i < SLOTS; i++) {
      cart.setItem(i, items.get(i));
    }
    data.getString("LootTable").map(Identifier::tryParse).ifPresent(id -> {
      cart.setContainerLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
      cart.setContainerLootTableSeed(data.getLongOr("LootTableSeed", 0));
    });
    data.read("CustomName", ComponentSerialization.CODEC).ifPresent(cart::setCustomName);
  }

  /** Swaps a minecart for another one in the same place and motion */
  private static void replace(AbstractMinecart old, AbstractMinecart replacement) {
    replacement.snapTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
    replacement.setDeltaMovement(old.getDeltaMovement());
    if (old.hasCustomName() && replacement.getCustomName() == null && !(old instanceof MinecartChest)) {
      replacement.setCustomName(old.getCustomName());
    }
    old.discard();
    old.level().addFreshEntity(replacement);
  }
}
