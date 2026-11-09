package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.TestImages;
import com.ltz.messaging.application.service.AttachmentService;
import com.ltz.messaging.domain.entity.MessageAttachment;

/**
 * MSG-17 görsel yükleme ve indirme: tür ve boyut sunucuda içerikten doğrulanır (1 MB), metadata atılır; görseli yalnız
 * mesajı görebilen aktif üye (gönderilmeden önce yalnız yükleyen) indirir; yükleme oranı, bekleyen yükleme sınırı ve
 * süresi dolan yüklemelerin temizliği.
 */
class AttachmentUploadTest extends IntegrationTestSupport {

    @Autowired
    private AttachmentService attachmentService;

    @Test
    void pngYuklenirGonderilinceAliciIndirebilir() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        String response = upload(alice, direct, TestImages.png(40, 30), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.width").value(40))
                .andExpect(jsonPath("$.height").value(30))
                .andExpect(jsonPath("$.frameCount").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID attachment = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat((String) JsonPath.read(response, "$.url"))
                .isEqualTo("/api/v1/messaging/conversations/%s/attachments/%s".formatted(direct, attachment));
        assertThat((Integer) JsonPath.read(response, "$.sizeBytes")).isPositive();

        // Gönderilmeden önce yalnız yükleyen görür.
        download(bob, direct, attachment).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        download(alice, direct, attachment).andExpect(status().isOk());

        sendImage(alice, direct, attachment, null).andExpect(status().isCreated());

        byte[] content = download(bob, direct, attachment)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(30);
    }

    @Test
    void jpegYonuUygulanirExifVeGizliVeriSaklanmaz() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        byte[] photo = TestImages.withExif(TestImages.jpeg(40, 20), 6, true);

        UUID attachment = idOf(upload(alice, direct, photo, MediaType.IMAGE_JPEG_VALUE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(20))
                .andExpect(jsonPath("$.height").value(40)));

        byte[] stored = storedContent(attachment);
        assertThat(TestImages.contains(stored, "Exif")).isFalse();
        assertThat(TestImages.contains(stored, TestImages.SECRET_MARKER)).isFalse();
    }

    @Test
    void animasyonluGifKarelerleSaklanirGizliBloklarAtilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        byte[] gif = TestImages.animatedGif(3, 20, 10, "<script>alert(1)</script>");

        UUID attachment = idOf(upload(alice, direct, gif, MediaType.IMAGE_GIF_VALUE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/gif"))
                .andExpect(jsonPath("$.frameCount").value(3)));

        byte[] stored = storedContent(attachment);
        assertThat(TestImages.contains(stored, "<script>")).isFalse();
        assertThat(TestImages.contains(stored, TestImages.SECRET_MARKER)).isFalse();
    }

    @Test
    void turIcerikleUyusmazsaYaDaDesteklenmiyorsaReddedilir() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        upload(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_GIF_VALUE)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        upload(alice, direct, svg, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        upload(alice, direct, svg, "image/svg+xml")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        upload(alice, direct, "<html></html>".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML_VALUE)
                .andExpect(status().isUnsupportedMediaType());
        upload(alice, direct, TestImages.png(5000, 2), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_DIMENSIONS_EXCEEDED"));
        upload(alice, direct, new byte[0], MediaType.IMAGE_PNG_VALUE).andExpect(status().is4xxClientError());

        assertThat(attachmentCount(direct)).isZero();
    }

    @Test
    void birMegabaytiAsanDosya413SinirIcindekiBuyukDosyaKabul() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        byte[] overLimit = new byte[MessageAttachment.MAX_SIZE_BYTES + 1];
        System.arraycopy(TestImages.png(2, 2), 0, overLimit, 0, 8);
        // JSON uçlarının 32 KB sınırından büyük ama 1 MB'tan küçük gerçek görsel.
        byte[] large = TestImages.noisyJpeg(300, 300, 0.95f);
        assertThat(large.length).isBetween(32 * 1024, MessageAttachment.MAX_SIZE_BYTES);

        upload(alice, direct, overLimit, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        upload(alice, direct, large, MediaType.IMAGE_JPEG_VALUE).andExpect(status().isCreated());
    }

    @Test
    void uyeOlmayanYukleyemezVeIndiremez() throws Exception {
        UUID alice = newUser();
        UUID outsider = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID attachment = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        sendImage(alice, direct, attachment, null).andExpect(status().isCreated());

        upload(outsider, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        download(outsider, direct, attachment).andExpect(status().isNotFound());
        mockMvc.perform(post(MessagingApiPaths.ATTACHMENTS, direct)
                        .contentType(MediaType.IMAGE_PNG_VALUE).content(TestImages.png(4, 4)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sonradanKatilanEskiGorseliAyrilanYeniGorseliGoremez() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID late = newUser();
        UUID group = createGroupAndGetId(owner, member);
        UUID early = uploadAndGetId(owner, group, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        sendImage(owner, group, early, null).andExpect(status().isCreated());
        addMembers(owner, group, late).andExpect(status().isOk());

        download(late, group, early).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        download(member, group, early).andExpect(status().isOk());

        leave(member, group).andExpect(status().isNoContent());
        download(member, group, early).andExpect(status().isNotFound());
    }

    @Test
    void baskaSohbetinGorseliOSohbetYoluylaBulunmaz() throws Exception {
        UUID alice = newUser();
        UUID first = openDirectAndGetId(alice, newUser());
        UUID second = openDirectAndGetId(alice, newUser());
        UUID attachment = uploadAndGetId(alice, first, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);

        download(alice, second, attachment).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        download(alice, first, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void yuklemeOraniVeBekleyenYuklemeSiniri() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        Instant now = Instant.now();
        for (int index = 0; index < 10; index++) {
            insertAttachment(direct, alice, now.minus(5, ChronoUnit.MINUTES));
        }
        upload(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PENDING_ATTACHMENTS"));

        UUID bob = newUser();
        UUID other = openDirectAndGetId(bob, newUser());
        UUID sentMessageHolder = uploadAndGetId(bob, other, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        sendImage(bob, other, sentMessageHolder, null).andExpect(status().isCreated());
        jdbc.update("update message_attachments set created_at = now() where uploader_id = ?", bob);
        for (int index = 0; index < 9; index++) {
            insertAttachment(other, bob, now);
        }
        upload(bob, other, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_UPLOADS"));
    }

    @Test
    void temizlikYalnizSuresiDolanGonderilmemisYuklemeleriSiler() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());
        UUID sent = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        sendImage(alice, direct, sent, null).andExpect(status().isCreated());
        UUID fresh = uploadAndGetId(alice, direct, TestImages.png(4, 4), MediaType.IMAGE_PNG_VALUE);
        UUID expired = insertAttachment(direct, alice, Instant.now().minus(2, ChronoUnit.HOURS));
        jdbc.update("update message_attachments set created_at = now() - interval '3 hours' where id = ?", sent);

        int deleted = attachmentService.deleteExpiredUploads();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(exists(expired)).isFalse();
        assertThat(exists(fresh)).isTrue();
        assertThat(exists(sent)).isTrue();
    }

    private UUID insertAttachment(UUID conversationId, UUID uploader, Instant createdAt) {
        UUID id = UUID.randomUUID();
        byte[] content = TestImages.png(2, 2);
        jdbc.update("insert into message_attachments (id, conversation_id, uploader_id, mime_type, size_bytes, width, "
                + "height, frame_count, content, created_at) values (?, ?, ?, 'image/png', ?, 2, 2, 1, ?, ?)",
                id, conversationId, uploader, content.length, content, Timestamp.from(createdAt));
        return id;
    }

    private byte[] storedContent(UUID attachmentId) {
        return jdbc.queryForObject("select content from message_attachments where id = ?", byte[].class,
                attachmentId);
    }

    private boolean exists(UUID attachmentId) {
        return jdbc.queryForObject("select count(*) from message_attachments where id = ?", Integer.class,
                attachmentId) == 1;
    }

    private int attachmentCount(UUID conversationId) {
        return jdbc.queryForObject("select count(*) from message_attachments where conversation_id = ?",
                Integer.class, conversationId);
    }
}
