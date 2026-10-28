package com.ltz.community.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostImageContentTest {

	@Test
	void equalityAndHashCodeUseArrayContent() {
		PostImageContent first = new PostImageContent(new byte[] { 1, 2, 3 }, "image/png");
		PostImageContent same = new PostImageContent(new byte[] { 1, 2, 3 }, "image/png");

		assertThat(first)
				.isEqualTo(same)
				.hasSameHashCodeAs(same)
				.isNotEqualTo(new PostImageContent(new byte[] { 1, 2, 4 }, "image/png"))
				.isNotEqualTo(new PostImageContent(new byte[] { 1, 2, 3 }, "image/jpeg"))
				.isNotEqualTo("not a content object");
	}

	@Test
	void toStringDescribesSizeWithoutExposingBytes() {
		PostImageContent content = new PostImageContent(new byte[] { 9, 9, 9, 9 }, "image/png");

		assertThat(content).hasToString("PostImageContent[mimeType=image/png, size=4]");
	}
}
