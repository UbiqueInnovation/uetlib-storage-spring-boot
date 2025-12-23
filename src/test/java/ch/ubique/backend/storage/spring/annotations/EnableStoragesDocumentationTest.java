package ch.ubique.backend.storage.spring.annotations;

import static ch.ubique.backend.test.assertion.AssertionUtils.assertThrowsWithMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.stream.Collectors.toMap;

import ch.ubique.backend.storage.spring.config.EnableStoragesTestConfig;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;

@WebMvcTest
@ActiveProfiles("test")
@ContextConfiguration(classes = EnableStoragesTestConfig.class)
@AutoConfigureMockMvc
class EnableStoragesDocumentationTest {

    @MockitoSpyBean private ObjectMapper objectMapper;

    @Autowired private ApplicationContext applicationContext;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testDocumentationControllerReturnTypes() {
        final var testStorageDocumentationController =
                applicationContext.getBean("annotatedTestStorageDocumentationController");
        final var controllerMethods =
                Arrays.stream(testStorageDocumentationController.getClass().getDeclaredMethods())
                        .collect(
                                toMap(
                                        Method::getName,
                                        method ->
                                                ((ParameterizedType) method.getGenericReturnType())
                                                        .getActualTypeArguments()[0]
                                                                .getTypeName()));
        assertEquals(
                ofEntries(
                        entry("saveImage", "byte[]"),
                        entry("saveImageByteOutput", "byte[]"),
                        entry("saveJson", "ch.ubique.backend.storage.spring.annotations.TestUser"),
                        entry(
                                "saveGenericMap",
                                "java.util.Map<java.lang.Integer, java.lang.String>"),
                        entry(
                                "saveJsonOutput",
                                "ch.ubique.backend.storage.spring.annotations.TestUser")),
                controllerMethods);
    }

    @Test
    void testDocumentation() throws Exception {
        final var testStorageDocumentation =
                applicationContext.getBean("annotatedTestStorageDocumentationController");
        assertNotNull(testStorageDocumentation);

        mockMvc.perform(get("/documentation/storage/files/5.json"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"detail\":\"this storage method exists only to document blob storage endpoints and must not be called\",\"instance\":\"/documentation/storage/files/5.json\",\"status\":400,\"title\":\"Bad Request\"}"));

        mockMvc.perform(get("/documentation/storage/files/38.png"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .json(
                                        "{\"detail\":\"this storage method exists only to document blob storage endpoints and must not be called\",\"instance\":\"/documentation/storage/files/38.png\",\"status\":400,\"title\":\"Bad Request\"}"));
    }

    @Test
    void profileAnnotationStorage() {
        assertDoesNotThrow(
                () ->
                        applicationContext.getBean(
                                "profileEnabledAnnotatedTestStorageDocumentationController"));
        assertThrowsWithMessage(
                NoSuchBeanDefinitionException.class,
                () ->
                        applicationContext.getBean(
                                "profileDisabledAnnotatedTestStorageDocumentationController"),
                "No bean named 'profileDisabledAnnotatedTestStorageDocumentationController' available");
    }
}
