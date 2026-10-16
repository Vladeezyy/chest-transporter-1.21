package dev.vladeezyy.chesttransporter.selftest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.TransporterTier;
import dev.vladeezyy.chesttransporter.client.ClientSetup;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client scene, inert unless {@code -Dchest_transporter.scene=main} ({@code tools/scenetest.sh}): every truck empty
 * and loaded in the hotbar and the inventory, a loaded truck in the hand in first and third person, on the ground and
 * in an item frame. Checks the models (no missing ones, no magenta on screen), the tooltip and the translations, saves
 * screenshots to {@code run/screenshots/scene_*.png}, logs {@code [scene] CHECK OK/FAIL}, then quits.
 */
@EventBusSubscriber(modid = ChestTransporter.MOD_ID, value = Dist.CLIENT)
public final class ClientScene {
  private static final String MODE = System.getProperty("chest_transporter.scene", "");
  /** {@code -Pscene=pose}: only the hand views, to tune the display transforms */
  private static final boolean POSE = MODE.equals("pose");
  private static final boolean ENABLED = MODE.equals("main") || POSE;
  static final String LANG = System.getProperty("chest_transporter.scene.lang", "en_us");
  private static final String WORLD = "ct_scene";
  private static final List<Pair<Integer, Consumer<Minecraft>>> STEPS = new ArrayList<>();
  private static boolean opened;
  private static int tick, checks, failures;
  private static BlockPos stage;

  static void at(int t, Consumer<Minecraft> action) {
    STEPS.add(Pair.of(t, action));
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

  static void shot(Minecraft mc, String name) {
    Screenshot.grab(mc.gameDirectory, "scene_" + name + ".png", mc.getMainRenderTarget(), c -> ChestTransporter.LOG.info("[scene] " + c.getString()));
  }

  static void lookAt(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
    double dx = tx - x, dy = ty - (y + player.getEyeHeight()), dz = tz - z;
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    player.teleportTo(player.serverLevel(), stage.getX() + x, stage.getY() + y, stage.getZ() + z, yaw, pitch);
  }

  /** A truck of the tier with the block on it */
  static ItemStack loaded(TransporterTier tier, Block block, int stacks) {
    ItemStack stack = new ItemStack(ModRegistry.TRANSPORTERS.get(tier).get());
    CompoundTag data = new CompoundTag();
    ListTag items = new ListTag();
    for (int i = 0; i < stacks; i++) {
      CompoundTag item = new CompoundTag();
      item.putByte("Slot", (byte) i);
      item.putString("id", "minecraft:cobblestone");
      item.putInt("count", 64);
      items.add(item);
    }
    if (stacks > 0) {
      data.put("Items", items);
    }
    if (block == Blocks.SPAWNER) {
      CompoundTag entity = new CompoundTag();
      entity.putString("id", "minecraft:zombie");
      CompoundTag spawnData = new CompoundTag();
      spawnData.put("entity", entity);
      data.put("SpawnData", spawnData);
    }
    stack.set(ModRegistry.CARRIED, new CarriedBlock(block.defaultBlockState(), data));
    return stack;
  }

  static {
    if (POSE) {
      poseSteps();
    } else {
      mainSteps();
    }
  }

  private static void poseSteps() {
    int t = 40;
    at(t, mc -> server(mc, ClientScene::build));
    at(t += 30, mc -> server(mc, p -> {
      lookAt(p, 0.5, 0, -3, 0.5, 1, 3);
      p.getInventory().clearContent();
      p.getInventory().setItem(0, loaded(TransporterTier.IRON, Blocks.CHEST, 1));
      p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, loaded(TransporterTier.DIAMOND, Blocks.BARREL, 1));
      p.getInventory().selected = 0;
    }));
    at(t += 20, mc -> shot(mc, "pose_first"));
    at(t += 5, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
    at(t += 15, mc -> shot(mc, "pose_third_front"));
    at(t += 5, mc -> server(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -90, 10)));
    at(t += 15, mc -> shot(mc, "pose_third_side"));
    at(t += 5, mc -> {
      mc.level.disconnect();
      mc.disconnect(new TitleScreen());
      mc.stop();
    });
  }

  private static void mainSteps() {
    int t = 40;
    at(t, mc -> server(mc, ClientScene::build));
    at(t += 40, ClientScene::checkModels);
    // hotbar: the six empty trucks, then loaded ones
    at(t += 5, mc -> server(mc, p -> {
      lookAt(p, 0.5, 0, -3, 0.5, 1, 3);
      p.getInventory().clearContent();
      int slot = 0;
      for (TransporterTier tier : TransporterTier.values()) {
        p.getInventory().setItem(slot++, new ItemStack(ModRegistry.TRANSPORTERS.get(tier).get()));
      }
      p.getInventory().setItem(6, loaded(TransporterTier.IRON, Blocks.CHEST, 5));
      p.getInventory().setItem(7, loaded(TransporterTier.DIAMOND, Blocks.BARREL, 2));
      p.getInventory().setItem(8, loaded(TransporterTier.NETHERITE, Blocks.SPAWNER, 0));
      p.getInventory().setItem(9, loaded(TransporterTier.GOLDEN, Blocks.TRAPPED_CHEST, 1));
      p.getInventory().setItem(10, loaded(TransporterTier.COPPER, Blocks.CHEST, 0));
      p.getInventory().selected = 6;
    }));
    // survival: the inventory screen instead of the creative tabs
    at(t += 10, mc -> server(mc, p -> p.setGameMode(GameType.SURVIVAL)));
    at(t += 10, mc -> {
      mc.options.hideGui = false;
      mc.setScreen(new InventoryScreen(mc.player));
    });
    at(t += 15, mc -> {
      shot(mc, "inventory");
      checkNoMissingTexture(mc, "inventory");
      mc.setScreen(null);
      server(mc, p -> {
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
      });
    });
    // in the hand, first person
    at(t += 10, mc -> {
      mc.player.getInventory().selected = 6;
      shot(mc, "hand_chest");
      checkNoMissingTexture(mc, "hand");
    });
    at(t += 10, mc -> mc.player.getInventory().selected = 7);
    at(t += 10, mc -> shot(mc, "hand_barrel"));
    at(t += 10, mc -> mc.player.getInventory().selected = 2);
    at(t += 10, mc -> shot(mc, "hand_empty"));
    // third person from the front
    at(t += 5, mc -> {
      mc.player.getInventory().selected = 6;
      mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    });
    at(t += 15, mc -> {
      shot(mc, "third_person");
      checkNoMissingTexture(mc, "third person");
      mc.options.setCameraType(CameraType.FIRST_PERSON);
    });
    // on the ground and in item frames
    at(t += 5, mc -> server(mc, p -> lookAt(p, 3.5, 0, -1.5, 3.5, 0.3, 2.5)));
    at(t += 25, mc -> {
      shot(mc, "ground_frames");
      checkNoMissingTexture(mc, "ground and frames");
    });
    at(t += 5, ClientScene::checkTooltip);
    at(t += 5, ClientScene::checkTranslations);
    at(t += 10, mc -> {
      ChestTransporter.LOG.info("[scene] DONE " + (checks - failures) + "/" + checks + (failures == 0 ? " passed" : " (" + failures + " failed)"));
      mc.level.disconnect();
      mc.disconnect(new TitleScreen());
      mc.stop();
    });
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
    if (!opened && mc.getOverlay() != null) {
      return;
    }
    if (!opened && mc.screen instanceof TitleScreen) {
      if (!mc.options.languageCode.equals(LANG)) {
        mc.options.languageCode = LANG;
        mc.getLanguageManager().setSelected(LANG);
        mc.options.save();
        mc.reloadResourcePacks();
        return;
      }
      opened = true;
      GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
      mc.options.guiScale().set(3);
      mc.options.cloudStatus().set(CloudStatus.OFF);
      mc.options.pauseOnLostFocus = false;
      mc.resizeDisplay();
      LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
      mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261016L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
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
    for (Pair<Integer, Consumer<Minecraft>> step : STEPS) {
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

  /** Grass floor in the sky, trucks on the ground and in item frames on a wall */
  private static void build(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    BlockPos spawn = level.getSharedSpawnPos();
    stage = new BlockPos(spawn.getX(), 140, spawn.getZ());
    level.setDayTime(6000);
    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
    for (BlockPos pos : BlockPos.betweenClosed(stage.offset(-6, -1, -6), stage.offset(10, 6, 8))) {
      level.setBlock(pos, pos.getY() == stage.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    for (int x = 0; x < 7; x++) {
      level.setBlock(stage.offset(x, 0, 3), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
      level.setBlock(stage.offset(x, 1, 3), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
    }
    ItemStack[] onWall = {new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.WOODEN).get()), loaded(TransporterTier.IRON, Blocks.CHEST, 3),
      loaded(TransporterTier.DIAMOND, Blocks.BARREL, 1), loaded(TransporterTier.NETHERITE, Blocks.SPAWNER, 0)};
    for (int i = 0; i < onWall.length; i++) {
      ItemFrame frame = new ItemFrame(level, stage.offset(2 + i, 1, 2), Direction.NORTH);
      frame.setItem(onWall[i]);
      level.addFreshEntity(frame);
    }
    ItemStack[] onGround = {loaded(TransporterTier.COPPER, Blocks.CHEST, 1), new ItemStack(ModRegistry.TRANSPORTERS.get(TransporterTier.GOLDEN).get()),
      loaded(TransporterTier.GOLDEN, Blocks.TRAPPED_CHEST, 0)};
    for (int i = 0; i < onGround.length; i++) {
      ItemEntity item = new ItemEntity(level, stage.getX() + 2.5 + i, stage.getY(), stage.getZ() + 1.5, onGround[i], 0, 0, 0);
      item.setNeverPickUp();
      item.setUnlimitedLifetime();
      level.addFreshEntity(item);
    }
    player.getAbilities().flying = true;
    player.onUpdateAbilities();
    lookAt(player, 0.5, 0, -3, 0.5, 1, 3);
  }

  private static void checkModels(Minecraft mc) {
    BakedModel missing = mc.getModelManager().getMissingModel();
    List<String> bad = new ArrayList<>();
    for (TransporterTier tier : TransporterTier.values()) {
      if (mc.getModelManager().getModel(ClientSetup.frameModel(tier)) == missing) {
        bad.add(tier.itemName() + "_frame");
      }
      Item item = ModRegistry.TRANSPORTERS.get(tier).get();
      BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
      if (model == missing || !model.isCustomRenderer()) {
        bad.add(tier.itemName());
      }
    }
    check("every truck has its frame model and the item renderer", bad.isEmpty(), bad);
  }

  private static void checkTooltip(Minecraft mc) {
    List<Component> lines = loaded(TransporterTier.IRON, Blocks.SPAWNER, 0).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
    String text = String.join(" | ", lines.stream().map(Component::getString).toList());
    String spawner = Blocks.SPAWNER.getName().getString();
    String zombie = EntityType.ZOMBIE.getDescription().getString();
    check("tooltip of a loaded truck names the block and the mob", text.contains(spawner) && text.contains(zombie), text);
    List<Component> chest = loaded(TransporterTier.IRON, Blocks.CHEST, 5).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
    check("tooltip counts the stacks inside", chest.stream().anyMatch(line -> line.getString().contains("5")), chest);
  }

  /** Every key the mod shows is in the language, and the language file has no other keys */
  private static void checkTranslations(Minecraft mc) {
    List<String> needed = new ArrayList<>();
    for (Item item : BuiltInRegistries.ITEM) {
      if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ChestTransporter.MOD_ID)) {
        needed.add(item.getDescriptionId());
      }
    }
    for (String key : List.of("empty", "carrying", "stacks", "loot", "spawner", "moves_left")) {
      needed.add("item.chest_transporter." + key);
    }
    for (String key : List.of("not_transportable", "spawner_disabled", "spawner_tier", "protected", "locked", "too_heavy", "no_room")) {
      needed.add("message.chest_transporter." + key);
    }
    Language language = Language.getInstance();
    List<String> missing = needed.stream().filter(key -> !language.has(key)).toList();
    check("translations: every text in " + LANG, missing.isEmpty(), missing);
    List<String> unused = new ArrayList<>();
    try (var reader = mc.getResourceManager().openAsReader(ChestTransporter.id("lang/" + LANG + ".json"))) {
      for (String key : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().keySet()) {
        if (!needed.contains(key)) {
          unused.add(key);
        }
      }
    } catch (Exception e) {
      unused.add(e.toString());
    }
    check("translations: no unused keys in " + LANG, unused.isEmpty(), unused);
  }

  /** The missing texture is magenta and black: count magenta pixels of the frame */
  private static void checkNoMissingTexture(Minecraft mc, String name) {
    int magenta = 0;
    try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
      for (int pixel : image.getPixelsRGBA()) {
        int r = pixel & 0xFF, g = pixel >> 8 & 0xFF, b = pixel >> 16 & 0xFF;
        if (r > 200 && b > 200 && g < 40) {
          magenta++;
        }
      }
    }
    check(name + ": no missing texture on screen", magenta < 50, magenta + " magenta pixels");
  }
}
