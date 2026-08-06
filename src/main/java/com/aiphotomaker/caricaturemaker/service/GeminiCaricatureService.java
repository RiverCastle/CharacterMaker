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
			Turn the person in this photo into a black-and-white hand-drawn caricature \
			line-art illustration, like a caricature artist's ink sketch.
			- Slightly exaggerate the head size relative to the body, like a caricature.
			- Simplify facial features into minimal, iconic hand-drawn linework: small dot or \
			simple line eyes, the nose reduced to a single curved line, a simple mouth line.
			- Use bold, uniform-width black ink outlines only.
			- Absolutely no color, no shading, no cross-hatching, no gray fill.
			- Plain solid white background, no scenery or objects.
			- Keep the person's likeness, hairstyle, and clothing recognizable but drawn in \
			this simplified cartoon line style.
			Output only the illustration.""";

	// Output aspect ratio is fixed to 1:1 for now; a future "9:16" option can reuse this field.
	private static final String ASPECT_RATIO = "1:1";

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

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
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
