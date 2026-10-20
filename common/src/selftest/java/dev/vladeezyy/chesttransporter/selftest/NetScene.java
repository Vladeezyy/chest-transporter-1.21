package dev.vladeezyy.chesttransporter.selftest;

import dev.vladeezyy.chesttransporter.CarriedBlock;
import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Multiplayer check ({@code tools/nettest.sh}), inert unless {@code chest_transporter.scene} is {@code net}: the dev
 * client joins a dev dedicated server as an operator (spawner tier list "diamond" in its config), builds with commands
 * and clicks like a player: picks up a chest, puts it down and opens it, is refused a spawner with an iron truck, moves
 * the chest of a chest minecart and back. Logs {@code [scene] CHECK/DONE}, then quits.
 */
public final class NetScene {
  private static final boolean ENABLED = System.getProperty("chest_transporter.scene", "").equals("net");
  private static final int Y = 200;
  private static final BlockPos CHEST = new BlockPos(3, Y, 3), SPAWNER = new BlockPos(6, Y, 3), PLACED = new BlockPos(4, Y, 2);
  private static int tick, checks, failures;
  private static boolean joining;
  private static boolean quitting;

  static void check(String name, boolean ok, Object detail) {
    checks++;
    if (!ok) {
      failures++;
    }
    ChestTransporter.LOG.info("[scene] CHECK " + (ok ? "OK " : "FAIL ") + name + (ok ? "" : " (" + detail + ")"));
  }

  private static void command(Minecraft mc, String command) {
    mc.player.connection.sendCommand(command);
  }

  /** Right click the top of the block, as a player does */
  private static void useTop(Minecraft mc, BlockPos pos) {
    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
  }

  private static <T extends AbstractMinecart> T nearest(Minecraft mc, Class<T> type) {
    T found = null;
    for (Entity entity : mc.level.entitiesForRendering()) {
      if (type.isInstance(entity) && entity.distanceToSqr(mc.player) < 25 && (found == null || entity.distanceToSqr(mc.player) < found.distanceToSqr(mc.player))) {
        found = type.cast(entity);
      }
    }
    if (found == null) {
      throw new IllegalStateException("no " + type.getSimpleName() + " next to the player");
    }
    return found;
  }

  /** After every client tick (wired by the loader's test hooks) */
  public static void onClientTick() {
    if (!ENABLED) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
      mc.options.onboardAccessibility = false;
      mc.options.save();
      mc.setScreen(new TitleScreen());
      return;
    }
    // 26.x ignores --quickPlayMultiplayer in the dev run (the client waits on the title screen): join from code
    if (!joining && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
      joining = true;
      net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc,
        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("localhost:25565"),
        new net.minecraft.client.multiplayer.ServerData("Test server", "localhost:25565", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
      return;
    }
    // kicked or the server went away: fail and quit instead of waiting on the screen until the script's timeout
    if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen && !quitting) {
      quitting = true;
      check("stayed connected to the server", false, "disconnected");
      ChestTransporter.LOG.info("[scene] DONE " + (checks - failures) + "/" + checks + " (" + failures + " failed)");
      mc.stop();
      return;
    }
    if (mc.player == null || mc.getConnection() == null) {
      return;
    }
    mc.options.pauseOnLostFocus = false;
    mc.getToasts().clear();
    tick++;
    try {
      step(mc);
    } catch (RuntimeException e) {
      check("net step at tick " + tick + " ran", false, e);
      ChestTransporter.LOG.error("[scene] net step failed", e);
    }
  }

  private static void step(Minecraft mc) {
    switch (tick) {
      case 40 -> {
        check("joined the dedicated server with the mod", mc.getSingleplayerServer() == null && mc.getConnection().getConnection().isConnected(), "no connection");
        command(mc, "time set day");
        command(mc, "gamerule doMobSpawning false");
        command(mc, "forceload add -3 -3 10 10");
      }
      case 50 -> {
        command(mc, "fill -3 " + (Y - 1) + " -3 10 " + (Y - 1) + " 10 minecraft:stone");
        command(mc, "fill -3 " + Y + " -3 10 " + (Y + 3) + " 10 minecraft:air");
        command(mc, "kill @e[type=!minecraft:player]");
        command(mc, "setblock " + pos(CHEST) + " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},{Slot:5b,id:\"minecraft:apple\",count:3}]}");
        command(mc, "setblock " + pos(SPAWNER) + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
        command(mc, "summon minecraft:chest_minecart 1.5 " + Y + " 1.5 {Items:[{Slot:2b,id:\"minecraft:emerald\",count:9}]}");
      }
      case 60 -> {
        command(mc, "gamemode survival");
        command(mc, "tp @s 4.5 " + Y + " 0.5 0 30");
        command(mc, "item replace entity @s weapon.mainhand with chest_transporter:iron_chest_transporter");
      }
      case 80 -> useTop(mc, CHEST);
      case 95 -> {
        CarriedBlock carried = mc.player.getMainHandItem().get(ModRegistry.carried());
        check("chest picked up: gone from the world, on the truck with its 2 stacks", mc.level.getBlockState(CHEST).isAir() && carried != null
          && carried.data().getList("Items", Tag.TAG_COMPOUND).size() == 2, mc.level.getBlockState(CHEST) + " " + carried);
        check("the client slows down the loaded player", mc.player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS), "no modifier");
      }
      case 100 -> useTop(mc, PLACED.below());
      case 115 -> {
        check("chest put down where clicked, the truck is empty and used once", mc.level.getBlockState(PLACED).is(Blocks.CHEST)
          && !mc.player.getMainHandItem().has(ModRegistry.carried()) && mc.player.getMainHandItem().getDamageValue() == 1, mc.level.getBlockState(PLACED));
        check("the client stops slowing the player", !mc.player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CarryEffects.SLOWNESS), "modifier left");
        command(mc, "item replace entity @s weapon.offhand with minecraft:air");
      }
      case 120 -> {
        // open it with an empty hand
        command(mc, "item replace entity @s weapon.mainhand with minecraft:air");
      }
      case 130 -> useTop(mc, PLACED);
      case 145 -> {
        boolean ok = mc.player.containerMenu instanceof ChestMenu menu && menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 12
          && menu.getSlot(5).getItem().is(Items.APPLE);
        check("the moved chest opens with its items in place", ok, mc.player.containerMenu);
        mc.player.closeContainer();
      }
      // spawner refused: the server config allows only diamond trucks
      case 150 -> command(mc, "item replace entity @s weapon.mainhand with chest_transporter:iron_chest_transporter");
      case 160 -> useTop(mc, SPAWNER);
      case 175 -> check("iron truck refused the spawner (server config: diamond only)", mc.level.getBlockState(SPAWNER).is(Blocks.SPAWNER)
        && !mc.player.getMainHandItem().has(ModRegistry.carried()), mc.level.getBlockState(SPAWNER));
      case 180 -> command(mc, "item replace entity @s weapon.mainhand with chest_transporter:diamond_chest_transporter");
      case 190 -> useTop(mc, SPAWNER);
      case 205 -> check("diamond truck took the spawner", mc.level.getBlockState(SPAWNER).isAir() && mc.player.getMainHandItem().has(ModRegistry.carried()), mc.level.getBlockState(SPAWNER));
      // minecart
      case 210 -> command(mc, "item replace entity @s weapon.mainhand with chest_transporter:copper_chest_transporter");
      case 220 -> mc.gameMode.interact(mc.player, nearest(mc, MinecartChest.class), InteractionHand.MAIN_HAND);
      case 235 -> {
        CarriedBlock carried = mc.player.getMainHandItem().get(ModRegistry.carried());
        check("chest of the minecart on the truck, a plain minecart left", carried != null && carried.state().is(Blocks.CHEST) && nearest(mc, Minecart.class) != null, carried);
      }
      case 240 -> mc.gameMode.interact(mc.player, nearest(mc, Minecart.class), InteractionHand.MAIN_HAND);
      case 255 -> check("chest back into the minecart", nearest(mc, MinecartChest.class) != null && !mc.player.getMainHandItem().has(ModRegistry.carried()), "no chest minecart");
      case 265 -> {
        ChestTransporter.LOG.info("[scene] DONE " + (checks - failures) + "/" + checks + (failures == 0 ? " passed" : " (" + failures + " failed)"));
        mc.level.disconnect();
        mc.disconnect(new TitleScreen());
        mc.stop();
      }
      default -> { }
    }
  }

  private static String pos(BlockPos pos) {
    return pos.getX() + " " + pos.getY() + " " + pos.getZ();
  }
}
