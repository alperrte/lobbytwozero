package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.response.AttachmentResponse;
import com.ltz.messaging.application.service.AttachmentContent;
import com.ltz.messaging.application.service.AttachmentService;

import lombok.RequiredArgsConstructor;

/**
 * Sohbete görsel (MSG-17) ve sesli mesaj (MSG-21) yükleme ve indirme. Yükleme gövdesi dosyanın kendisidir (çok parçalı
 * form değil): {@code Content-Type} PNG, JPEG, GIF ya da Opus sesi için {@code audio/webm} / {@code audio/ogg} olmalıdır,
 * gövde en çok 1 MB'tır. Tür, boyut ve ses süresi sunucuda içerikten yeniden doğrulanır; seste isteğe bağlı
 * {@code waveform} sorgu parametresi 64 değerlik dalga formudur.
 * <p>
 * İndirme yanıtı tarayıcıda çalıştırılamaz ({@code nosniff}, {@code sandbox} CSP) ve önbelleğe alınmaz. Bayt aralığı
 * ({@code Range}) desteklenir ({@code 206} / {@code 416}): tarayıcı oynatıcısı (Safari) sesi parça parça ister. Yetki
 * her istekte, aralık hesaplanmadan önce denetlenir.
 */
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";
    private static final String AUDIO_WEBM = "audio/webm";
    private static final String AUDIO_OGG = "audio/ogg";

    private final AttachmentService attachments;

    @PostMapping(value = MessagingApiPaths.ATTACHMENTS,
            consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_GIF_VALUE, AUDIO_WEBM,
                    AUDIO_OGG})
    public ResponseEntity<AttachmentResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID conversationId, @RequestBody byte[] content,
                                                     @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
                                                     @RequestParam(required = false) String waveform) {
        AttachmentResponse response = AttachmentResponse.from(
                attachments.upload(AuthenticatedUser.id(jwt), conversationId, content, contentType, waveform));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(MessagingApiPaths.ATTACHMENT)
    public ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                           @PathVariable UUID attachmentId) {
        AttachmentContent content = attachments.download(AuthenticatedUser.id(jwt), conversationId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .body(new ByteArrayResource(content.content()));
    }
}
