package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse;
import com.ltz.game.api.exception.GameExceptionHandler;
import com.ltz.game.application.service.RecommendationMetadataBatchService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.ltz.game.api.controller.RecommendationMetadataBatchController.PATH;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationMetadataBatchControllerTest {
    private static final String IDS = "{\"gameIds\":[%s]}";
    private final RecommendationMetadataBatchService service = mock(RecommendationMetadataBatchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationMetadataBatchController(service))
                .setControllerAdvice(new GameExceptionHandler()).build();
    }

    @Test void acceptsExactlyOneHundredCanonicalIds() throws Exception {
        when(service.read(anyList())).thenAnswer(call -> new RecommendationMetadataBatchResponse(List.of(), call.getArgument(0)));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(ids(100)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.unavailable.length()").value(100));
    }

    @Test void rejectsInvalidSchemaAndNonCanonicalIdsBeforeReading() throws Exception {
        for (String invalid : List.of("", "{", "null", "[]", "{}", "{\"gameIds\":null}", ids(0), ids(101),
                IDS.formatted("null"), IDS.formatted("1"), IDS.formatted("\"1-1-1-1-1\""),
                IDS.formatted("\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\""),
                "{\"gameIds\":[],\"gameIds\":[]}", "{\"gameIds\":[],\"extra\":1}", ids(1) + " {}")) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_GAME_IDS"));
        }
        verifyNoInteractions(service);
    }

    @Test void rejectsDuplicatesIgnoringHexCase() throws Exception {
        String id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String repeated = quoted(id) + "," + quoted(id.toUpperCase(Locale.ROOT));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(IDS.formatted(repeated)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_GAME_ID"));
        verifyNoInteractions(service);
    }

    @Test void boundsBodyBytesBeforeParsing() throws Exception {
        String body = ids(1);
        String exact = body + " ".repeat(8192 - body.length());
        when(service.read(anyList())).thenReturn(new RecommendationMetadataBatchResponse(List.of(), List.of()));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(exact))
                .andExpect(status().isOk());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(exact + " ").header("Transfer-Encoding", "chunked"))
                .andExpect(status().is(413)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test void exposesDatabaseTimeoutAsUnavailableWithoutReturningPartialSuccess() throws Exception {
        when(service.read(anyList())).thenThrow(new QueryTimeoutException("database details"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(ids(1)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("GAME_METADATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.items").doesNotExist()).andExpect(jsonPath("$.unavailable").doesNotExist());
    }

    private static String ids(int size) {
        return IDS.formatted(IntStream.range(0, size).mapToObj(index -> quoted(new UUID(0, index).toString()))
                .collect(Collectors.joining(",")));
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
