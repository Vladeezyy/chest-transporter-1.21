package dev.vladeezyy.chesttransporter.selftest;

import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Functional checks on a dev server, only with -Dchest_transporter.selftest=true (the source set is not in the jar).
 * Prints "[Chest Transporter selftest] PASS n/n" or the failed checks, then stops the server.
 */
@EventBusSubscriber(modid = ChestTransporter.MOD_ID)
public final class SelfTest {
  private static final String PREFIX = "[Chest Transporter selftest] ";
  private static final boolean ENABLED = Boolean.getBoolean("chest_transporter.selftest");
  private static final List<String> failures = new ArrayList<>();
  private static int checks;
  private static int tick = -1;
  private static BlockPos origin;
  private static FakePlayer player;
  /** Set by the protection check: cancels every break event while true */
  private static boolean protect;

  static void check(String name, BooleanSupplier test) {
    boolean result;
    try {
      result = test.getAsBoolean();
    } catch (RuntimeException e) {
      ChestTransporter.LOG.error(PREFIX + "exception in {}", name, e);
      result = false;
    }
    checks++;
    if (result) {
      ChestTransporter.LOG.info(PREFIX + "ok   {}", name);
    } else {
      failures.add(name);
      ChestTransporter.LOG.error(PREFIX + "FAIL {}", name);
    }
  }

  private static BlockPos at(int x, int y, int z) {
    return origin.offset(x, y, z);
  }

  private static ItemStack truck(TransporterTier tier) {
    return new ItemStack(ModRegistry.TRANSPORTERS.get(tier).get());
  }

  /** Right clicks a face of the block with the stack in the main hand, like a player (events, item, block) */
  private static InteractionResult use(ServerLevel level, BlockPos pos, ItemStack stack, Direction side) {
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(side, 0.5), side, pos, false);
    return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
  }

  private static ItemStack held() {
    return player.getMainHandItem();
  }

  private static int itemEntities(ServerLevel level, BlockPos pos) {
    return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).size();
  }

  @SubscribeEvent
  static void protection(BlockEvent.BreakEvent event) {
    if (protect) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  static void serverStarted(ServerStartedEvent event) {
    if (!ENABLED) {
      return;
    }
    ServerLevel level = event.getServer().overworld();
    BlockPos spawn = level.getSharedSpawnPos();
    // away from spawn protection, the fake player is not an operator
    origin = new BlockPos(spawn.getX() + 200, 150, spawn.getZ());
    for (int cx = -1; cx <= 2; cx++) {
      for (int cz = -1; cz <= 2; cz++) {
        level.setChunkForced((origin.getX() >> 4) + cx, (origin.getZ() >> 4) + cz, true);
      }
    }
    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(30, 6, 12))) {
      level.setBlock(pos, pos.getY() == origin.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    player = FakePlayerFactory.getMinecraft(level);
    player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() - 2.5);
    try {
      checks(event.getServer(), level);
    } catch (RuntimeException e) {
      ChestTransporter.LOG.error(PREFIX + "checks crashed", e);
      failures.add("checks crashed");
    }
    tick = 0;
  }

  @SubscribeEvent
  static void serverTick(ServerTickEvent.Post event) {
    if (tick < 0) {
      return;
    }
    tick++;
    ServerLevel level = event.getServer().overworld();
    if (tick == 40) {
      tickChecks(level);
      int passed = checks - failures.size();
      if (failures.isEmpty()) {
        ChestTransporter.LOG.info(PREFIX + "PASS {}/{}", passed, checks);
      } else {
        ChestTransporter.LOG.error(PREFIX + "FAILED {}/{}: {}", passed, checks, failures);
      }
      tick = -1;
      event.getServer().halt(false);
    }
  }

  private static void checks(MinecraftServer server, ServerLevel level) {
    check("six trucks registered, moves per tier", () -> {
      int[] moves = {1, 4, 9, 19, 79, 159};
      TransporterTier[] tiers = TransporterTier.values();
      for (int i = 0; i < tiers.length; i++) {
        if (truck(tiers[i]).getMaxDamage() != moves[i]) {
          return false;
        }
      }
      return BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(ChestTransporter.MOD_ID)).count() == 6;
    });
    check("netherite truck survives fire and lava", () -> truck(TransporterTier.NETHERITE).has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT)
      && !truck(TransporterTier.IRON).has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT));
    check("five crafting recipes and one smithing recipe", () ->
      server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream().filter(r -> r.id().getNamespace().equals(ChestTransporter.MOD_ID)).count() == 5
        && server.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING).stream().filter(r -> r.id().getNamespace().equals(ChestTransporter.MOD_ID)).count() == 1);
    check("trucks repair with their material", () -> truck(TransporterTier.IRON).getItem().isValidRepairItem(truck(TransporterTier.IRON), new ItemStack(Items.IRON_INGOT))
      && truck(TransporterTier.WOODEN).getItem().isValidRepairItem(truck(TransporterTier.WOODEN), new ItemStack(Items.OAK_PLANKS))
      && !truck(TransporterTier.IRON).getItem().isValidRepairItem(truck(TransporterTier.IRON), new ItemStack(Items.DIAMOND)));

    // single chest with items and a name
    BlockPos chest = at(0, 0, 0);
    check("chest with items picked up: block gone, nothing dropped", () -> {
      level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
      ChestBlockEntity be = (ChestBlockEntity) level.getBlockEntity(chest);
      be.setItem(0, new ItemStack(Items.DIAMOND, 12));
      be.setItem(26, new ItemStack(Items.OAK_LOG, 64));
      CompoundTag nameData = new CompoundTag();
      nameData.putString("CustomName", Component.Serializer.toJson(Component.literal("Loot"), level.registryAccess()));
      be.loadWithComponents(mergedWith(be.saveWithoutMetadata(level.registryAccess()), nameData), level.registryAccess());
      InteractionResult result = use(level, chest, truck(TransporterTier.IRON), Direction.UP);
      CarriedBlock carried = held().get(ModRegistry.CARRIED);
      return result.consumesAction() && level.getBlockState(chest).isAir() && carried != null && carried.state().is(Blocks.CHEST)
        && carried.data().getList("Items", Tag.TAG_COMPOUND).size() == 2 && itemEntities(level, chest) == 0;
    });
    check("chest put down on top of a block: items and name back, one move used", () -> {
      BlockPos ground = at(3, -1, 0);
      InteractionResult result = use(level, ground, held(), Direction.UP);
      BlockPos placed = ground.above();
      return result.consumesAction() && level.getBlockEntity(placed) instanceof ChestBlockEntity be
        && be.getItem(0).is(Items.DIAMOND) && be.getItem(0).getCount() == 12 && be.getItem(26).getCount() == 64
        && be.getCustomName() != null && be.getCustomName().getString().equals("Loot")
        && !held().has(ModRegistry.CARRIED) && held().getDamageValue() == 1;
    });
    check("put down chest faces the player", () -> {
      BlockState state = level.getBlockState(at(3, 0, 0));
      return state.getValue(ChestBlock.FACING) == player.getDirection().getOpposite();
    });

    // double chest
    check("half of a double chest: the other half turns single and keeps its items", () -> {
      BlockPos left = at(6, 0, 0);
      BlockPos right = at(7, 0, 0);
      // facing north, the LEFT half has its partner to the east
      level.setBlockAndUpdate(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
      level.setBlockAndUpdate(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
      ((ChestBlockEntity) level.getBlockEntity(left)).setItem(0, new ItemStack(Items.APPLE, 5));
      ((ChestBlockEntity) level.getBlockEntity(right)).setItem(0, new ItemStack(Items.BREAD, 7));
      use(level, left, truck(TransporterTier.IRON), Direction.UP);
      CarriedBlock carried = held().get(ModRegistry.CARRIED);
      return carried != null && carried.state().getValue(ChestBlock.TYPE) == ChestType.SINGLE
        && level.getBlockState(right).getValue(ChestBlock.TYPE) == ChestType.SINGLE
        && ((ChestBlockEntity) level.getBlockEntity(right)).getItem(0).is(Items.BREAD)
        && ItemStack.parseOptional(level.registryAccess(), carried.data().getList("Items", Tag.TAG_COMPOUND).getCompound(0)).is(Items.APPLE);
    });
    check("chest put down next to a single chest joins it into a double chest", () -> {
      player.setYRot(0); // facing south: the chest faces north like its neighbour
      use(level, at(6, -1, 0), held(), Direction.UP);
      BlockState placed = level.getBlockState(at(6, 0, 0));
      return placed.is(Blocks.CHEST) && placed.getValue(ChestBlock.TYPE) != ChestType.SINGLE
        && level.getBlockState(at(7, 0, 0)).getValue(ChestBlock.TYPE) != ChestType.SINGLE;
    });

    // barrel
    check("barrel with items: picked up and put down facing the clicked face", () -> {
      BlockPos barrel = at(10, 0, 0);
      level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
      ((BarrelBlockEntity) level.getBlockEntity(barrel)).setItem(4, new ItemStack(Items.REDSTONE, 33));
      ItemStack truck = truck(TransporterTier.COPPER);
      use(level, barrel, truck, Direction.UP);
      boolean taken = level.getBlockState(barrel).isAir() && held().has(ModRegistry.CARRIED);
      player.setXRot(90); // looking down: barrels face up
      use(level, at(12, -1, 0), held(), Direction.UP);
      player.setXRot(0);
      BlockState state = level.getBlockState(at(12, 0, 0));
      return taken && state.is(Blocks.BARREL) && state.getValue(BarrelBlock.FACING) == Direction.UP
        && level.getBlockEntity(at(12, 0, 0)) instanceof BarrelBlockEntity be && be.getItem(4).getCount() == 33;
    });

    // loot chest
    check("unopened loot chest keeps its loot table", () -> {
      BlockPos loot = at(14, 0, 0);
      level.setBlockAndUpdate(loot, Blocks.CHEST.defaultBlockState());
      ((ChestBlockEntity) level.getBlockEntity(loot)).setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 42L);
      use(level, loot, truck(TransporterTier.IRON), Direction.UP);
      CarriedBlock carried = held().get(ModRegistry.CARRIED);
      boolean kept = carried != null && carried.data().getString("LootTable").equals(BuiltInLootTables.SIMPLE_DUNGEON.location().toString());
      use(level, at(16, -1, 0), held(), Direction.UP);
      return kept && level.getBlockEntity(at(16, 0, 0)) instanceof ChestBlockEntity be && BuiltInLootTables.SIMPLE_DUNGEON.equals(be.getLootTable());
    });

    // spawner
    check("spawner with its mob: picked up by an iron truck and put down", () -> {
      BlockPos spawner = at(18, 0, 0);
      level.setBlockAndUpdate(spawner, Blocks.SPAWNER.defaultBlockState());
      ((SpawnerBlockEntity) level.getBlockEntity(spawner)).setEntityId(EntityType.SKELETON, level.random);
      use(level, spawner, truck(TransporterTier.IRON), Direction.UP);
      boolean taken = level.getBlockState(spawner).isAir() && held().has(ModRegistry.CARRIED);
      use(level, at(20, -1, 0), held(), Direction.UP);
      CompoundTag data = level.getBlockEntity(at(20, 0, 0)).saveWithoutMetadata(level.registryAccess());
      return taken && data.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:skeleton");
    });
    check("spawner refused when the config turns spawners off", () -> {
      BlockPos spawner = at(20, 0, 0);
      TransporterConfig.PICKUP_SPAWNERS.set(false);
      use(level, spawner, truck(TransporterTier.DIAMOND), Direction.UP);
      TransporterConfig.PICKUP_SPAWNERS.set(true);
      return level.getBlockState(spawner).is(Blocks.SPAWNER) && !held().has(ModRegistry.CARRIED);
    });
    check("spawner refused by a truck not in the spawner tier list", () -> {
      BlockPos spawner = at(20, 0, 0);
      TransporterConfig.SPAWNER_TIERS.set(List.of("diamond", "netherite"));
      use(level, spawner, truck(TransporterTier.IRON), Direction.UP);
      boolean refused = level.getBlockState(spawner).is(Blocks.SPAWNER) && !held().has(ModRegistry.CARRIED);
      use(level, spawner, truck(TransporterTier.DIAMOND), Direction.UP);
      boolean diamond = level.getBlockState(spawner).isAir();
      TransporterConfig.SPAWNER_TIERS.set(TransporterConfig.SPAWNER_TIERS.getDefault());
      return refused && diamond;
    });

    // refusals
    check("locked chest refused", () -> {
      BlockPos locked = at(22, 0, 0);
      level.setBlockAndUpdate(locked, Blocks.CHEST.defaultBlockState());
      ChestBlockEntity be = (ChestBlockEntity) level.getBlockEntity(locked);
      CompoundTag lock = new CompoundTag();
      lock.putString("Lock", "secret");
      be.loadWithComponents(mergedWith(be.saveWithoutMetadata(level.registryAccess()), lock), level.registryAccess());
      use(level, locked, truck(TransporterTier.IRON), Direction.UP);
      return level.getBlockState(locked).is(Blocks.CHEST) && !held().has(ModRegistry.CARRIED);
    });
    check("protected chest refused (break event cancelled)", () -> {
      BlockPos guarded = at(24, 0, 0);
      level.setBlockAndUpdate(guarded, Blocks.CHEST.defaultBlockState());
      protect = true;
      use(level, guarded, truck(TransporterTier.IRON), Direction.UP);
      protect = false;
      return level.getBlockState(guarded).is(Blocks.CHEST) && !held().has(ModRegistry.CARRIED);
    });
    check("other blocks are left alone", () -> {
      BlockPos furnace = at(26, 0, 0);
      level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
      use(level, furnace, truck(TransporterTier.IRON), Direction.UP);
      return level.getBlockState(furnace).is(Blocks.FURNACE) && !held().has(ModRegistry.CARRIED);
    });
    check("too much inside: refused under a small size limit", () -> {
      BlockPos full = at(28, 0, 0);
      level.setBlockAndUpdate(full, Blocks.CHEST.defaultBlockState());
      ChestBlockEntity be = (ChestBlockEntity) level.getBlockEntity(full);
      for (int i = 0; i < 27; i++) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT, new net.minecraft.world.item.component.WritableBookContent(
          java.util.Collections.nCopies(10, net.minecraft.server.network.Filterable.passThrough("x".repeat(1000)))));
        be.setItem(i, book);
      }
      TransporterConfig.MAX_DATA_KIB.set(16);
      use(level, full, truck(TransporterTier.IRON), Direction.UP);
      boolean refused = level.getBlockState(full).is(Blocks.CHEST) && !held().has(ModRegistry.CARRIED);
      TransporterConfig.MAX_DATA_KIB.set(TransporterConfig.MAX_DATA_KIB.getDefault());
      use(level, full, truck(TransporterTier.IRON), Direction.UP);
      return refused && held().has(ModRegistry.CARRIED);
    });
    check("no room: putting down into a solid block face is refused, the load stays", () -> {
      BlockPos wall = at(0, 0, 4);
      level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(wall.north(), Blocks.STONE.defaultBlockState());
      InteractionResult result = use(level, wall, held(), Direction.NORTH);
      return !result.consumesAction() && held().has(ModRegistry.CARRIED);
    });
    check("wooden truck breaks after its one move", () -> {
      BlockPos box = at(2, 0, 4);
      level.setBlockAndUpdate(box, Blocks.CHEST.defaultBlockState());
      ItemStack wooden = truck(TransporterTier.WOODEN);
      use(level, box, wooden, Direction.UP);
      boolean loaded = held().has(ModRegistry.CARRIED);
      use(level, at(4, -1, 4), held(), Direction.UP);
      return loaded && held().isEmpty() && level.getBlockState(at(4, 0, 4)).is(Blocks.CHEST);
    });
    check("a screen still open on the picked up chest is no longer valid (no item duplication)", () -> {
      BlockPos box = at(6, 0, 4);
      level.setBlockAndUpdate(box, Blocks.CHEST.defaultBlockState());
      ((ChestBlockEntity) level.getBlockEntity(box)).setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
      FakePlayer viewer = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "viewer"));
      viewer.setPos(box.getX() + 0.5, box.getY(), box.getZ() - 1.5);
      var menu = ((MenuProvider) level.getBlockEntity(box)).createMenu(1, viewer.getInventory(), viewer);
      boolean valid = menu.stillValid(viewer);
      use(level, box, truck(TransporterTier.IRON), Direction.UP);
      // the server ignores clicks in a menu that is not valid, and closes it on the player's next tick
      return valid && !menu.stillValid(viewer) && menu.getSlot(0).getItem().isEmpty();
    });

    // data
    check("carried block survives saving the item and loading it again", () -> {
      ItemStack truck = truck(TransporterTier.GOLDEN);
      CompoundTag data = new CompoundTag();
      data.putString("LootTable", "minecraft:chests/simple_dungeon");
      truck.set(ModRegistry.CARRIED, new CarriedBlock(Blocks.TRAPPED_CHEST.defaultBlockState(), data));
      Tag saved = truck.save(level.registryAccess());
      ItemStack loaded = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) saved);
      return ItemStack.isSameItemSameComponents(truck, loaded) && loaded.get(ModRegistry.CARRIED).state().is(Blocks.TRAPPED_CHEST);
    });

  }

  /** After ticks: the carrying effects on the fake player (ticked by the test) */
  private static void tickChecks(ServerLevel level) {
    // minecarts
    check("chest minecart: the chest goes on the truck, a plain minecart stays", () -> {
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      MinecartChest cart = new MinecartChest(level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 8.5);
      cart.setItem(3, new ItemStack(Items.EMERALD, 9));
      level.addFreshEntity(cart);
      ItemStack truck = truck(TransporterTier.IRON);
      player.setItemInHand(InteractionHand.MAIN_HAND, truck);
      player.interactOn(cart, InteractionHand.MAIN_HAND);
      CarriedBlock carried = held().get(ModRegistry.CARRIED);
      List<Minecart> plain = level.getEntitiesOfClass(Minecart.class, new AABB(origin.offset(0, 0, 8)).inflate(2));
      return !cart.isAlive() && carried != null && carried.state().is(Blocks.CHEST) && plain.size() == 1 && itemEntities(level, origin.offset(0, 0, 8)) == 0
        && ItemStack.parseOptional(level.registryAccess(), carried.data().getList("Items", Tag.TAG_COMPOUND).getCompound(0)).getCount() == 9;
    });
    check("carried chest goes into a plain minecart", () -> {
      Minecart plain = level.getEntitiesOfClass(Minecart.class, new AABB(origin.offset(0, 0, 8)).inflate(2)).get(0);
      player.interactOn(plain, InteractionHand.MAIN_HAND);
      List<MinecartChest> carts = level.getEntitiesOfClass(MinecartChest.class, new AABB(origin.offset(0, 0, 8)).inflate(2));
      return !plain.isAlive() && carts.size() == 1 && carts.get(0).getItem(3).is(Items.EMERALD) && !held().has(ModRegistry.CARRIED) && held().getDamageValue() == 1;
    });

    // effects
    player.getInventory().clearContent();
    ItemStack loaded = truck(TransporterTier.IRON);
    loaded.set(ModRegistry.CARRIED, new CarriedBlock(Blocks.CHEST.defaultBlockState(), new CompoundTag()));
    player.getInventory().setItem(20, loaded);
    player.getFoodData().setExhaustion(0);
    check("loaded truck in the inventory: slower, weaker jump, slower mining, hungrier", () -> {
      for (int i = 0; i < 20; i++) {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
      }
      return player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS)
        && Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - 0.34) < 1e-6
        && player.getAttribute(Attributes.BLOCK_BREAK_SPEED).hasModifier(CarryEffects.FATIGUE)
        && player.getFoodData().getExhaustionLevel() > 0.05f;
    });
    check("effects end when the truck is gone", () -> {
      player.getInventory().clearContent();
      NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
      return !player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS)
        && Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - 0.42) < 1e-6;
    });
  }

  private static CompoundTag mergedWith(CompoundTag base, CompoundTag extra) {
    return base.merge(extra);
  }
}
