package com.ltz.community.api.dto.request;

import java.util.UUID;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.GameContentType;
import com.ltz.community.domain.enums.PostCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code gameId} isteğe bağlıdır; verilirse LTZ oyun kataloğunda doğrulanır (COM-14). {@code category}
 * isteğe bağlıdır; verilmezse {@code GENERAL} atanır (COM-12). {@code forumId} verilirse gönderi o forumda konu olarak
 * açılır (COM-19); forum bir oyuna bağlıysa konu da o oyuna bağlanır. {@code contentType} verilirse gönderi oyuna bağlı
 * türlü içerik olur (GAME-11): {@code title} ve {@code gameId} zorunludur, {@code forumId} verilemez; {@code title}
 * türsüz gönderide verilemez. Tür sonradan değiştirilemez.
 */
public record CreatePostRequest(
		@NotBlank
		@Size(max = Post.BODY_MAX_LENGTH)
		@Pattern(regexp = Post.BODY_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String body,
		UUID gameId,
		PostCategory category,
		UUID forumId,
		GameContentType contentType,
		@Size(max = Post.TITLE_MAX_LENGTH)
		@Pattern(regexp = Post.TITLE_ALLOWED_CHARACTERS, message = "must not contain control characters")
		String title) {
}
