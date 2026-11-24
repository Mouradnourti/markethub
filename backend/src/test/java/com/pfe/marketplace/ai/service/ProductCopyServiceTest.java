package com.pfe.marketplace.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.AiCopyRequest;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraft;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraftRequest;
import com.pfe.marketplace.catalog.entity.Category;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.common.exception.ServiceUnavailableException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductCopyServiceTest {

    @Mock AiClient aiClient;
    @Mock CategoryRepository categoryRepository;
    @InjectMocks ProductCopyService service;

    @Test
    void sendsTheFullCategoryPathAndDefaults() {
        Category parent = new Category();
        parent.setName("Home & Kitchen");
        Category kitchen = new Category();
        kitchen.setName("Kitchen");
        kitchen.setParent(parent);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(kitchen));
        CopyDraft draft = new CopyDraft("t", "d", List.of(), List.of(), List.of(), "home");
        when(aiClient.writeCopy(any())).thenReturn(Optional.of(draft));

        assertThat(service.draft(new CopyDraftRequest(" Kettle ", 7L, null, null, null))).isEqualTo(draft);
        verify(aiClient).writeCopy(new AiCopyRequest("Kettle", "Home & Kitchen / Kitchen", List.of(), "friendly", 0));
    }

    @Test
    void reportsTheAssistantAsUnavailableWhenTheAiServiceIsDown() {
        when(aiClient.writeCopy(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.draft(new CopyDraftRequest("Kettle", null, List.of("2200 W"), "premium", 1)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }
}
