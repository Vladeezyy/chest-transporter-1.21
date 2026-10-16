package dev.vladeezyy.chesttransporter;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Server config, synced to the clients (the movement debuffs run on both sides) */
public final class TransporterConfig {
  public static final ModConfigSpec SPEC;
  public static final ModConfigSpec.BooleanValue PICKUP_SPAWNERS;
  public static final ModConfigSpec.ConfigValue<List<? extends String>> SPAWNER_TIERS;
  public static final ModConfigSpec.BooleanValue SLOWNESS;
  public static final ModConfigSpec.BooleanValue MINING_FATIGUE;
  public static final ModConfigSpec.BooleanValue NO_JUMP;
  public static final ModConfigSpec.BooleanValue HUNGER;
  public static final ModConfigSpec.IntValue MAX_DATA_KIB;

  static {
    ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
    builder.push("pickup");
    PICKUP_SPAWNERS = builder.comment("If false, mob spawners cannot be picked up").define("spawners", true);
    List<String> allTiers = Arrays.stream(TransporterTier.values()).map(TransporterTier::getSerializedName).toList();
    SPAWNER_TIERS = builder.comment("Trucks that can pick up spawners: " + String.join(", ", allTiers))
      .defineListAllowEmpty("spawnerTiers", allTiers, () -> "iron", value -> value instanceof String name && allTiers.contains(name.toLowerCase(Locale.ROOT)));
    MAX_DATA_KIB = builder.comment("Largest block data a truck takes, in KiB: bigger contents are refused so the item can still be sent to clients")
      .defineInRange("maxDataKiB", 1024, 16, 2000);
    builder.pop();
    builder.comment("Effects while a loaded truck is anywhere in the inventory (not in creative mode)").push("carrying");
    SLOWNESS = builder.comment("Walk slower").define("slowness", true);
    MINING_FATIGUE = builder.comment("Mine slower").define("miningFatigue", true);
    NO_JUMP = builder.comment("Cannot jump up a full block").define("noJump", true);
    HUNGER = builder.comment("Get hungry faster").define("hunger", true);
    builder.pop();
    SPEC = builder.build();
  }

  private TransporterConfig() {}

  /** Config value with the default before the server config is loaded (main menu, early ticks) */
  public static boolean get(ModConfigSpec.BooleanValue value) {
    try {
      return value.get();
    } catch (IllegalStateException e) {
      return value.getDefault();
    }
  }

  public static boolean canTakeSpawner(TransporterTier tier) {
    if (!get(PICKUP_SPAWNERS)) {
      return false;
    }
    try {
      return SPAWNER_TIERS.get().stream().anyMatch(name -> name.equalsIgnoreCase(tier.getSerializedName()));
    } catch (IllegalStateException e) {
      return true;
    }
  }

  public static int maxDataBytes() {
    try {
      return MAX_DATA_KIB.get() * 1024;
    } catch (IllegalStateException e) {
      return MAX_DATA_KIB.getDefault() * 1024;
    }
  }
}
