package dev.vladeezyy.chesttransporter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.ChestTransporterItem;
import dev.vladeezyy.chesttransporter.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws the hand truck, and the carried block standing on its plate */
public final class TransporterRenderer extends BlockEntityWithoutLevelRenderer {
  private static TransporterRenderer instance;

  /** Load area on the plate, in block units: 10 pixels wide, deep and high */
  private static final float LOAD_SIZE = 10 / 16f;

  private TransporterRenderer() {
    super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
  }

  static TransporterRenderer get() {
    if (instance == null) {
      instance = new TransporterRenderer();
    }
    return instance;
  }

  @Override
  public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    if (!(stack.getItem() instanceof ChestTransporterItem item)) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    BakedModel frame = mc.getModelManager().getModel(ClientSetup.frameModel(item.tier()));
    mc.getItemRenderer().renderModelLists(frame, stack, light, overlay, pose,
      ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, stack.hasFoil()));

    CarriedBlock carried = stack.get(ModRegistry.CARRIED);
    if (carried != null) {
      pose.pushPose();
      // the plate is 1 pixel thick, the load stands on it in front of the rails
      pose.translate(3 / 16f, 1 / 16f, 2 / 16f);
      pose.scale(LOAD_SIZE, LOAD_SIZE, LOAD_SIZE);
      mc.getBlockRenderer().renderSingleBlock(displayState(carried.state()), pose, buffers, light, overlay, ModelData.EMPTY, null);
      pose.popPose();
    }
  }

  /** The load faces the front of the truck; barrels stand upright */
  private static BlockState displayState(BlockState state) {
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      return state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }
    if (state.hasProperty(BlockStateProperties.FACING)) {
      return state.setValue(BlockStateProperties.FACING, Direction.UP);
    }
    return state;
  }
}
