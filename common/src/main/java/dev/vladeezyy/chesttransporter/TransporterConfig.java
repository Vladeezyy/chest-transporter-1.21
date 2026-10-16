package dev.vladeezyy.chesttransporter;

import dev.vladeezyy.chesttransporter.platform.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Server config in {@code config/chest_transporter-server.toml} (the same file and keys as the NeoForge config of
 * 1.0.0), read when the server starts and sent to every joining client: the carrying effects run on both sides. Wrong
 * types fall back to the default, numbers are clamped to their range, the file is written back corrected.
 */
public final class TransporterConfig {
  public static final String FILE = "chest_transporter-server.toml";
  public static final int MIN_DATA_KIB = 16, MAX_DATA_KIB = 2000, DEFAULT_DATA_KIB = 1024;
  private static final List<String> ALL_TIERS = Arrays.stream(TransporterTier.values()).map(TransporterTier::getSerializedName).toList();

  public boolean pickupSpawners = true;
  public List<String> spawnerTiers = ALL_TIERS;
  public int maxDataKiB = DEFAULT_DATA_KIB;
  public boolean slowness = true;
  public boolean miningFatigue = true;
  public boolean noJump = true;
  public boolean hunger = true;

  /** The values of this side's server (in singleplayer the integrated one) */
  private static TransporterConfig server = new TransporterConfig();
  /** What the client got from the server it plays on */
  private static TransporterConfig synced = new TransporterConfig();

  public static final StreamCodec<ByteBuf, TransporterConfig> STREAM_CODEC = StreamCodec.of(
    (buf, config) -> {
      buf.writeBoolean(config.pickupSpawners);
      ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(TransporterTier.values().length)).encode(buf, config.spawnerTiers);
      buf.writeInt(config.maxDataKiB);
      buf.writeBoolean(config.slowness);
      buf.writeBoolean(config.miningFatigue);
      buf.writeBoolean(config.noJump);
      buf.writeBoolean(config.hunger);
    },
    buf -> {
      TransporterConfig config = new TransporterConfig();
      config.pickupSpawners = buf.readBoolean();
      config.spawnerTiers = List.copyOf(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(TransporterTier.values().length)).decode(buf));
      config.maxDataKiB = clamp(buf.readInt());
      config.slowness = buf.readBoolean();
      config.miningFatigue = buf.readBoolean();
      config.noJump = buf.readBoolean();
      config.hunger = buf.readBoolean();
      return config;
    });

  public static TransporterConfig server() {
    return server;
  }

  /** The values in effect on this side: the client uses what its server sent */
  public static TransporterConfig forSide(boolean clientSide) {
    return clientSide ? synced : server;
  }

  /** Client: values sent by the server */
  public static void receive(TransporterConfig values) {
    synced = values;
  }

  /** Client: left the server, back to the defaults */
  public static void resetSynced() {
    synced = new TransporterConfig();
  }

  public boolean canTakeSpawner(TransporterTier tier) {
    return pickupSpawners && spawnerTiers.stream().anyMatch(name -> name.equalsIgnoreCase(tier.getSerializedName()));
  }

  public int maxDataBytes() {
    return maxDataKiB * 1024;
  }

  public void reset() {
    TransporterConfig defaults = new TransporterConfig();
    pickupSpawners = defaults.pickupSpawners;
    spawnerTiers = defaults.spawnerTiers;
    maxDataKiB = defaults.maxDataKiB;
    slowness = defaults.slowness;
    miningFatigue = defaults.miningFatigue;
    noJump = defaults.noJump;
    hunger = defaults.hunger;
  }

  /* File */

  /** Reads the server file (or makes it), corrects it and writes it back */
  public static void loadServer() {
    Path file = Platform.INSTANCE.configDir().resolve(FILE);
    TransporterConfig config = new TransporterConfig();
    try {
      if (Files.exists(file)) {
        config = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
      }
      Files.createDirectories(file.getParent());
      Files.writeString(file, config.write(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      ChestTransporter.LOG.error("Could not read or write {}, using the defaults", file, e);
    }
    server = config;
  }

  /** Lenient reader of the small TOML subset the file uses: sections, booleans, integers and lists of strings */
  static TransporterConfig parse(List<String> lines) {
    TransporterConfig config = new TransporterConfig();
    String section = "";
    for (String raw : lines) {
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("[") && line.endsWith("]")) {
        section = line.substring(1, line.length() - 1).strip();
        continue;
      }
      int eq = line.indexOf('=');
      if (eq < 0) {
        continue;
      }
      String key = section + "." + line.substring(0, eq).strip();
      String value = line.substring(eq + 1).strip();
      switch (key) {
        case "pickup.spawners" -> config.pickupSpawners = bool(value, config.pickupSpawners);
        case "pickup.spawnerTiers" -> config.spawnerTiers = tiers(value, config.spawnerTiers);
        case "pickup.maxDataKiB" -> config.maxDataKiB = integer(value, config.maxDataKiB);
        case "carrying.slowness" -> config.slowness = bool(value, config.slowness);
        case "carrying.miningFatigue" -> config.miningFatigue = bool(value, config.miningFatigue);
        case "carrying.noJump" -> config.noJump = bool(value, config.noJump);
        case "carrying.hunger" -> config.hunger = bool(value, config.hunger);
        default -> ChestTransporter.LOG.warn("Unknown key {} in {}", key, FILE);
      }
    }
    return config;
  }

  private static boolean bool(String value, boolean fallback) {
    return switch (value) {
      case "true" -> true;
      case "false" -> false;
      default -> fallback;
    };
  }

  private static int integer(String value, int fallback) {
    try {
      return clamp(Long.parseLong(value));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static int clamp(long value) {
    return (int) Math.max(MIN_DATA_KIB, Math.min(MAX_DATA_KIB, value));
  }

  /** ["a", "b"]; unknown tier names are dropped, anything that is not a list gives the default */
  private static List<String> tiers(String value, List<String> fallback) {
    if (!value.startsWith("[") || !value.endsWith("]")) {
      return fallback;
    }
    List<String> tiers = new ArrayList<>();
    for (String part : value.substring(1, value.length() - 1).split(",")) {
      String name = part.strip().replace("\"", "").replace("'", "").toLowerCase(Locale.ROOT);
      if (ALL_TIERS.contains(name) && !tiers.contains(name)) {
        tiers.add(name);
      }
    }
    return List.copyOf(tiers);
  }

  String write() {
    return """
      # Chest Transporter server config, sent to the clients

      [pickup]
      \t# If false, mob spawners cannot be picked up
      \tspawners = %s
      \t# Trucks that can pick up spawners: %s
      \tspawnerTiers = [%s]
      \t# Largest block data a truck takes, in KiB (%d-%d): bigger contents are refused so the item can still be sent to clients
      \tmaxDataKiB = %d

      # Effects while a loaded truck is anywhere in the inventory (not in creative mode)
      [carrying]
      \t# Walk slower
      \tslowness = %s
      \t# Mine slower
      \tminingFatigue = %s
      \t# Cannot jump up a full block
      \tnoJump = %s
      \t# Get hungry faster
      \thunger = %s
      """.formatted(pickupSpawners, String.join(", ", ALL_TIERS),
      String.join(", ", spawnerTiers.stream().map(name -> "\"" + name + "\"").toList()),
      MIN_DATA_KIB, MAX_DATA_KIB, maxDataKiB, slowness, miningFatigue, noJump, hunger);
  }
}
