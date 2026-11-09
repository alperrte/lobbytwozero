package com.ltz.messaging.api.controller;

import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.response.AttachmentResponse;
import com.ltz.messaging.application.service.AttachmentContent;
import com.ltz.messaging.application.service.AttachmentService;

import lombok.RequiredArgsConstructor;

/**
 * Sohbete görsel yükleme ve indirme (MSG-17). Yükleme gövdesi dosyanın kendisidir (çok parçalı form değil):
 * {@code Content-Type} PNG, JPEG ya da GIF olmalıdır, gövde en çok 1 MB'tır. Tür ve boyut sunucuda içerikten yeniden
 * doğrulanır. İndirme yanıtı tarayıcıda çalıştırılamaz ({@code nosniff}, {@code sandbox} CSP) ve önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";

    private final AttachmentService attachments;

    @PostMapping(value = MessagingApiPaths.ATTACHMENTS,
            consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_GIF_VALUE})
    public ResponseEntity<AttachmentResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID conversationId, @RequestBody byte[] content,
                                                     @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        AttachmentResponse response = AttachmentResponse.from(
                attachments.upload(AuthenticatedUser.id(jwt), conversationId, content, contentType));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(MessagingApiPaths.ATTACHMENT)
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                           @PathVariable UUID attachmentId) {
        AttachmentContent content = attachments.download(AuthenticatedUser.id(jwt), conversationId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .body(content.content());
    }
}
