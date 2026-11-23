package com.pfe.marketplace.catalog.service;

import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryRequest;
import com.pfe.marketplace.catalog.entity.Category;
import com.pfe.marketplace.catalog.mapper.CatalogMapper;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.common.util.Slugs;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    public static final String CACHE = "categories";

    private final CategoryRepository repository;
    private final CatalogMapper mapper;

    @Cacheable(CACHE)
    @Transactional(readOnly = true)
    public List<CategoryDto> findAll() {
        return repository.findAllWithParent().stream().map(mapper::toDto).toList();
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public CategoryDto create(CategoryRequest request) {
        Category category = new Category();
        apply(category, request);
        category.setSlug(Slugs.uniqueSlug(request.name(), repository::existsBySlug));
        return mapper.toDto(repository.save(category));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = find(id);
        if (Objects.equals(request.parentId(), id)) {
            throw new BusinessException("A category cannot be its own parent");
        }
        apply(category, request);
        return mapper.toDto(category);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (repository.existsByParentId(id)) {
            throw new BusinessException("Delete or move the sub-categories first");
        }
        try {
            repository.delete(category);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("This category still contains products");
        }
    }

    Category find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setParent(request.parentId() == null ? null : find(request.parentId()));
    }
}
