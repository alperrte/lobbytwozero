package com.ltz.user.api.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.response.ProfileResponse;
import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.application.service.MediaService;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;

import lombok.RequiredArgsConstructor;

/**
 * Profil görselleri (USER-03 avatar, USER-04 kapak). Yükleme <b>ham gövdeyle</b> yapılır ({@code PUT}, gövde görselin
 * kendisi, {@code Content-Type} {@code image/jpeg|png|gif}): multipart ayrıştırıcısı ve geçici dosya yükü yoktur, bayt
 * sınırı akış sırasında uygulanır (bkz. {@code RequestBodySizeLimitFilter}). Kimlik yalnız token'dan gelir. Görseller
 * herkese açıktır ve adresleri tahmin edilemez 128 bitlik rastgele anahtardır.
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final MediaService media;
    private final MediaStoragePort storage;

    @PutMapping(value = UserApiPaths.ME_AVATAR, consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE})
    public ResponseEntity<ProfileResponse> putAvatar(@AuthenticationPrincipal Jwt token, @RequestBody byte[] body,
                                                     @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        return upload(token, MediaKind.AVATAR, body, contentType);
    }

    @PutMapping(value = UserApiPaths.ME_COVER, consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE})
    public ResponseEntity<ProfileResponse> putCover(@AuthenticationPrincipal Jwt token, @RequestBody byte[] body,
                                                    @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        return upload(token, MediaKind.COVER, body, contentType);
    }

    @DeleteMapping(UserApiPaths.ME_AVATAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@AuthenticationPrincipal Jwt token) {
        media.remove(userId(token), MediaKind.AVATAR);
    }

    @DeleteMapping(UserApiPaths.ME_COVER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCover(@AuthenticationPrincipal Jwt token) {
        media.remove(userId(token), MediaKind.COVER);
    }

    /**
     * Herkese açık görsel. Veritabanına gitmez (dosya adı anahtarı taşır), bu yüzden okuma yükü yalnız disk/önbellektir.
     * Yanıt değişmezdir ({@code immutable}); {@code nosniff} ve {@code sandbox} CSP ile içerik tarayıcıda çalıştırılamaz.
     */
    @GetMapping(UserApiPaths.MEDIA + "/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) {
        Resource file = storage.open(fileName).orElseThrow(() -> new UserException(UserErrorCode.MEDIA_NOT_FOUND));
        try {
            return ResponseEntity.ok()
                    .contentType(mediaTypeOf(fileName))
                    .contentLength(file.contentLength())
                    .cacheControl(IMMUTABLE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .body(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ResponseEntity<ProfileResponse> upload(Jwt token, MediaKind kind, byte[] body, String contentType) {
        ProfileResponse response = ProfileResponse.from(media.replace(userId(token), kind, body, contentType));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private static MediaType mediaTypeOf(String fileName) {
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        return fileName.endsWith(".gif") ? MediaType.IMAGE_GIF : MediaType.IMAGE_JPEG;
    }

    /** Token doğrulayıcısı {@code sub}'ın UUID olduğunu zaten garanti eder. */
    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
