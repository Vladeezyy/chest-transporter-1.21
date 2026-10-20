package dev.vladeezyy.chesttransporter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The carried block standing on the plate of the truck: the second part of the truck's item model
 * ({@code assets/chest_transporter/items/*.json}, the frame is a plain model). Blocks drawn by a block entity renderer
 * (chests) are drawn with their own item model, other blocks with their block model.
 */
public final class LoadRenderer implements SpecialModelRenderer<BlockState> {
  public static final Identifier ID = ChestTransporter.id("load");
  /** Load area on the plate, in block units: 10 pixels wide, deep and high, in front of the rails */
  private static final float LOAD_SIZE = 10 / 16f;
  private static final float X = 3 / 16f, Y = 1 / 16f, Z = 2 / 16f;

  /** Display states of the loads, made once per carried state */
  private final Map<BlockState, BlockState> displayStates = new IdentityHashMap<>();
  /** Item stacks of the loads drawn with their item model, made once per block */
  private final Map<Block, ItemStack> loadStacks = new IdentityHashMap<>();
  /** Reused for every load drawn with an item model (render thread only) */
  private final ItemStackRenderState loadState = new ItemStackRenderState();

  @Override
  public @Nullable BlockState extractArgument(ItemStack stack) {
    CarriedBlock carried = stack.get(ModRegistry.carried());
    return carried == null ? null : carried.state();
  }

  @Override
  public void submit(@Nullable BlockState carried, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
    if (carried == null) {
      return;
    }
    pose.pushPose();
    pose.translate(X, Y, Z);
    pose.scale(LOAD_SIZE, LOAD_SIZE, LOAD_SIZE);
    BlockState state = displayStates.computeIfAbsent(carried, LoadRenderer::displayState);
    if (state.getRenderShape() == RenderShape.MODEL) {
      collector.submitBlock(pose, state, light, overlay, outline);
    } else {
      // no block model: its block entity renderer draws it, so does the special renderer of its item model
      Minecraft mc = Minecraft.getInstance();
      ItemStack load = loadStacks.computeIfAbsent(state.getBlock(), ItemStack::new);
      mc.getItemModelResolver().updateForTopItem(loadState, load, ItemDisplayContext.NONE, mc.level, null, 0);
      loadState.submit(pose, collector, light, overlay, outline);
    }
    pose.popPose();
  }

  @Override
  public void getExtents(Consumer<Vector3fc> extents) {
    for (int i = 0; i < 8; i++) {
      extents.accept(new Vector3f(X + ((i & 1) == 0 ? 0 : LOAD_SIZE), Y + ((i & 2) == 0 ? 0 : LOAD_SIZE), Z + ((i & 4) == 0 ? 0 : LOAD_SIZE)));
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

  public record Unbaked() implements SpecialModelRenderer.Unbaked {
    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

    @Override
    public SpecialModelRenderer<?> bake(BakingContext context) {
      return new LoadRenderer();
    }

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }
  }
}
