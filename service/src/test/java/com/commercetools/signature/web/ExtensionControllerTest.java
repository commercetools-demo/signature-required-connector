package com.commercetools.signature.web;

import static com.commercetools.signature.CartPayloads.NO_CUSTOM;
import static com.commercetools.signature.CartPayloads.envelope;
import static com.commercetools.signature.CartPayloads.foreignCustom;
import static com.commercetools.signature.CartPayloads.narcoticItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.signature.CartPayloads;
import com.commercetools.signature.service.TypeResolver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Router-level tests: the auth rejection matrix (security.md, testing.md) plus the fail-closed
 * behavior at the HTTP boundary. The commercetools client and Type lookup are mocked, so no real
 * project is needed.
 */
@SpringBootTest(properties = {
        "EXTENSION_AUTH_SECRET=test-secret",
        "CTP_PROJECT_KEY=test"
})
class ExtensionControllerTest {

    private static final String VALID_AUTH = "Bearer test-secret";

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private ProjectApiRoot projectApiRoot; // replaces the real client so no CTP creds are needed

    @MockBean
    private TypeResolver typeResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(typeResolver.resolveTypeId()).thenReturn(Optional.of(CartPayloads.OUR_TYPE_ID));
    }

    // ---- auth matrix ----

    @Test
    void rejectsMissingAuthorization() throws Exception {
        mockMvc.perform(post("/service").contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(narcoticItem(), NO_CUSTOM)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        mockMvc.perform(post("/service").header("Authorization", "Bearer nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(narcoticItem(), NO_CUSTOM)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/service/status")).andExpect(status().isOk());
    }

    // ---- behavior with valid auth ----

    @Test
    void narcoticCart_returnsSetCustomTypeAction() throws Exception {
        mockMvc.perform(post("/service").header("Authorization", VALID_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(narcoticItem(), NO_CUSTOM)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("setCustomType")));
    }

    @Test
    void conflictingForeignType_failsClosedWith400() throws Exception {
        mockMvc.perform(post("/service").header("Authorization", VALID_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(narcoticItem(), foreignCustom())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("InvalidInput")));
    }

    @Test
    void malformedPayload_failsClosedWith400() throws Exception {
        mockMvc.perform(post("/service").header("Authorization", VALID_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":{\"typeId\":\"cart\"}}"))
                .andExpect(status().isBadRequest());
    }
}
