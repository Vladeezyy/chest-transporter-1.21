package dev.vladeezyy.chesttransporter.selftest;

import com.mojang.datafixers.util.Pair;
import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Stress scene ({@code tools/scenetest.sh -Pscene=stress}), inert unless {@code chest_transporter.scene} is
 * {@code stress}: a broken server config from before the start (tools/scenetest.sh writes it), 128 loaded trucks on
 * screen against 128 vanilla chests, a chest of 27 full shulker boxes carried to the client and back, 800 pick ups and
 * put downs counting every item, the carrying effects on the client, leaving and rejoining with a loaded truck.
 */
@EventBusSubscriber(modid = ChestTransporter.MOD_ID, value = Dist.CLIENT)
public final class StressScene {
  private static final boolean ENABLED = System.getProperty("chest_transporter.scene", "").equals("stress");
  private static final String WORLD = "ct_stress";
  private static final List<Pair<Integer, Consumer<Minecraft>>> STEPS = new ArrayList<>();
  private static final List<Pair<Integer, Consumer<Minecraft>>> REJOIN_STEPS = new ArrayList<>();
  private static final Random RANDOM = new Random(20261016L);
  private static boolean opened, rejoining, rejoined;
  private static int tick, checks, failures, frames;
  private static long measureStart;
  private static BlockPos stage;
  private static double fpsVanilla;
  private static long itemsBefore;
  /** Stacks inside the shulker boxes of the heavy chest after it was put down */
  private static int heavyCount;

  static void at(int t, Consumer<Minecraft> action) {
    STEPS.add(Pair.of(t, action));
  }

  static void afterRejoin(int t, Consumer<Minecraft> action) {
    REJOIN_STEPS.add(Pair.of(t, action));
  }

  static void check(String name, boolean ok, Object detail) {
    checks++;
    if (!ok) {
      failures++;
    }
    ChestTransporter.LOG.info("[scene] CHECK " + (ok ? "OK " : "FAIL ") + name + (ok ? "" : " (" + detail + ")"));
  }

  static void server(Minecraft mc, Consumer<ServerPlayer> action) {
    var server = mc.getSingleplayerServer();
    server.execute(() -> {
      try {
        action.accept(server.getPlayerList().getPlayer(mc.player.getUUID()));
      } catch (RuntimeException | LinkageError e) {
        check("server step ran", false, e);
        ChestTransporter.LOG.error("[scene] server step failed", e);
      }
    });
  }

  private static double fps(String name) {
    double fps = frames / ((System.nanoTime() - measureStart) / 1e9);
    ChestTransporter.LOG.info(String.format("[scene] PERF %s: %.1f FPS", name, fps));
    return fps;
  }

  private static double msptIdle;
  private static long tickNanosSum;
  private static int tickSamples;

  private static void msptStart(Minecraft mc) {
    tickNanosSum = 0;
    tickSamples = 0;
    measuringTicks = true;
  }

  private static boolean measuringTicks;

  @SubscribeEvent
  public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    if (ENABLED && measuringTicks) {
      // the server's own average over its last 100 ticks, sampled every tick
      tickNanosSum += event.getServer().getAverageTickTimeNanos();
      tickSamples++;
    }
  }

  private static double mspt(Minecraft mc, String name) {
    measuringTicks = false;
    double mspt = tickSamples == 0 ? 0 : tickNanosSum / (double) tickSamples / 1e6;
    ChestTransporter.LOG.info(String.format("[scene] PERF %s: %.3f ms per server tick", name, mspt));
    return mspt;
  }

  /** Average time of the player tick handler of the mod, in microseconds, over 20000 calls */
  private static double handlerMicros(ServerPlayer player) {
    var event = new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player);
    for (int i = 0; i < 2000; i++) {
      CarryEffects.tick(event);
    }
    long start = System.nanoTime();
    for (int i = 0; i < 20000; i++) {
      CarryEffects.tick(event);
    }
    return (System.nanoTime() - start) / 20000.0 / 1000.0;
  }

  private static void startMeasure() {
    frames = 0;
    measureStart = System.nanoTime();
  }

  static {
    int t = 20;
    at(t, mc -> {
      // tools/scenetest.sh wrote spawners = "maybe", maxDataKiB = 999999 and slowness = 1 before the start
      // NeoForge resets wrong types to the default and clamps numbers to their range
      check("broken server config: wrong values corrected", TransporterConfig.get(TransporterConfig.PICKUP_SPAWNERS)
        && TransporterConfig.maxDataBytes() == 2000 * 1024 && TransporterConfig.get(TransporterConfig.SLOWNESS)
        && TransporterConfig.SPAWNER_TIERS.get().size() == 6, TransporterConfig.maxDataBytes());
      server(mc, StressScene::floor);
    });
    // server tick time: idle, then with a loaded truck in the inventory (the effects run every tick)
    at(t += 20, mc -> msptStart(mc));
    at(t += 100, mc -> {
      msptIdle = mspt(mc, "idle player");
      server(mc, p -> p.getInventory().setItem(20, loaded(TransporterTier.IRON, Blocks.CHEST)));
    });
    at(t += 20, mc -> msptStart(mc));
    at(t += 100, mc -> {
      mspt(mc, "player carrying a loaded truck");
      // the whole server tick is noisy in a dev world: time the effects handler itself
      server(mc, p -> {
        double carrying = handlerMicros(p);
        p.getInventory().setItem(20, ItemStack.EMPTY);
        double empty = handlerMicros(p);
        ChestTransporter.LOG.info(String.format("[scene] PERF carrying effects handler per player tick: %.3f us loaded, %.3f us empty", carrying, empty));
        check("carrying effects cost under 5 microseconds per player tick", carrying < 5 && empty < 5, carrying + " / " + empty);
      });
    });
    at(t += 10, mc -> server(mc, p -> showcase(p, false)));
    at(t += 40, mc -> startMeasure());
    at(t += 100, mc -> {
      fpsVanilla = fps("128 vanilla chests, barrels and spawners as items and in frames");
      server(mc, p -> showcase(p, true));
    });
    at(t += 40, mc -> startMeasure());
    at(t += 100, mc -> {
      double fps = fps("128 loaded trucks as items and in frames");
      check("128 loaded trucks keep at least 70 % of the frame rate of the same 128 blocks", fps > fpsVanilla * 0.7, fps + " vs " + fpsVanilla);
      server(mc, StressScene::clearShowcase);
    });
    // a chest of 27 full shulker boxes
    at(t += 10, mc -> server(mc, StressScene::heavyPickUp));
    at(t += 20, mc -> {
      ItemStack held = mc.player.getMainHandItem();
      CarriedBlock carried = held.get(ModRegistry.CARRIED);
      check("chest of 27 full shulker boxes reached the client on the truck", carried != null
        && carried.data().getList("Items", Tag.TAG_COMPOUND).size() == 27, carried == null ? "nothing" : carried.data().sizeInBytes());
      if (carried != null) {
        ChestTransporter.LOG.info("[scene] PERF heavy load: " + carried.data().sizeInBytes() / 1024 + " KiB of block data");
      }
      check("carrying slows the player on the client too", mc.player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS), "no modifier");
    });
    at(t += 5, mc -> server(mc, StressScene::heavyPutDown));
    at(t += 10, mc -> check("all 729 stacks back in the chest", heavyCount == 27 * 27, heavyCount));
    // churn: 4 chests picked up and put down every tick for 200 ticks
    at(t += 5, mc -> server(mc, StressScene::churnSetup));
    int churnStart = t + 1;
    for (int i = churnStart; i < churnStart + 200; i++) {
      at(i, mc -> server(mc, StressScene::churn));
    }
    at(t = churnStart + 205, mc -> server(mc, p -> {
      long after = countItems(p.serverLevel());
      check("800 moves: no item lost or duplicated", after == itemsBefore, after + " vs " + itemsBefore);
    }));
    // extremes
    at(t += 5, mc -> server(mc, p -> {
      TransporterConfig.SPAWNER_TIERS.set(List.of());
      p.serverLevel().setBlockAndUpdate(stage.offset(0, 0, 6), Blocks.SPAWNER.defaultBlockState());
      use(p, stage.offset(0, 0, 6), new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.NETHERITE).get()));
      check("empty spawner tier list: no truck takes spawners", p.serverLevel().getBlockState(stage.offset(0, 0, 6)).is(Blocks.SPAWNER), "taken");
      TransporterConfig.SPAWNER_TIERS.set(TransporterConfig.SPAWNER_TIERS.getDefault());
    }));
    // leave with a loaded truck and come back
    at(t += 5, mc -> server(mc, p -> {
      ItemStack truck = new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.DIAMOND).get());
      p.serverLevel().setBlockAndUpdate(stage.offset(2, 0, 6), Blocks.BARREL.defaultBlockState());
      ((Container) p.serverLevel().getBlockEntity(stage.offset(2, 0, 6))).setItem(0, new ItemStack(Items.TNT, 17));
      use(p, stage.offset(2, 0, 6), truck);
      p.getInventory().setItem(30, p.getMainHandItem().copy());
      p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }));
    at(t += 20, mc -> {
      rejoining = true;
      mc.level.disconnect();
      mc.disconnect(new TitleScreen());
    });
    afterRejoin(40, mc -> {
      CarriedBlock carried = mc.player.getInventory().getItem(30).get(ModRegistry.CARRIED);
      check("loaded truck survives leaving and rejoining", carried != null && carried.state().is(Blocks.BARREL), carried);
      check("carrying effects come back after rejoining", mc.player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS), "no modifier");
      server(mc, p -> {
        p.setItemInHand(InteractionHand.MAIN_HAND, p.getInventory().getItem(30));
        p.getInventory().setItem(30, ItemStack.EMPTY);
        use(p, stage.offset(4, -1, 6), p.getMainHandItem());
        BlockPos placed = stage.offset(4, 0, 6);
        check("barrel put down after rejoining with its TNT", p.serverLevel().getBlockEntity(placed) instanceof Container c && c.getItem(0).getCount() == 17, p.serverLevel().getBlockState(placed));
      });
    });
    afterRejoin(50, mc -> {
      ChestTransporter.LOG.info("[scene] DONE " + (checks - failures) + "/" + checks + (failures == 0 ? " passed" : " (" + failures + " failed)"));
      mc.level.disconnect();
      mc.disconnect(new TitleScreen());
      mc.stop();
    });
  }

  @SubscribeEvent
  public static void onFrame(RenderFrameEvent.Post event) {
    if (ENABLED) {
      frames++;
    }
  }

  @SubscribeEvent
  public static void onTick(ClientTickEvent.Post event) {
    if (!ENABLED) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (!opened && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
      mc.options.onboardAccessibility = false;
      mc.options.save();
      mc.setScreen(new TitleScreen());
      return;
    }
    if (mc.getOverlay() != null) {
      return;
    }
    if (!opened && mc.screen instanceof TitleScreen) {
      opened = true;
      GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
      mc.options.guiScale().set(3);
      mc.options.cloudStatus().set(CloudStatus.OFF);
      mc.options.framerateLimit().set(260);
      mc.options.enableVsync().set(false);
      mc.options.pauseOnLostFocus = false;
      mc.resizeDisplay();
      LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
      mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261016L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
      return;
    }
    if (rejoining && !rejoined && mc.level == null && mc.screen instanceof TitleScreen) {
      rejoined = true;
      tick = 0;
      mc.createWorldOpenFlows().openWorld(WORLD, () -> check("world opens again", false, "failed"));
      return;
    }
    if (mc.player == null || mc.getSingleplayerServer() == null) {
      return;
    }
    mc.options.pauseOnLostFocus = false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    mc.getToasts().clear();
    if (mc.screen instanceof PauseScreen) {
      mc.setScreen(null);
    }
    tick++;
    for (Pair<Integer, Consumer<Minecraft>> step : rejoined ? REJOIN_STEPS : STEPS) {
      if (step.getFirst() == tick) {
        try {
          step.getSecond().accept(mc);
        } catch (RuntimeException | LinkageError e) {
          check("step at tick " + tick + " ran", false, e);
          ChestTransporter.LOG.error("[scene] step failed", e);
        }
      }
    }
  }

  /* Server side */

  static void use(ServerPlayer player, BlockPos pos, ItemStack stack) {
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, 0.5), Direction.UP, pos, false);
    player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
  }

  private static void floor(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    BlockPos spawn = level.getSharedSpawnPos();
    stage = new BlockPos(spawn.getX(), 150, spawn.getZ());
    level.setDayTime(6000);
    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
    for (BlockPos pos : BlockPos.betweenClosed(stage.offset(-10, -1, -4), stage.offset(20, 8, 20))) {
      level.setBlock(pos, pos.getY() == stage.getY() - 1 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    for (int x = -8; x <= 18; x++) {
      for (int y = 0; y < 4; y++) {
        level.setBlock(stage.offset(x, y, 16), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
    }
    player.teleportTo(level, stage.getX() + 5.5, stage.getY(), stage.getZ() - 2.5, 0, 25);
  }

  /** 64 items on the floor and 64 item frames on the wall: vanilla chests, or loaded trucks */
  private static void showcase(ServerPlayer player, boolean trucks) {
    clearShowcase(player);
    ServerLevel level = player.serverLevel();
    TransporterTier[] tiers = TransporterTier.values();
    Block[] loads = {Blocks.CHEST, Blocks.BARREL, Blocks.TRAPPED_CHEST, Blocks.SPAWNER};
    for (int i = 0; i < 64; i++) {
      // the same blocks as plain items for the baseline
      ItemStack stack = trucks ? loaded(tiers[i % tiers.length], loads[i % loads.length]) : new ItemStack(loads[i % loads.length]);
      ItemEntity item = new ItemEntity(level, stage.getX() - 3.5 + (i % 16) * 1.0, stage.getY(), stage.getZ() + 2.5 + (i / 16) * 1.5, stack, 0, 0, 0);
      item.setNeverPickUp();
      item.setUnlimitedLifetime();
      level.addFreshEntity(item);
      ItemFrame frame = new ItemFrame(level, stage.offset(-8 + (i % 26) + i / 26 * 0, i / 26, 15), Direction.NORTH);
      frame.setItem(trucks ? loaded(tiers[(i + 1) % tiers.length], loads[(i + 1) % loads.length]) : new ItemStack(loads[(i + 1) % loads.length]));
      level.addFreshEntity(frame);
    }
  }

  private static void clearShowcase(ServerPlayer player) {
    for (Entity entity : player.serverLevel().getEntitiesOfClass(Entity.class, new AABB(stage).inflate(30), e -> e instanceof ItemEntity || e instanceof ItemFrame)) {
      entity.discard();
    }
  }

  private static ItemStack loaded(TransporterTier tier, Block block) {
    ItemStack stack = new ItemStack(ModRegistry.TRANSPORTERS.get(tier).get());
    stack.set(ModRegistry.CARRIED, new CarriedBlock(block.defaultBlockState(), new net.minecraft.nbt.CompoundTag()));
    return stack;
  }

  private static void heavyPickUp(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    BlockPos pos = stage.offset(0, 0, 0);
    level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
    for (int i = 0; i < 27; i++) {
      NonNullList<ItemStack> inside = NonNullList.withSize(27, ItemStack.EMPTY);
      for (int j = 0; j < 27; j++) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sword " + i + "-" + j));
        inside.set(j, sword);
      }
      ItemStack box = new ItemStack(Items.SHULKER_BOX);
      box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
      chest.setItem(i, box);
    }
    use(player, pos, new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.NETHERITE).get()));
  }

  private static void heavyPutDown(ServerPlayer player) {
    use(player, stage.offset(0, -1, 2), player.getMainHandItem());
    heavyCount = 0;
    if (player.serverLevel().getBlockEntity(stage.offset(0, 0, 2)) instanceof ChestBlockEntity chest) {
      for (int i = 0; i < 27; i++) {
        for (ItemStack ignored : chest.getItem(i).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
          heavyCount++;
        }
      }
    }
  }

  /** Four rows of chests and barrels with random items, one truck per row */
  private static final List<BlockPos> slots = new ArrayList<>();
  private static final List<ItemStack> trucks = new ArrayList<>();

  private static void churnSetup(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    slots.clear();
    trucks.clear();
    for (int row = 0; row < 4; row++) {
      for (int x = 0; x < 6; x++) {
        BlockPos pos = stage.offset(6 + x, 0, 2 + row * 2);
        slots.add(pos);
        if (x < 5) {
          level.setBlockAndUpdate(pos, (x % 2 == 0 ? Blocks.CHEST : Blocks.BARREL).defaultBlockState());
          Container container = (Container) level.getBlockEntity(pos);
          for (int i = 0; i < 27; i++) {
            if (RANDOM.nextBoolean()) {
              container.setItem(i, new ItemStack(Items.COBBLESTONE, 1 + RANDOM.nextInt(64)));
            }
          }
        }
      }
      trucks.add(new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.NETHERITE).get()));
    }
    itemsBefore = countItems(level);
  }

  /** Each row: the truck picks up a random block of the row, or puts its load down on a free spot of the row */
  private static void churn(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    for (int row = 0; row < 4; row++) {
      ItemStack truck = trucks.get(row);
      // repair as it goes: the test is about the contents, not the durability
      truck.setDamageValue(0);
      List<BlockPos> rowSlots = slots.subList(row * 6, row * 6 + 6);
      if (truck.has(ModRegistry.CARRIED)) {
        BlockPos free = rowSlots.stream().filter(level::isEmptyBlock).findAny().orElseThrow();
        use(player, free.below(), truck);
      } else {
        List<BlockPos> full = rowSlots.stream().filter(pos -> !level.isEmptyBlock(pos)).toList();
        use(player, full.get(RANDOM.nextInt(full.size())), truck);
      }
      trucks.set(row, player.getMainHandItem());
    }
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
  }

  /** Cobblestone in the churn rows, in the blocks and on the trucks */
  private static long countItems(ServerLevel level) {
    long count = 0;
    for (BlockPos pos : slots) {
      if (level.getBlockEntity(pos) instanceof Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
          count += container.getItem(i).getCount();
        }
      }
    }
    for (ItemStack truck : trucks) {
      CarriedBlock carried = truck.get(ModRegistry.CARRIED);
      if (carried != null) {
        for (Tag tag : carried.data().getList("Items", Tag.TAG_COMPOUND)) {
          count += ((net.minecraft.nbt.CompoundTag) tag).getInt("count");
        }
      }
    }
    count += level.getEntitiesOfClass(ItemEntity.class, new AABB(stage).inflate(30)).stream().mapToLong(e -> e.getItem().getCount()).sum();
    return count;
  }
}
