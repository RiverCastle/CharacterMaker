package com.aiphotomaker.caricaturemaker.controller;

import com.aiphotomaker.caricaturemaker.dto.FontResponse;
import com.aiphotomaker.caricaturemaker.service.FontService;
import com.aiphotomaker.caricaturemaker.service.FontService.FontMeta;
import com.aiphotomaker.caricaturemaker.service.FontService.LoadedFont;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * No-login admin endpoint for managing the font files end users can pick
 * for the promo text overlay on the main page. Reachable only by knowing
 * the URL (/admin/index.html); there is no authentication in front of it.
 */
@RestController
@RequestMapping("/api/admin/font")
public class AdminFontController {

	private final FontService fontService;

	public AdminFontController(FontService fontService) {
		this.fontService = fontService;
	}

	@PostMapping
	public ResponseEntity<?> upload(@RequestParam("fontFile") MultipartFile fontFile) {
		if (fontFile.isEmpty()) {
			return ResponseEntity.badRequest().body("업로드된 폰트 파일이 없습니다.");
		}
		try {
			FontMeta meta = fontService.save(fontFile);
			return ResponseEntity.ok(toResponse(meta));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("폰트 저장에 실패했습니다: " + e.getMessage());
		}
	}

	@GetMapping
	public ResponseEntity<?> list() {
		try {
			List<FontResponse> fonts = fontService.list().stream().map(this::toResponse).toList();
			return ResponseEntity.ok(fonts);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("폰트 목록을 불러오지 못했습니다: " + e.getMessage());
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> get(@PathVariable String id) {
		try {
			LoadedFont font = fontService.load(requireValidId(id));
			return ResponseEntity.ok().contentType(mediaTypeFor(font.extension())).body(font.data());
		} catch (IllegalArgumentException | FileNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IOException e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		try {
			fontService.delete(requireValidId(id));
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException | FileNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("폰트 삭제에 실패했습니다: " + e.getMessage());
		}
	}

	private String requireValidId(String id) {
		return UUID.fromString(id).toString();
	}

	private FontResponse toResponse(FontMeta meta) {
		return new FontResponse(meta.id(), meta.name(), formatFor(meta.extension()), "/api/admin/font/" + meta.id());
	}

	private static MediaType mediaTypeFor(String extension) {
		return switch (extension) {
			case ".woff2" -> MediaType.parseMediaType("font/woff2");
			case ".woff" -> MediaType.parseMediaType("font/woff");
			case ".otf" -> MediaType.parseMediaType("font/otf");
			default -> MediaType.parseMediaType("font/ttf");
		};
	}

	private static String formatFor(String extension) {
		return switch (extension) {
			case ".woff2" -> "woff2";
			case ".woff" -> "woff";
			case ".otf" -> "opentype";
			default -> "truetype";
		};
	}
}
