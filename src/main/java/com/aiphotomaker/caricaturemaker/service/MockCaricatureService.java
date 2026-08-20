package com.aiphotomaker.caricaturemaker.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Placeholder implementation used until a real AI image-generation API
 * (e.g. OpenAI Images, Stability AI) is wired in. Produces a transparent-background
 * line-art sketch (silhouette + edges only, no color fill) locally so the full
 * upload -> convert -> return flow can be exercised end to end without any
 * external API key.
 *
 * Background removal is a color-similarity flood fill grown inward from the
 * image border, not real person segmentation - it works best on photos with a
 * fairly uniform background.
 */
@Service
@ConditionalOnProperty(prefix = "app.caricature", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockCaricatureService implements CaricatureService {

	private static final int EDGE_THRESHOLD = 80;
	private static final int WALL_THRESHOLD = 70;
	private static final int BACKGROUND_TOLERANCE_SQ = 40 * 40 * 3;

	@Override
	public byte[] convert(MultipartFile photo) throws IOException {
		BufferedImage uploaded = ImageIO.read(photo.getInputStream());
		if (uploaded == null) {
			throw new IOException("업로드된 파일을 이미지로 읽을 수 없습니다.");
		}
		// Output aspect ratio is fixed to 1:1 for now; center-crop to square before processing.
		BufferedImage source = cropToSquare(uploaded);

		int width = source.getWidth();
		int height = source.getHeight();
		int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

		double[] gradient = sobelGradient(pixels, width, height);
		boolean[] edges = new boolean[pixels.length];
		boolean[] wall = new boolean[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			edges[i] = gradient[i] > EDGE_THRESHOLD;
			wall[i] = gradient[i] > WALL_THRESHOLD;
		}

		boolean[] background = floodFillBackground(pixels, width, height, wall);

		int[] outPixels = new int[pixels.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int idx = y * width + x;
				if (background[idx]) {
					outPixels[idx] = 0x00000000;
					continue;
				}
				boolean isOutline = edges[idx] || touchesBackground(background, width, height, x, y);
				outPixels[idx] = isOutline ? 0xFF000000 : 0xFFFFFFFF;
			}
		}

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		result.setRGB(0, 0, width, height, outPixels, 0, width);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(result, "png", out);
		return out.toByteArray();
	}

	private BufferedImage cropToSquare(BufferedImage source) {
		int size = Math.min(source.getWidth(), source.getHeight());
		int x = (source.getWidth() - size) / 2;
		int y = (source.getHeight() - size) / 2;
		return source.getSubimage(x, y, size, size);
	}

	private boolean[] floodFillBackground(int[] pixels, int width, int height, boolean[] wall) {
		boolean[] background = new boolean[pixels.length];
		Deque<Integer> queue = new ArrayDeque<>();

		for (int x = 0; x < width; x++) {
			enqueueBackground(background, queue, x, 0, width);
			enqueueBackground(background, queue, x, height - 1, width);
		}
		for (int y = 0; y < height; y++) {
			enqueueBackground(background, queue, 0, y, width);
			enqueueBackground(background, queue, width - 1, y, width);
		}

		int[] dx = {1, -1, 0, 0};
		int[] dy = {0, 0, 1, -1};
		while (!queue.isEmpty()) {
			int idx = queue.poll();
			int x = idx % width;
			int y = idx / width;
			for (int d = 0; d < 4; d++) {
				int nx = x + dx[d];
				int ny = y + dy[d];
				if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
					continue;
				}
				int nIdx = ny * width + nx;
				if (background[nIdx] || wall[nIdx]) {
					continue;
				}
				if (colorDistanceSq(pixels[idx], pixels[nIdx]) <= BACKGROUND_TOLERANCE_SQ) {
					background[nIdx] = true;
					queue.add(nIdx);
				}
			}
		}
		return background;
	}

	private void enqueueBackground(boolean[] background, Deque<Integer> queue, int x, int y, int width) {
		int idx = y * width + x;
		if (!background[idx]) {
			background[idx] = true;
			queue.add(idx);
		}
	}

	private int colorDistanceSq(int rgbA, int rgbB) {
		int dr = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
		int dg = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
		int db = (rgbA & 0xFF) - (rgbB & 0xFF);
		return dr * dr + dg * dg + db * db;
	}

	private boolean touchesBackground(boolean[] background, int width, int height, int x, int y) {
		int[] dx = {1, -1, 0, 0};
		int[] dy = {0, 0, 1, -1};
		for (int d = 0; d < 4; d++) {
			int nx = x + dx[d];
			int ny = y + dy[d];
			if (nx < 0 || ny < 0 || nx >= width || ny >= height || background[ny * width + nx]) {
				return true;
			}
		}
		return false;
	}

	private double[] sobelGradient(int[] pixels, int width, int height) {
		int[] luma = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			int rgb = pixels[i];
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			luma[i] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
		}

		double[] gradient = new double[pixels.length];
		for (int y = 1; y < height - 1; y++) {
			for (int x = 1; x < width - 1; x++) {
				int gx = luma[idx(x + 1, y - 1, width)] + 2 * luma[idx(x + 1, y, width)] + luma[idx(x + 1, y + 1, width)]
						- luma[idx(x - 1, y - 1, width)] - 2 * luma[idx(x - 1, y, width)] - luma[idx(x - 1, y + 1, width)];
				int gy = luma[idx(x - 1, y + 1, width)] + 2 * luma[idx(x, y + 1, width)] + luma[idx(x + 1, y + 1, width)]
						- luma[idx(x - 1, y - 1, width)] - 2 * luma[idx(x, y - 1, width)] - luma[idx(x + 1, y - 1, width)];
				gradient[idx(x, y, width)] = Math.sqrt((double) (gx * gx + gy * gy));
			}
		}
		return gradient;
	}

	private int idx(int x, int y, int width) {
		return y * width + x;
	}
}
