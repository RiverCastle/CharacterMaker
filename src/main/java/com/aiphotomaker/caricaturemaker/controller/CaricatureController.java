package com.aiphotomaker.caricaturemaker.controller;

import com.aiphotomaker.caricaturemaker.dto.CaricatureResponse;
import com.aiphotomaker.caricaturemaker.service.CaricatureService;
import com.aiphotomaker.caricaturemaker.service.ImageStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/caricature")
public class CaricatureController {

	private final CaricatureService caricatureService;
	private final ImageStorageService imageStorageService;

	public CaricatureController(CaricatureService caricatureService, ImageStorageService imageStorageService) {
		this.caricatureService = caricatureService;
		this.imageStorageService = imageStorageService;
	}

	@PostMapping("/convert")
	public ResponseEntity<?> convert(@RequestParam("photo") MultipartFile photo) {
		if (photo.isEmpty()) {
			return ResponseEntity.badRequest().body("업로드된 사진이 없습니다.");
		}

		String id = UUID.randomUUID().toString();
		try {
			imageStorageService.saveUpload(id, photo);
			byte[] resultPng = caricatureService.convert(photo);
			imageStorageService.saveResult(id, resultPng);
			return ResponseEntity.ok(new CaricatureResponse(id, "/api/caricature/result/" + id));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body("이미지 변환에 실패했습니다: " + e.getMessage());
		}
	}

	@GetMapping("/result/{id}")
	public ResponseEntity<byte[]> getResult(@PathVariable String id) {
		try {
			byte[] png = imageStorageService.loadResult(id);
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
		} catch (FileNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IOException e) {
			return ResponseEntity.internalServerError().build();
		}
	}
}
