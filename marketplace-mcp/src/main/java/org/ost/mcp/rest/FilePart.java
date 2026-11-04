package org.ost.mcp.rest;

import lombok.NonNull;
import org.springframework.core.io.Resource;

/** One file of a multipart upload; its content is a Spring Resource. */
public record FilePart(@NonNull String fileName, @NonNull String contentType, @NonNull Resource content) {}
