package com.aiphotomaker.caricaturemaker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/**
 * Calls the Gemini image generation API (Nano Banana) to turn an uploaded
 * photo into a hand-drawn, black-and-white caricature line-art illustration.
 */
@Service
@ConditionalOnProperty(prefix = "app.caricature", name = "provider", havingValue = "gemini")
public class GeminiCaricatureService implements CaricatureService {

	private static final String STYLE_PROMPT = """
			Redraw this photo as a black-and-white hand-drawn line-art illustration of ONLY \
			the people in it, like an ink sketch traced over just the people. This is not a \
			portrait of a single subject, and it is not a drawing of the scene or setting.
			- Include every person visible anywhere in the photo, including people who are \
			small, partially visible, blurry, or far in the background. Do not drop, crop out, \
			or merge any person into the background - if a person appears in the photo, they \
			must appear in the illustration.
			- Preserve the original composition exactly: keep every person's pose, position, \
			and size relative to each other and to the frame the same as in the source photo. \
			Do not zoom in, crop, or re-frame around one person.
			- Simplify facial features into minimal, iconic hand-drawn linework: small dot or \
			simple line eyes, the nose reduced to a single curved line, a simple mouth line. \
			Apply this the same way to every person in the photo, near or far.
			- Use bold, uniform-width black ink outlines only for the people, with the inside \
			of every figure filled pure white.
			- Absolutely no color, no shading, no cross-hatching, no gray fill anywhere on any figure.
			- Everything that is not a person - trees, plants, buildings, furniture, chairs, \
			vehicles, sky, ground, walls, railings, or any other object or scenery - must be \
			completely deleted and replaced by a flat, solid, uniform chroma-key green (#00FF00) \
			fill. This green area is a plain blank fill with zero linework, outlines, texture, \
			gradient, or shading in it - do not draw the background scene in green ink, just \
			remove it entirely down to flat green. The green must appear only in this blank \
			fill, never on or touching a person.
			- Keep each person's likeness, hairstyle, and clothing recognizable but drawn in \
			this simplified line style.
			Output only the illustration.""";

	// Output aspect ratio is fixed to 1:1 for now; a future "9:16" option can reuse this field.
	private static final String ASPECT_RATIO = "1:1";

	// The prompt asks Gemini for a flat chroma-key green background (never used elsewhere in
	// the black-and-white line art), so background removal is a simple per-pixel color-distance
	// key instead of flood-fill - it doesn't depend on the outline forming a closed loop, which
	// generated line art isn't guaranteed to do.
	private static final int KEY_R = 0;
	private static final int KEY_G = 255;
	private static final int KEY_B = 0;
	private static final double KEY_LOW_DISTANCE = 60;
	private static final double KEY_HIGH_DISTANCE = 160;

	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public GeminiCaricatureService(
			@Value("${app.gemini.base-url}") String baseUrl,
			@Value("${app.gemini.api-key}") String apiKey,
			@Value("${app.gemini.model}") String model) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public byte[] convert(MultipartFile photo) throws IOException {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IOException("GEMINI_API_KEY가 설정되지 않았습니다.");
		}

		String mimeType = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
		String base64Image = Base64.getEncoder().encodeToString(photo.getBytes());

		ObjectNode requestBody = objectMapper.createObjectNode();
		ArrayNode contents = requestBody.putArray("contents");
		ObjectNode content = contents.addObject();
		ArrayNode parts = content.putArray("parts");
		parts.addObject().put("text", STYLE_PROMPT);
		ObjectNode inlineData = parts.addObject().putObject("inline_data");
		inlineData.put("mime_type", mimeType);
		inlineData.put("data", base64Image);
		requestBody.putObject("generationConfig").putObject("imageConfig").put("aspectRatio", ASPECT_RATIO);

		// Serialize/parse JSON ourselves with classic Jackson (com.fasterxml) rather than
		// letting Spring's auto-selected message converter guess how to (de)serialize
		// JsonNode - Spring Boot 4's default converter uses a different Jackson 3
		// (tools.jackson) engine that doesn't recognize this JsonNode as a tree type.
		String responseJson = restClient.post()
				.uri("/v1beta/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body(objectMapper.writeValueAsString(requestBody))
				.retrieve()
				.body(String.class);

		JsonNode response = objectMapper.readTree(responseJson);
		String imageBase64 = extractImageBase64(response);
		if (imageBase64 == null) {
			throw new IOException("Gemini 응답에서 이미지를 찾을 수 없습니다: " + extractText(response));
		}

		byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
		if (image == null) {
			throw new IOException("Gemini가 반환한 이미지를 디코딩할 수 없습니다.");
		}
		BufferedImage transparent = keyOutGreenScreen(image);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(transparent, "png", out);
		return out.toByteArray();
	}

	private BufferedImage keyOutGreenScreen(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		int[] outPixels = new int[pixels.length];

		for (int i = 0; i < pixels.length; i++) {
			int rgb = pixels[i];
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;

			double distance = keyDistance(r, g, b);
			int alpha;
			if (distance <= KEY_LOW_DISTANCE) {
				alpha = 0;
			} else if (distance >= KEY_HIGH_DISTANCE) {
				alpha = 255;
			} else {
				// Feather the cutout edge instead of a hard cutoff, so anti-aliased pixels
				// between the green background and the line art don't leave a green fringe.
				alpha = (int) Math.round(255.0 * (distance - KEY_LOW_DISTANCE) / (KEY_HIGH_DISTANCE - KEY_LOW_DISTANCE));
			}
			outPixels[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
		}

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		result.setRGB(0, 0, width, height, outPixels, 0, width);
		return result;
	}

	private double keyDistance(int r, int g, int b) {
		int dr = r - KEY_R;
		int dg = g - KEY_G;
		int db = b - KEY_B;
		return Math.sqrt((double) (dr * dr + dg * dg + db * db));
	}

	private String extractImageBase64(JsonNode response) {
		if (response == null) {
			return null;
		}
		Iterator<JsonNode> parts = response.path("candidates").path(0).path("content").path("parts").elements();
		while (parts.hasNext()) {
			JsonNode part = parts.next();
			JsonNode inlineData = part.has("inlineData") ? part.get("inlineData") : part.get("inline_data");
			if (inlineData != null && inlineData.has("data")) {
				return inlineData.get("data").asText();
			}
		}
		return null;
	}

	private String extractText(JsonNode response) {
		if (response == null) {
			return "(응답 없음)";
		}
		Iterator<JsonNode> parts = response.path("candidates").path(0).path("content").path("parts").elements();
		StringBuilder text = new StringBuilder();
		while (parts.hasNext()) {
			JsonNode part = parts.next();
			if (part.has("text")) {
				text.append(part.get("text").asText());
			}
		}
		return !text.isEmpty() ? text.toString() : response.toString();
	}
}
