package com.ltz.hardware.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ltz.hardware.api.dto.response.HardwarePerformanceSummaryResponse;
import com.ltz.hardware.application.port.PerformanceSummaryReader;
import com.ltz.hardware.domain.exception.HardwarePerformanceUnavailableException;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HardwarePerformanceControllerTest extends PostgresIntegrationSupport {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final String ROUTE = "/api/v1/hardware/profiles/" + PROFILE + "/performance-summary";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PerformanceSummaryReader performance;

    @BeforeEach void profile() {
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,'PC','DESKTOP',now(),now())",
                PROFILE, OWNER);
    }

    private HardwarePerformanceSummaryResponse empty() {
        return new HardwarePerformanceSummaryResponse(PROFILE, 0, 0, null, List.of());
    }

    @Test void ownerReadsOnlyItsBoundProfileWithPrivateNoStore() throws Exception {
        when(performance.read(OWNER, PROFILE)).thenReturn(empty());
        mvc.perform(get(ROUTE).with(HardwareProfileControllerTest.actor(OWNER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.hardwareProfileId").value(PROFILE.toString()))
                .andExpect(jsonPath("$.metrics").isEmpty()).andExpect(jsonPath("$.sampleCount").value(0));
        verify(performance).read(OWNER, PROFILE);
    }

    @Test void foreignMissingAnonymousAndUntrustedPrincipalsNeverCallPerformance() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE).with(user("untrusted"))).andExpect(status().isForbidden());
        mvc.perform(get(ROUTE).with(HardwareProfileControllerTest.actor(UUID.randomUUID()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hardware/profiles/" + UUID.randomUUID() + "/performance-summary")
                .with(HardwareProfileControllerTest.actor(OWNER))).andExpect(status().isNotFound());
        verifyNoInteractions(performance);
    }

    @Test void upstreamFailureIsRetryableProblemInsteadOfFabricatedEmptyMetrics() throws Exception {
        when(performance.read(OWNER, PROFILE)).thenThrow(new HardwarePerformanceUnavailableException());
        mvc.perform(get(ROUTE).with(HardwareProfileControllerTest.actor(OWNER)))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("PERFORMANCE_SERVICE_UNAVAILABLE"));
    }

    @Test void profileDeletedWhileReadingCannotLeakAStaleSummary() throws Exception {
        when(performance.read(OWNER, PROFILE)).thenAnswer(_ -> {
            jdbc.update("DELETE FROM hardware_profiles WHERE id=?", PROFILE);
            return empty();
        });
        mvc.perform(get(ROUTE).with(HardwareProfileControllerTest.actor(OWNER))).andExpect(status().isNotFound());
    }
}
