package dev.vladeezyy.chesttransporter;

import dev.vladeezyy.chesttransporter.client.ClientSetup;
import dev.vladeezyy.chesttransporter.data.DataGen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A hand truck that carries chests, barrels and spawners with everything inside */
@Mod(ChestTransporter.MOD_ID)
public final class ChestTransporter {
  public static final String MOD_ID = "chest_transporter";
  public static final Logger LOG = LoggerFactory.getLogger("Chest Transporter");

  public ChestTransporter(IEventBus modBus, ModContainer container) {
    ModRegistry.register(modBus);
    container.registerConfig(ModConfig.Type.SERVER, TransporterConfig.SPEC);
    modBus.addListener(ChestTransporter::creativeTab);
    modBus.addListener(DataGen::gather);
    if (FMLEnvironment.dist.isClient()) {
      ClientSetup.init(modBus);
    }
    NeoForge.EVENT_BUS.register(CarryEffects.class);
    NeoForge.EVENT_BUS.register(MinecartTransport.class);
  }

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
  }

  private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
      for (TransporterTier tier : TransporterTier.values()) {
        event.accept(ModRegistry.TRANSPORTERS.get(tier).get());
      }
    }
  }
}
