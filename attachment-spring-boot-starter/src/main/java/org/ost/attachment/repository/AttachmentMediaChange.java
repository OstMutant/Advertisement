package org.ost.attachment.repository;

import java.util.List;

public record AttachmentMediaChange(List<String> before, List<String> after) {}
