package ch.ubique.backend.storage.annotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.ubique.backend.storage.config.implementation.EnableStoragesImplementationTestConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@ContextConfiguration(classes = EnableStoragesImplementationTestConfig.class)
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
        applicationContext.getBeansWithAnnotation(RestController.class);
        assertDoesNotThrow(() -> applicationContext.getBean("annotatedTestStorageDocumentation"));
        final var testStorageDocumentation =
                applicationContext.getBean("annotatedTestStorageDocumentation");
        assertNotNull(testStorageDocumentation);

        mockMvc.perform(get("/documentation/storage/files/5.json"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"this storage method exists only to document blob storage endpoints and must not be called\",\"instance\":\"/documentation/storage/files/5.json\",\"properties\":null}"));

        mockMvc.perform(get("/documentation/storage/files/38.png"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"this storage method exists only to document blob storage endpoints and must not be called\",\"instance\":\"/documentation/storage/files/38.png\",\"properties\":null}"));
    }
}
