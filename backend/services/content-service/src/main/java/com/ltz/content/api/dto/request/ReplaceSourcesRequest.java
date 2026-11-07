package com.ltz.content.api.dto.request;

import java.util.List;

import com.ltz.content.domain.entity.ContentSource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** İçeriğin kaynaklarını tamamen değiştirir; boş liste tüm kaynakları kaldırır. En çok 3 kaynak. */
public record ReplaceSourcesRequest(
		@NotNull
		@Size(max = ContentSource.MAX_PER_CONTENT)
		List<@NotNull @Valid SourceRequest> sources) {
}
