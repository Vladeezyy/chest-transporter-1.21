package dev.vladeezyy.chesttransporter.fabric;

import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ConfigSyncPayload;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import dev.vladeezyy.chesttransporter.client.TransporterRenderer;
import dev.vladeezyy.chesttransporter.platform.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.util.Collection;
import java.util.List;

public final class ChestTransporterFabricClient implements ClientModInitializer {
  /** The truck without its load, rendered by {@link TransporterRenderer} */
  static ResourceLocation frameModel(TransporterTier tier) {
    return ChestTransporter.id("item/" + tier.itemName() + "_frame");
  }

  @Override
  public void onInitializeClient() {
    ClientPlatform.set(tier -> Minecraft.getInstance().getModelManager().getModel(frameModel(tier)));
    ModelLoadingPlugin.register(context -> {
      for (TransporterTier tier : TransporterTier.values()) {
        context.addModels(frameModel(tier));
      }
    });
    for (TransporterTier tier : TransporterTier.values()) {
      BuiltinItemRendererRegistry.INSTANCE.register(ModRegistry.truck(tier),
        (stack, context, pose, buffers, light, overlay) -> TransporterRenderer.get().renderByItem(stack, context, pose, buffers, light, overlay));
    }
    // cached frame models are dropped after the models are baked again
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ModelsBaked());
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
      if (mc.player != null) {
        CarryEffects.tick(mc.player);
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> TransporterConfig.receive(payload.values()));
    ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> TransporterConfig.resetSynced());
  }

  private static final class ModelsBaked implements ResourceManagerReloadListener, IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
      return ChestTransporter.id("models_baked");
    }

    @Override
    public Collection<ResourceLocation> getFabricDependencies() {
      return List.of(ResourceReloadListenerKeys.MODELS);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
      TransporterRenderer.modelsBaked();
    }
  }
}
