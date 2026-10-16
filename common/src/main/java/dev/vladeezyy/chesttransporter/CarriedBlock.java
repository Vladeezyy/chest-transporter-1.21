package dev.vladeezyy.chesttransporter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block on the truck: its state and the data of its block entity (inventory, custom name, loot table, spawner
 * data), saved without the position.
 */
public record CarriedBlock(BlockState state, CompoundTag data) {
  public static final Codec<CarriedBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    BlockState.CODEC.fieldOf("state").forGetter(CarriedBlock::state),
    CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(CarriedBlock::data)
  ).apply(instance, CarriedBlock::new));

  /** Same limit as the vanilla NBT reader of the network (2 MiB), a truck over it is refused when picking up */
  public static final StreamCodec<ByteBuf, CarriedBlock> STREAM_CODEC = StreamCodec.composite(
    ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), CarriedBlock::state,
    ByteBufCodecs.COMPOUND_TAG, CarriedBlock::data,
    CarriedBlock::new);

  public CarriedBlock {
    data = data.copy();
  }

  @Override
  public CompoundTag data() {
    return data.copy();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CarriedBlock carried && carried.state == state && carried.data.equals(data);
  }

  @Override
  public int hashCode() {
    return 31 * state.hashCode() + data.hashCode();
  }
}
