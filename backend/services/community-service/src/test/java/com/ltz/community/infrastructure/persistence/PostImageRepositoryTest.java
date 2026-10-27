package com.ltz.community.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

import jakarta.persistence.EntityManager;

/** V92 migration'ını gerçek PostgreSQL üzerinde çalıştırır; her test geri alınır. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostImageRepositoryTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID newPost() {
		return postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "içerik", NOW)).getId();
	}

	private PostImage image(UUID postId, int sortOrder) {
		return PostImage.attach(postId, new byte[] { 1, 2, 3, (byte) sortOrder }, "image/png", 10, 20, sortOrder, NOW);
	}

	@Test
	void storesAndLoadsImageBytesWithMetadata() {
		UUID postId = newPost();
		PostImage saved = postImageRepository.saveAndFlush(image(postId, 2));
		entityManager.clear();

		PostImage loaded = postImageRepository.findByIdAndPostId(saved.getId(), postId).orElseThrow();

		assertThat(loaded.getContent()).containsExactly(1, 2, 3, 2);
		assertThat(loaded.getMimeType()).isEqualTo("image/png");
		assertThat(loaded.getSizeBytes()).isEqualTo(4);
		assertThat(loaded.getWidth()).isEqualTo(10);
		assertThat(loaded.getHeight()).isEqualTo(20);
		assertThat(loaded.getSortOrder()).isEqualTo(2);
		assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void imageIsNotFoundThroughAnotherPost() {
		UUID postId = newPost();
		PostImage saved = postImageRepository.saveAndFlush(image(postId, 0));

		assertThat(postImageRepository.findByIdAndPostId(saved.getId(), newPost())).isEmpty();
	}

	@Test
	void listsUsedSortOrdersOfPostOnly() {
		UUID postId = newPost();
		postImageRepository.saveAndFlush(image(postId, 0));
		postImageRepository.saveAndFlush(image(postId, 2));
		postImageRepository.saveAndFlush(image(newPost(), 1));

		assertThat(postImageRepository.findSortOrdersByPostId(postId)).containsExactlyInAnyOrder(0, 2);
	}

	@Test
	void deleteByPostIdRemovesOnlyThatPostsImages() {
		UUID postId = newPost();
		UUID otherPostId = newPost();
		postImageRepository.saveAndFlush(image(postId, 0));
		postImageRepository.saveAndFlush(image(postId, 1));
		postImageRepository.saveAndFlush(image(otherPostId, 0));

		int deleted = postImageRepository.deleteByPostId(postId);
		entityManager.clear();

		assertThat(deleted).isEqualTo(2);
		assertThat(postImageRepository.findSortOrdersByPostId(postId)).isEmpty();
		assertThat(postImageRepository.findSortOrdersByPostId(otherPostId)).containsExactly(0);
	}

	@Test
	void sameSortOrderCannotBeUsedTwiceForOnePost() {
		UUID postId = newPost();
		postImageRepository.saveAndFlush(image(postId, 1));

		PostImage candidate = image(postId, 1);

		assertThatThrownBy(() -> postImageRepository.saveAndFlush(candidate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseLimitsPostToFourImages() {
		UUID postId = newPost();

		PostImage candidate = image(postId, 4);

		assertThatThrownBy(() -> postImageRepository.saveAndFlush(candidate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void imageMustReferenceExistingPost() {
		PostImage candidate = image(UUID.randomUUID(), 0);
		assertThatThrownBy(() -> postImageRepository.saveAndFlush(candidate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsUnsupportedMimeType() {
		UUID postId = newPost();
		PostImage gif = PostImage.attach(postId, new byte[] { 1 }, "image/gif", 1, 1, 0, NOW);

		assertThatThrownBy(() -> postImageRepository.saveAndFlush(gif))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsEmptyAndOversizedContent() {
		UUID postId = newPost();
		PostImage empty = PostImage.attach(postId, new byte[0], "image/png", 1, 1, 0, NOW);

		assertThatThrownBy(() -> postImageRepository.saveAndFlush(empty))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsOversizedContent() {
		UUID postId = newPost();
		PostImage big = PostImage.attach(postId, new byte[PostImage.MAX_SIZE_BYTES + 1], "image/png", 1, 1, 0, NOW);

		assertThatThrownBy(() -> postImageRepository.saveAndFlush(big))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void postWithImagesCannotBePhysicallyDeleted() {
		UUID postId = newPost();
		postImageRepository.saveAndFlush(image(postId, 0));

		assertThatThrownBy(() -> jdbcTemplate.update("delete from posts where id = ?", postId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
