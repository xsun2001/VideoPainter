package io.xsun.minecraft.videopainter;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class BukkitPlugin extends JavaPlugin {

    private static final String[] helpStrings = {
            "This is xsun2001's VideoPainter plugin",
            "You can use it to display video in a block matrix",
            "Set display region: /vpainter region set x y z w h orientation",
            "Unset display region: /vpainter region unset",
            "Clear display region: /vpainter region clear",
            "List video (image sequence) found: /vpainter list",
            "Display an image: /vpainter image series index",
            "Set video: /vpainter video set series",
            "Set video fps: /vpainter video fps fps",
            "Play video: /vpainter video play",
            "Pause video: /vpainter video pause",
    };
    private final Logger logger = getSLF4JLogger();
    private boolean regionAvailable = false;
    private int rx, ry, rz, rw, rh, ro; // Region Parameter
    private BukkitRunnable videoPlayer;
    private boolean pauseFlag;

    private static double rgb2grey(int rgb) {
        int b = rgb & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int r = (rgb >> 16) & 0xFF;
        return (0.3 * r + 0.59 * g + 0.11 * b) / 255;
    }

    private static void regionBlockSet(World world, int x, int y, int z, int w, int h, int o, BlockMaterialSupplier supplier) {
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int cx = x, cy = y, cz = z;
                if (o == 0) cx += j;
                if (o == 1) cz += j;
                cy += i;
                var newMaterial = supplier.materialAt(i, j);
                var block = world.getBlockAt(cx, cy, cz);
                if (!block.getType().equals(newMaterial)) {
                    block.setType(newMaterial);
                }
            }
        }
    }

    private void sendToBoth(CommandSender mcPlayer, String msg) {
        logger.info(msg);
        if (mcPlayer instanceof Player) {
            mcPlayer.sendMessage(msg);
        }
    }

    private void printHelp(CommandSender sender) {
        sender.sendMessage(helpStrings);
    }

    private World getWorld(CommandSender sender) {
        if (sender instanceof Player) return ((Player) sender).getWorld();
        return getServer().getWorlds().get(0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        assert (command.getName().equalsIgnoreCase("vpainter"));
        boolean goodCommand = true;
        if (args.length == 0) {
            goodCommand = false;
        } else {
            switch (args[0]) {
                case "help":
                    printHelp(sender);
                    break;
                case "region":
                    if (args.length == 2) {
                        if (args[1].equals("unset")) {
                            regionAvailable = false;
                            sendToBoth(sender, "Unset region successfully");
                        } else if (args[1].equals("clear")) {
                            if (regionAvailable) {
                                regionBlockSet(getWorld(sender), rx, ry, rz, rw, rh, ro, (int x, int y) -> Material.AIR);
                                sendToBoth(sender, "Region cleared");
                            } else {
                                sendToBoth(sender, "No region available");
                            }
                        } else {
                            goodCommand = false;
                        }
                    } else if (args.length == 8 && args[1].equals("set")) {
                        try {
                            int[] regionParameter = Arrays.stream(args).skip(2).mapToInt(Integer::parseInt).toArray();
                            rx = regionParameter[0];
                            ry = regionParameter[1];
                            rz = regionParameter[2];
                            rw = regionParameter[3];
                            rh = regionParameter[4];
                            ro = regionParameter[5];
                            goodCommand = regionAvailable = verifyRegion();
                        } catch (NumberFormatException e) {
                            logger.warn("Error in processing region parameter", e);
                            goodCommand = regionAvailable = false;
                        }
                        sender.sendMessage(regionAvailable ? "Set region successfully" : "Please check your region parameter");
                    } else {
                        goodCommand = false;
                    }
                    break;
                case "list":
                    if (args.length == 1) {
                        try {
                            var vseqPath = Paths.get("video");
                            var directories = Files.list(vseqPath)
                                    .filter(Files::isDirectory)
                                    .map(Path::getFileName)
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", "));
                            if (directories.isEmpty()) {
                                sendToBoth(sender, "No video found");
                            } else {
                                sendToBoth(sender, "Found sequences: ");
                                sendToBoth(sender, directories);
                            }
                        } catch (IOException e) {
                            logger.error("Cannot list vseq directory", e);
                            sendToBoth(sender, "No video found");
                        }
                    } else {
                        goodCommand = false;
                    }
                    break;
                case "image":
                    if (args.length == 3) {
                        var sequence = args[1];
                        var index = 0;
                        try {
                            index = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            logger.warn("Error in processing image index", e);
                        }
                        try {
                            int[] imageData = readImage(Paths.get("video"), sequence, index, rw, rh);
                            regionBlockSet(getWorld(sender), rx, ry, rz, rw, rh, ro, (int i, int j) -> imageData[(rh - i - 1) * rw + j] == 0 ? Material.WHITE_WOOL : Material.BLACK_WOOL);
                            sender.sendMessage("Done");
                        } catch (FileNotFoundException e) {
                            logger.warn("Invalid image path", e);
                            sender.sendMessage("Invalid image path");
                        } catch (IOException e) {
                            logger.error("Unknown error in displaying image", e);
                            sender.sendMessage(TextComponent.fromLegacyText("Unknown error in displaying image", ChatColor.RED));
                        }
                    } else {
                        goodCommand = false;
                    }
                    break;
                case "video":
                    if (args.length == 2) {
                        if (args[1].equals("resume")) {
                            if (videoPlayer == null) {
                                sendToBoth(sender, "No video is playing");
                            } else {
                                pauseFlag = false;
                            }
                        } else if (args[1].equals("pause")) {
                            if (videoPlayer == null) {
                                sendToBoth(sender, "No video is playing");
                            } else {
                                pauseFlag = true;
                            }
                        } else if (args[1].equals("stop")) {
                            if (videoPlayer == null) {
                                sendToBoth(sender, "No video is playing");
                            } else {
                                videoPlayer.cancel();
                                videoPlayer = null;
                            }
                        }
                    } else if (args.length == 4) {
                        if (args[1].equals("play")) {
                            if (videoPlayer != null) {
                                sendToBoth(sender, "A video is playing");
                            } else {
                                try {
                                    videoPlayer = new VideoPlayingThread(getWorld(sender), sender, args[2]);
                                    var fps = Integer.parseInt(args[3]);
                                    pauseFlag = false;
                                    videoPlayer.runTaskTimer(this, 20, 20 / fps);
                                } catch (NumberFormatException e) {
                                    logger.warn("Error in processing fps", e);
                                    sender.sendMessage("Please input valid fps");
                                }
                            }
                        }
                    } else {
                        goodCommand = false;
                    }
                    break;
                default:
                    goodCommand = false;
            }
        }
        if (!goodCommand) printHelp(sender);
        return false;
    }

    private boolean verifyRegion() {
        return ry > 0 && rw > 0 && rh > 0 && (ro == 0 || ro == 1);
    }

    public int[] readImage(Path base, String sequenceName, int index, int width, int height) throws IOException {
        var imageLocation = base.resolve(sequenceName).resolve("out" + index + ".png");
        logger.debug("Read image [{}]", imageLocation.toString());
        var image = ImageIO.read(imageLocation.toFile());
        logger.debug("Raw image dimension: width={}, height={}", image.getWidth(), image.getHeight());
        logger.debug("Rescale to width={}, height={}", width, height);
        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        scaled.getGraphics().drawImage(image.getScaledInstance(width, height, Image.SCALE_DEFAULT), 0, 0, null);
        int[] data = new int[width * height];
        scaled.getRGB(0, 0, width, height, data, 0, width);
        Arrays.setAll(data, i -> rgb2grey(data[i]) >= 0.5 ? 1 : 0);
        return data;
    }

    @FunctionalInterface
    public interface BlockMaterialSupplier {
        Material materialAt(int x, int y);
    }

    public class VideoPlayingThread extends BukkitRunnable implements Runnable {
        private final World world;
        private final CommandSender sender;
        private final String video;
        private int index = 0;

        public VideoPlayingThread(World world, CommandSender sender, String video) {
            this.world = world;
            this.sender = sender;
            this.video = video;
        }

        @Override
        public void run() {
            if (pauseFlag) return;
            try {
                int[] imageData = readImage(Paths.get("video"), video, ++index, rw, rh);
                regionBlockSet(world, rx, ry, rz, rw, rh, ro, (int i, int j) -> imageData[(rh - i - 1) * rw + j] == 0 ? Material.WHITE_WOOL : Material.BLACK_WOOL);
                sendToBoth(sender, String.valueOf(index));
            } catch (IOException e) {
                sendToBoth(sender, "End of play");
                cancel();
            }
        }
    }
}