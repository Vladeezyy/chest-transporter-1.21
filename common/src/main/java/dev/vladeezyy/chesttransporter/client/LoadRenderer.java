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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The carried block standing on the plate of the truck: the second part of the truck's item model
 * ({@code assets/chest_transporter/items/*.json}, the frame is a plain model), drawn with the item model of the block
 * (chests with their special renderer, barrels and spawners with their block model).
 */
public final class LoadRenderer implements SpecialModelRenderer<BlockState> {
  public static final Identifier ID = ChestTransporter.id("load");
  /** Load area on the plate, in block units: 10 pixels wide, deep and high, in front of the rails */
  private static final float LOAD_SIZE = 10 / 16f;
  private static final float X = 3 / 16f, Y = 1 / 16f, Z = 2 / 16f;

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
  public void submit(@Nullable BlockState carried, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
    if (carried == null) {
      return;
    }
    pose.pushPose();
    pose.translate(X, Y, Z);
    pose.scale(LOAD_SIZE, LOAD_SIZE, LOAD_SIZE);
    // the item transform of the load (none) moves it by -0.5 to centre it, as it does for every item since 26.1
    pose.translate(0.5f, 0.5f, 0.5f);
    Minecraft mc = Minecraft.getInstance();
    ItemStack load = loadStacks.computeIfAbsent(carried.getBlock(), ItemStack::new);
    mc.getItemModelResolver().updateForTopItem(loadState, load, ItemDisplayContext.NONE, mc.level, null, 0);
    loadState.submit(pose, collector, light, overlay, outline);
    pose.popPose();
  }

  @Override
  public void getExtents(Consumer<Vector3fc> extents) {
    for (int i = 0; i < 8; i++) {
      extents.accept(new Vector3f(X + ((i & 1) == 0 ? 0 : LOAD_SIZE), Y + ((i & 2) == 0 ? 0 : LOAD_SIZE), Z + ((i & 4) == 0 ? 0 : LOAD_SIZE)));
    }
  }

  public record Unbaked() implements SpecialModelRenderer.Unbaked<BlockState> {
    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

    @Override
    public SpecialModelRenderer<BlockState> bake(BakingContext context) {
      return new LoadRenderer();
    }

    @Override
    public MapCodec<Unbaked> type() {
      return MAP_CODEC;
    }
  }
}
