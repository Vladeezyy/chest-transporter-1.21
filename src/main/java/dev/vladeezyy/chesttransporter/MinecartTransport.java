package dev.vladeezyy.chesttransporter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The chest of a minecart with chest goes onto the truck (a plain minecart stays), and back into a plain minecart */
public final class MinecartTransport {
  private static final int SLOTS = 27;

  private MinecartTransport() {}

  @SubscribeEvent
  public static void interact(PlayerInteractEvent.EntityInteract event) {
    ItemStack stack = event.getItemStack();
    if (!(stack.getItem() instanceof ChestTransporterItem)) {
      return;
    }
    Level level = event.getLevel();
    Player player = event.getEntity();
    CarriedBlock carried = stack.get(ModRegistry.CARRIED);
    if (carried == null && event.getTarget() instanceof MinecartChest cart && cart.isAlive()) {
      if (!level.isClientSide) {
        CarriedBlock taken = takeChest(cart, level.registryAccess());
        if (taken.data().sizeInBytes() > TransporterConfig.maxDataBytes()) {
          player.displayClientMessage(Transport.refusal("too_heavy"), true);
        } else {
          stack.set(ModRegistry.CARRIED, taken);
          replace(cart, new Minecart(level, cart.getX(), cart.getY(), cart.getZ()));
        }
      }
      done(event, level);
    } else if (carried != null && carried.state().is(Blocks.CHEST) && event.getTarget() instanceof Minecart cart
               && cart.getMinecartType() == AbstractMinecart.Type.RIDEABLE && !cart.isVehicle() && cart.isAlive()) {
      if (!level.isClientSide) {
        MinecartChest chestCart = new MinecartChest(level, cart.getX(), cart.getY(), cart.getZ());
        fill(chestCart, carried.data(), level.registryAccess());
        replace(cart, chestCart);
        stack.remove(ModRegistry.CARRIED);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
      }
      done(event, level);
    }
  }

  private static void done(PlayerInteractEvent.EntityInteract event, Level level) {
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
  }

  /** Data as a chest block entity saves it: items, loot table, name */
  private static CarriedBlock takeChest(MinecartChest cart, HolderLookup.Provider registries) {
    CompoundTag data = new CompoundTag();
    ContainerHelper.saveAllItems(data, cart.getItemStacks(), registries);
    ResourceKey<LootTable> lootTable = cart.getLootTable();
    if (lootTable != null) {
      data.putString("LootTable", lootTable.location().toString());
      if (cart.getLootTableSeed() != 0) {
        data.putLong("LootTableSeed", cart.getLootTableSeed());
      }
    }
    Component name = cart.getCustomName();
    if (name != null) {
      data.putString("CustomName", Component.Serializer.toJson(name, registries));
    }
    // emptied and without loot, so removing the minecart drops and generates nothing
    cart.setLootTable(null);
    cart.clearContent();
    return new CarriedBlock(Blocks.CHEST.defaultBlockState(), data);
  }

  private static void fill(MinecartChest cart, CompoundTag data, HolderLookup.Provider registries) {
    NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(data, items, registries);
    for (int i = 0; i < SLOTS; i++) {
      cart.setItem(i, items.get(i));
    }
    if (data.contains("LootTable", Tag.TAG_STRING)) {
      ResourceLocation id = ResourceLocation.tryParse(data.getString("LootTable"));
      if (id != null) {
        cart.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        cart.setLootTableSeed(data.getLong("LootTableSeed"));
      }
    }
    if (data.contains("CustomName", Tag.TAG_STRING)) {
      cart.setCustomName(Component.Serializer.fromJson(data.getString("CustomName"), registries));
    }
  }

  /** Swaps a minecart for another one in the same place and motion */
  private static void replace(AbstractMinecart old, AbstractMinecart replacement) {
    replacement.setYRot(old.getYRot());
    replacement.setXRot(old.getXRot());
    replacement.setDeltaMovement(old.getDeltaMovement());
    if (old.hasCustomName() && replacement.getCustomName() == null && !(old instanceof MinecartChest)) {
      replacement.setCustomName(old.getCustomName());
    }
    old.discard();
    old.level().addFreshEntity(replacement);
  }
}
