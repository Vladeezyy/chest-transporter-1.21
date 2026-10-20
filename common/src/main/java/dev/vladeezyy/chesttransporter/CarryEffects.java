package dev.vladeezyy.chesttransporter;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A loaded truck anywhere in the inventory is heavy: slower walking and mining, no jumping up a full block (jump
 * strength 0.34 lifts the player 0.86 blocks), faster hunger. Runs on both sides: movement is predicted by the client.
 */
public final class CarryEffects {
  public static final Identifier SLOWNESS = ChestTransporter.id("carrying_slowness");
  public static final Identifier FATIGUE = ChestTransporter.id("carrying_mining_fatigue");
  public static final Identifier JUMP = ChestTransporter.id("carrying_jump");
  /** Exhaustion per tick, the same as the Hunger I effect */
  private static final float EXHAUSTION = 0.005f;

  private CarryEffects() {}

  /** Inspect the inventory at least this often, even if its change counter did not move */
  private static final int RECHECK_TICKS = 20;
  /**
   * Per player: the inventory change counter and the answer of the last inventory scan. One map per side, each used by
   * one thread only (the client thread and the server thread of singleplayer tick at the same time).
   */
  private static final Map<Player, State> CLIENT_STATES = new WeakHashMap<>();
  private static final Map<Player, State> SERVER_STATES = new WeakHashMap<>();

  private static Map<Player, State> states(Player player) {
    return player.level().isClientSide() ? CLIENT_STATES : SERVER_STATES;
  }

  private static final class State {
    int timesChanged = -1;
    boolean carrying;
  }

  /** After every player tick, on both sides (called by the loaders) */
  public static void tick(Player player) {
    // the inventory is scanned only when it changed (or once a second), not every tick
    State state = states(player).computeIfAbsent(player, p -> new State());
    int timesChanged = player.getInventory().getTimesChanged();
    if (timesChanged != state.timesChanged || player.tickCount % RECHECK_TICKS == 0) {
      state.timesChanged = timesChanged;
      state.carrying = isCarrying(player);
    }
    boolean loaded = state.carrying && !player.getAbilities().instabuild && !player.isSpectator();
    TransporterConfig config = TransporterConfig.forSide(player.level().isClientSide());
    apply(player, Attributes.MOVEMENT_SPEED, SLOWNESS, loaded && config.slowness, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    apply(player, Attributes.BLOCK_BREAK_SPEED, FATIGUE, loaded && config.miningFatigue, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    apply(player, Attributes.JUMP_STRENGTH, JUMP, loaded && config.noJump, -0.08, AttributeModifier.Operation.ADD_VALUE);
    if (loaded && !player.level().isClientSide() && config.hunger) {
      player.causeFoodExhaustion(EXHAUSTION);
    }
  }

  /** The truck changed in place (loaded or unloaded): scan the inventory on the next tick */
  public static void invalidate(Player player) {
    State state = states(player).get(player);
    if (state != null) {
      state.timesChanged = -1;
    }
  }

  /** A loaded truck in the main inventory, the armor slots or the off hand */
  public static boolean isCarrying(Player player) {
    Inventory inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty() && stack.has(ModRegistry.carried())) {
        return true;
      }
    }
    return false;
  }

  private static void apply(Player player, Holder<Attribute> attribute, Identifier id, boolean on, double amount, AttributeModifier.Operation operation) {
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
