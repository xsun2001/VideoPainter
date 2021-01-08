package io.xsun.minecraft.videopainter;

import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class ImageReader {

    private final Logger logger;

    public ImageReader(Logger logger) {
        this.logger = logger;
    }

    private static double rgb2grey(int rgb) {
        int b = rgb & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int r = (rgb >> 16) & 0xFF;
        return (0.3 * r + 0.59 * g + 0.11 * b) / 255;
    }

    public int[] read(Path imageLocation, int width, int height) throws IOException {
        logger.info("Read image [{}]", imageLocation.toString());
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

}
