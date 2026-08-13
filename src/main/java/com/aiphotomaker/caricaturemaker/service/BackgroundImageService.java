package com.aiphotomaker.caricaturemaker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persists the set of background images uploaded through the no-login admin
 * page. Each upload is stored under its own generated id so many background
 * images can be registered and later chosen from (e.g. by end users on the
 * main page).
 */
@Service
public class BackgroundImageService {

	private static final String EXTENSION = ".png";

	private final Path backgroundDir;

	public BackgroundImageService(@Value("${app.storage.background-dir}") String backgroundDir) throws IOException {
		this.backgroundDir = Paths.get(backgroundDir);
		Files.createDirectories(this.backgroundDir);
	}

	public String save(MultipartFile image) throws IOException {
		BufferedImage decoded = ImageIO.read(image.getInputStream());
		if (decoded == null) {
			throw new IOException("업로드된 파일을 이미지로 읽을 수 없습니다.");
		}
		String id = UUID.randomUUID().toString();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(decoded, "png", out);
		Files.write(fileFor(id), out.toByteArray());
		return id;
	}

	public List<String> listIds() throws IOException {
		try (Stream<Path> files = Files.list(backgroundDir)) {
			return files
					.map(path -> path.getFileName().toString())
					.filter(name -> name.endsWith(EXTENSION))
					.map(name -> name.substring(0, name.length() - EXTENSION.length()))
					.sorted()
					.toList();
		}
	}

	public byte[] load(String id) throws IOException {
		Path target = fileFor(id);
		if (!Files.exists(target)) {
			throw new FileNotFoundException("배경 이미지를 찾을 수 없습니다: " + id);
		}
		return Files.readAllBytes(target);
	}

	public void delete(String id) throws IOException {
		if (!Files.deleteIfExists(fileFor(id))) {
			throw new FileNotFoundException("배경 이미지를 찾을 수 없습니다: " + id);
		}
	}

	private Path fileFor(String id) {
		// id is validated to be a UUID by callers, so it cannot contain
		// path separators or ".." segments.
		return backgroundDir.resolve(UUID.fromString(id) + EXTENSION);
	}
}
