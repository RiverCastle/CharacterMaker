package com.aiphotomaker.caricaturemaker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persists the set of font files uploaded through the no-login admin page,
 * so end users can pick a registered font for the promo text overlay on the
 * main page. Each font is stored under a generated id; the original file
 * name and extension are kept in the stored file name itself (id__name.ext)
 * so the display name and font format can be recovered without a database.
 */
@Service
public class FontService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".ttf", ".otf", ".woff", ".woff2");
	private static final String SEPARATOR = "__";

	private final Path fontDir;

	public FontService(@Value("${app.storage.font-dir}") String fontDir) throws IOException {
		this.fontDir = Paths.get(fontDir);
		Files.createDirectories(this.fontDir);
	}

	public FontMeta save(MultipartFile file) throws IOException {
		String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String extension = extensionOf(originalName);
		if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("지원하지 않는 폰트 파일 형식입니다. (ttf, otf, woff, woff2만 가능)");
		}
		String name = sanitizeName(originalName.substring(0, originalName.length() - extension.length()));
		String id = UUID.randomUUID().toString();
		Files.write(fontDir.resolve(id + SEPARATOR + name + extension), file.getBytes());
		return new FontMeta(id, name, extension);
	}

	public List<FontMeta> list() throws IOException {
		try (Stream<Path> files = Files.list(fontDir)) {
			return files
					.map(path -> parse(path.getFileName().toString()))
					.filter(Objects::nonNull)
					.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
					.toList();
		}
	}

	public LoadedFont load(String id) throws IOException {
		Path target = fileFor(id);
		FontMeta meta = parse(target.getFileName().toString());
		return new LoadedFont(Files.readAllBytes(target), meta.extension());
	}

	public void delete(String id) throws IOException {
		if (!Files.deleteIfExists(fileFor(id))) {
			throw new FileNotFoundException("폰트를 찾을 수 없습니다: " + id);
		}
	}

	private Path fileFor(String id) throws IOException {
		String validId = UUID.fromString(id).toString();
		try (Stream<Path> files = Files.list(fontDir)) {
			return files
					.filter(path -> path.getFileName().toString().startsWith(validId + SEPARATOR))
					.findFirst()
					.orElseThrow(() -> new FileNotFoundException("폰트를 찾을 수 없습니다: " + id));
		}
	}

	private static FontMeta parse(String filename) {
		int sepIndex = filename.indexOf(SEPARATOR);
		if (sepIndex < 0) {
			return null;
		}
		String id = filename.substring(0, sepIndex);
		String rest = filename.substring(sepIndex + SEPARATOR.length());
		String extension = extensionOf(rest);
		if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
			return null;
		}
		String name = rest.substring(0, rest.length() - extension.length());
		return new FontMeta(id, name, extension);
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? null : filename.substring(dot).toLowerCase();
	}

	private static String sanitizeName(String rawName) {
		String cleaned = rawName.trim().replaceAll("[^a-zA-Z0-9가-힣 _-]", "").trim();
		if (cleaned.isEmpty()) {
			cleaned = "font";
		}
		return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
	}

	public record FontMeta(String id, String name, String extension) {
	}

	public record LoadedFont(byte[] data, String extension) {
	}
}
