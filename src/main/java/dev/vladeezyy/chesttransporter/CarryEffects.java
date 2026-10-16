package dev.vladeezyy.chesttransporter;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A loaded truck anywhere in the inventory is heavy: slower walking and mining, no jumping up a full block (jump
 * strength 0.34 lifts the player 0.86 blocks), faster hunger. Runs on both sides: movement is predicted by the client.
 */
public final class CarryEffects {
  public static final ResourceLocation SLOWNESS = ChestTransporter.id("carrying_slowness");
  public static final ResourceLocation FATIGUE = ChestTransporter.id("carrying_mining_fatigue");
  public static final ResourceLocation JUMP = ChestTransporter.id("carrying_jump");
  /** Exhaustion per tick, the same as the Hunger I effect */
  private static final float EXHAUSTION = 0.005f;

  private CarryEffects() {}

  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    Player player = event.getEntity();
    boolean loaded = !player.getAbilities().instabuild && !player.isSpectator() && isCarrying(player);
    apply(player, Attributes.MOVEMENT_SPEED, SLOWNESS, loaded && TransporterConfig.get(TransporterConfig.SLOWNESS), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    apply(player, Attributes.BLOCK_BREAK_SPEED, FATIGUE, loaded && TransporterConfig.get(TransporterConfig.MINING_FATIGUE), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    apply(player, Attributes.JUMP_STRENGTH, JUMP, loaded && TransporterConfig.get(TransporterConfig.NO_JUMP), -0.08, AttributeModifier.Operation.ADD_VALUE);
    if (loaded && !player.level().isClientSide && TransporterConfig.get(TransporterConfig.HUNGER)) {
      player.causeFoodExhaustion(EXHAUSTION);
    }
  }

  /** A loaded truck in the main inventory, the armor slots or the off hand */
  public static boolean isCarrying(Player player) {
    Inventory inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty() && stack.has(ModRegistry.CARRIED)) {
        return true;
      }
    }
    return false;
  }

  private static void apply(Player player, Holder<Attribute> attribute, ResourceLocation id, boolean on, double amount, AttributeModifier.Operation operation) {
    AttributeInstance instance = player.getAttribute(attribute);
    if (instance == null) {
      return;
    }
    boolean has = instance.hasModifier(id);
    if (on && !has) {
      instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    } else if (!on && has) {
      instance.removeModifier(id);
    }
  }
}
