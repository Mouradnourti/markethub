package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.AiPriceHintRequest;
import com.pfe.marketplace.ai.dto.AiDtos.AiSearchHit;
import com.pfe.marketplace.ai.dto.AiDtos.AiSearchRequest;
import com.pfe.marketplace.ai.dto.AiDtos.PriceHintDto;
import com.pfe.marketplace.ai.dto.AiDtos.PriceHintRequest;
import com.pfe.marketplace.ai.dto.AiDtos.SmartSearchDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSearchCriteria;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSort;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog features backed by the AI index: meaning-based search and price suggestions for sellers. */
@Service
@RequiredArgsConstructor
public class AiCatalogService {

    private final AiClient aiClient;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    /**
     * Finds products by meaning ("something to keep coffee hot"). Falls back to the typo-tolerant database
     * search when the AI service is unavailable, so shoppers always get results.
     */
    @Transactional(readOnly = true)
    public SmartSearchDto search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new SmartSearchDto(List.of(), "keyword");
        }
        var semantic = aiClient.search(new AiSearchRequest(q, limit * 2))
                .map(result -> productService.findVisibleByIds(result.hits().stream().map(AiSearchHit::productId).toList()))
                .filter(products -> !products.isEmpty());
        if (semantic.isPresent()) {
            return new SmartSearchDto(semantic.get().stream().limit(limit).toList(), "semantic");
        }
        List<ProductSummaryDto> keyword = productService.search(new ProductSearchCriteria(q, null, null, null, null,
                null, null, null, ProductSort.RELEVANCE), 0, limit).content();
        return new SmartSearchDto(keyword, "keyword");
    }

    @Transactional(readOnly = true)
    public PriceHintDto priceHint(PriceHintRequest request) {
        String category = request.categoryId() == null ? null
                : categoryRepository.findById(request.categoryId()).map(c -> c.getName()).orElse(null);
        return aiClient.priceHint(new AiPriceHintRequest(request.name().trim(), category, request.description()))
                .orElseThrow(() -> new NotFoundException(
                        "No price suggestion right now: no comparable product was found, or the assistant is offline"));
    }
}
