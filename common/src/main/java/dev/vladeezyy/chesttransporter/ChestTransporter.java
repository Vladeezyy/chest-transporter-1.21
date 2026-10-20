package dev.vladeezyy.chesttransporter;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A hand truck that carries chests, barrels and spawners with everything inside (shared by both loaders) */
public final class ChestTransporter {
  public static final String MOD_ID = "chest_transporter";
  public static final Logger LOG = LoggerFactory.getLogger("Chest Transporter");

  private ChestTransporter() {}

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }

  /** Called by the loader's entry point */
  public static void init() {
    ModRegistry.register();
  }
}
