package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.AiCopyRequest;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraft;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraftRequest;
import com.pfe.marketplace.catalog.entity.Category;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.common.exception.ServiceUnavailableException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Drafts product copy for sellers with the AI service's description writer. */
@Service
@RequiredArgsConstructor
public class ProductCopyService {

    private final AiClient aiClient;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public CopyDraft draft(CopyDraftRequest request) {
        String category = request.categoryId() == null ? null
                : categoryRepository.findById(request.categoryId()).map(this::fullName).orElse(null);
        var aiRequest = new AiCopyRequest(request.name().trim(), category,
                request.keywords() == null ? List.of() : request.keywords().stream().map(String::trim).toList(),
                request.tone() == null ? "friendly" : request.tone(),
                request.variant() == null ? 0 : request.variant());
        return aiClient.writeCopy(aiRequest)
                .orElseThrow(() -> new ServiceUnavailableException(
                        "The description assistant is unavailable right now. Please try again in a moment."));
    }

    /** "Kitchen" is more useful to the writer with its parent: "Home & Kitchen / Kitchen". */
    private String fullName(Category category) {
        return category.getParent() == null ? category.getName()
                : category.getParent().getName() + " / " + category.getName();
    }
}
