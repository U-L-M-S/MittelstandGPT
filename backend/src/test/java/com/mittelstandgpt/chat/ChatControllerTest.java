package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

/**
 * Contract tests for the chat HTTP API (standalone MockMvc, mocked service): the
 * JSON answer/sources shape, bad-request handling, and the SSE stream contract
 * (token → sources → done, with sources omitted on the no-answer sentinel).
 */
class ChatControllerTest {

    private final AgenticRagService rag = mock(AgenticRagService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(rag, new ObjectMapper())).build();
    }

    @Test
    void chat_returnsAnswerAndSources() throws Exception {
        when(rag.ask("Wie viele Urlaubstage?"))
                .thenReturn(new ChatResponse("30 Tage.", List.of(new Source("urlaub.pdf", 1))));

        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"Wie viele Urlaubstage?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("30 Tage."))
                .andExpect(jsonPath("$.sources[0].source").value("urlaub.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value(1));
    }

    @Test
    void chat_blankQuestion_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void stream_emitsTokensThenSourcesThenDone() throws Exception {
        when(rag.stream(anyString()))
                .thenReturn(
                        new AgenticRagService.StreamResult(
                                List.of(new Source("urlaub.pdf", 1)), Flux.just("30 ", "Tage.")));

        MvcResult started =
                mockMvc.perform(
                                post("/api/chat/stream")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"question\":\"Frage?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        String body =
                mockMvc.perform(asyncDispatch(started))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body)
                .contains("event:token")
                .contains("30 ")
                .contains("Tage.")
                .contains("event:sources")
                .contains("urlaub.pdf")
                .contains("event:done");
    }

    @Test
    void stream_sentinelAnswerOmitsSources() throws Exception {
        when(rag.stream(anyString()))
                .thenReturn(
                        new AgenticRagService.StreamResult(
                                List.of(new Source("urlaub.pdf", 1)),
                                Flux.just(GroundedAnswerService.NO_ANSWER)));

        MvcResult started =
                mockMvc.perform(
                                post("/api/chat/stream")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"question\":\"Frage?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        String body =
                mockMvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:sources").contains("[]");
        assertThat(body).doesNotContain("urlaub.pdf");
    }
}
