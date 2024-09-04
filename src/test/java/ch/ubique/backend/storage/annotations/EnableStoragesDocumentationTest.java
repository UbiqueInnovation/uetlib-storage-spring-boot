package ch.ubique.backend.storage.annotations;

import static ch.ubique.backend.test.assertion.AssertionUtils.assertThrowsWithMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.ubique.backend.storage.config.implementation.EnableStoragesTestConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ActiveProfiles("test")
@ContextConfiguration(classes = EnableStoragesTestConfig.class)
@AutoConfigureMockMvc
class EnableStoragesDocumentationTest {

    @SpyBean private ObjectMapper objectMapper;

    @Autowired private ApplicationContext applicationContext;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testDocumentation() throws Exception {
        final var testStorageDocumentation =
                applicationContext.getBean("annotatedTestStorageDocumentation");

        mockMvc.perform(get("/documentation/storage/files/5.json"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"type\":\"about:blank\",\"title\":\"Bad"
                                                + " Request\",\"status\":400,\"detail\":\"this storage"
                                                + " method exists only to document blob storage"
                                                + " endpoints and must not be"
                                                + " called\",\"instance\":\"/documentation/storage/files/5.json\",\"properties\":null}"));

        mockMvc.perform(get("/documentation/storage/files/38.png"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"type\":\"about:blank\",\"title\":\"Bad"
                                                + " Request\",\"status\":400,\"detail\":\"this storage"
                                                + " method exists only to document blob storage"
                                                + " endpoints and must not be"
                                                + " called\",\"instance\":\"/documentation/storage/files/38.png\",\"properties\":null}"));
    }

    @Test
    void profileAnnotationStorage() {
        assertNotNull(applicationContext.getBean("profileEnabledAnnotatedTestStorageDocumentation"));
        assertThrowsWithMessage(
                NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean("profileDisabledAnnotatedTestStorageDocumentation"),
                "No bean named 'profileDisabledAnnotatedTestStorageDocumentation' available");
    }
}
