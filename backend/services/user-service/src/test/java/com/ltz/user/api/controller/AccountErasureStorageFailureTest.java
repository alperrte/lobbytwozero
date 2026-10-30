package com.ltz.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ltz.user.IntegrationTestSupport;
import com.ltz.user.application.port.MediaStorageException;
import com.ltz.user.application.port.MediaStoragePort;

/** Depo erişilemezken hesap silme: {@code 503} döner, veri satırı silinmez (Auth yeniden dener); {@code 204} yalnız tam silmede. */
class AccountErasureStorageFailureTest extends IntegrationTestSupport {

    @MockitoBean
    private MediaStoragePort storage;

    @Test
    void depoHatasindaSilme503VeSatirlarKorunur() throws Exception {
        UUID account = newUser();
        insertProfile(account);
        jdbc.update("""
                insert into user_media (id, user_id, kind, storage_key, content_type, byte_size, width, height, status,
                                        created_at)
                values (?, ?, 'AVATAR', ?, 'image/png', 10, 5, 5, 'CURRENT', now())""",
                UUID.randomUUID(), account, UUID.randomUUID().toString().replace("-", ""));
        doThrow(new MediaStorageException("disk", new java.io.IOException("unavailable"))).when(storage).delete(anyString());

        mockMvc.perform(delete(InternalApiPaths.ACCOUNT_ERASURE, account)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken(account, UnaryOperator.identity())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ERASURE_PENDING"));

        assertThat(profileCount(account)).as("dosyalar silinemedi: veri satırı da silinmemeli").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_media where user_id = ?", Integer.class, account))
                .isEqualTo(1);
    }
}
