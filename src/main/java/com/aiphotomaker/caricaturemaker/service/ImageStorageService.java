package com.aiphotomaker.caricaturemaker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Persists uploaded photos and converted caricature images to the local
 * filesystem, under the directories configured via app.storage.*.
 */
@Service
public class ImageStorageService {

	private final Path uploadDir;
	private final Path resultDir;

	public ImageStorageService(
			@Value("${app.storage.upload-dir}") String uploadDir,
			@Value("${app.storage.result-dir}") String resultDir) throws IOException {
		this.uploadDir = Paths.get(uploadDir);
		this.resultDir = Paths.get(resultDir);
		Files.createDirectories(this.uploadDir);
		Files.createDirectories(this.resultDir);
	}

	public void saveUpload(String id, MultipartFile photo) throws IOException {
		Path target = uploadDir.resolve(id + extensionOf(photo.getOriginalFilename()));
		photo.transferTo(target);
	}

	public String saveResult(String id, byte[] pngBytes) throws IOException {
		Path target = resultDir.resolve(id + ".png");
		Files.write(target, pngBytes);
		return id;
	}

	public byte[] loadResult(String id) throws IOException {
		Path target = resultDir.resolve(id + ".png");
		if (!Files.exists(target)) {
			throw new java.io.FileNotFoundException("결과 이미지를 찾을 수 없습니다: " + id);
		}
		return Files.readAllBytes(target);
	}

	private String extensionOf(String originalFilename) {
		if (originalFilename == null || !originalFilename.contains(".")) {
			return "";
		}
		return originalFilename.substring(originalFilename.lastIndexOf('.'));
	}
}
