package dev.vladeezyy.chesttransporter.platform;

import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.client.resources.model.BakedModel;

/** Client side of the mod loader: set by the loader's client setup */
public interface ClientPlatform {
  /** The truck without its load, an extra model loaded with the item models */
  BakedModel frameModel(TransporterTier tier);

  static ClientPlatform get() {
    return Holder.instance;
  }

  static void set(ClientPlatform platform) {
    Holder.instance = platform;
  }

  final class Holder {
    private static ClientPlatform instance;

    private Holder() {}
  }
}
