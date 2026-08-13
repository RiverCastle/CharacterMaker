package com.aiphotomaker.caricaturemaker.controller;

import com.aiphotomaker.caricaturemaker.dto.BackgroundImageResponse;
import com.aiphotomaker.caricaturemaker.service.BackgroundImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * No-login admin endpoint for managing the background images used by the
 * app. Reachable only by knowing the URL (/admin/index.html); there is no
 * authentication in front of it.
 */
@RestController
@RequestMapping("/api/admin/background")
public class AdminBackgroundController {

	private final BackgroundImageService backgroundImageService;

	public AdminBackgroundController(BackgroundImageService backgroundImageService) {
		this.backgroundImageService = backgroundImageService;
	}

	@PostMapping
	public ResponseEntity<?> upload(@RequestParam("backgroundImage") MultipartFile backgroundImage) {
		if (backgroundImage.isEmpty()) {
			return ResponseEntity.badRequest().body("업로드된 배경 이미지가 없습니다.");
		}
		try {
			String id = backgroundImageService.save(backgroundImage);
			return ResponseEntity.ok(new BackgroundImageResponse(id, imageUrl(id)));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("배경 이미지 저장에 실패했습니다: " + e.getMessage());
		}
	}

	@GetMapping
	public ResponseEntity<?> list() {
		try {
			List<BackgroundImageResponse> images = backgroundImageService.listIds().stream()
					.map(id -> new BackgroundImageResponse(id, imageUrl(id)))
					.toList();
			return ResponseEntity.ok(images);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("배경 이미지 목록을 불러오지 못했습니다: " + e.getMessage());
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> get(@PathVariable String id) {
		try {
			byte[] png = backgroundImageService.load(requireValidId(id));
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
		} catch (IllegalArgumentException | FileNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IOException e) {
			return ResponseEntity.internalServerError().build();
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		try {
			backgroundImageService.delete(requireValidId(id));
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException | FileNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("배경 이미지 삭제에 실패했습니다: " + e.getMessage());
		}
	}

	private String requireValidId(String id) {
		return UUID.fromString(id).toString();
	}

	private String imageUrl(String id) {
		return "/api/admin/background/" + id;
	}
}
