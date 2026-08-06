package com.aiphotomaker.caricaturemaker.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Converts an uploaded portrait photo into a caricature image.
 * Swap the implementation (e.g. to call an external AI image API) without
 * touching the controller.
 */
public interface CaricatureService {

	byte[] convert(MultipartFile photo) throws IOException;
}
