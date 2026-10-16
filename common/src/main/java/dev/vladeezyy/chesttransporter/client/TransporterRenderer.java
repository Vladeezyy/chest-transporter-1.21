package dev.vladeezyy.chesttransporter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.ChestTransporterItem;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.vladeezyy.chesttransporter.platform.ClientPlatform;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Draws the hand truck, and the carried block standing on its plate */
public final class TransporterRenderer extends BlockEntityWithoutLevelRenderer {
  private static TransporterRenderer instance;

  /** Load area on the plate, in block units: 10 pixels wide, deep and high */
  private static final float LOAD_SIZE = 10 / 16f;

  private TransporterRenderer() {
    super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
  }

  public static TransporterRenderer get() {
    if (instance == null) {
      instance = new TransporterRenderer();
    }
    return instance;
  }

  /** Frame models per tier, looked up once per resource reload instead of every frame */
  private final Map<TransporterTier, BakedModel> frames = new EnumMap<>(TransporterTier.class);
  /** Item stacks drawing the loads with a block entity renderer (chests), made once per block */
  private final Map<Block, ItemStack> loadStacks = new IdentityHashMap<>();
  /** Display states of the loads, made once per carried state */
  private final Map<BlockState, BlockState> displayStates = new IdentityHashMap<>();

  /** Models were baked again (resource reload): drop the cached ones */
  public static void modelsBaked() {
    if (instance != null) {
      instance.frames.clear();
    }
  }

  @Override
  public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    if (!(stack.getItem() instanceof ChestTransporterItem item)) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    BakedModel frame = frames.computeIfAbsent(item.tier(), tier -> ClientPlatform.get().frameModel(tier));
    renderQuads(frame, pose, ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, stack.hasFoil()), light, overlay);

    CarriedBlock carried = stack.get(ModRegistry.carried());
    if (carried != null) {
      pose.pushPose();
      // the plate is 1 pixel thick, the load stands on it in front of the rails
      pose.translate(3 / 16f, 1 / 16f, 2 / 16f);
      pose.scale(LOAD_SIZE, LOAD_SIZE, LOAD_SIZE);
      BlockState state = displayStates.computeIfAbsent(carried.state(), TransporterRenderer::displayState);
      if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
        // what renderSingleBlock does, without a new item stack every frame: the vanilla renderer draws chests
        ItemStack load = loadStacks.computeIfAbsent(state.getBlock(), ItemStack::new);
        super.renderByItem(load, ItemDisplayContext.NONE, pose, buffers, light, overlay);
      } else {
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay);
      }
      pose.popPose();
    }
  }

  private final RandomSource random = RandomSource.create();
  private static final Direction[] SIDES_AND_NONE = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

  /** All quads of a plain model without tint (what ItemRenderer.renderModelLists does, private in vanilla) */
  private void renderQuads(BakedModel model, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
    PoseStack.Pose last = pose.last();
    for (Direction side : SIDES_AND_NONE) {
      random.setSeed(42L);
      for (BakedQuad quad : model.getQuads(null, side, random)) {
        consumer.putBulkData(last, quad, 1f, 1f, 1f, 1f, light, overlay);
      }
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
