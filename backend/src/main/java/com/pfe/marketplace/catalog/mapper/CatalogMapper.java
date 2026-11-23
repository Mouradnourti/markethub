package com.pfe.marketplace.catalog.mapper;

import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.entity.Category;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.entity.ProductImage;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CatalogMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategoryDto toDto(Category category);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "shopSlug", source = "shop.slug")
    ProductDto toDto(Product product);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "imageUrl", expression = "java(firstImage(product))")
    ProductSummaryDto toSummary(Product product);

    default List<String> imageUrls(List<ProductImage> images) {
        return images == null ? List.of() : images.stream().map(ProductImage::getUrl).toList();
    }

    default String firstImage(Product product) {
        return product.getImages().isEmpty() ? null : product.getImages().getFirst().getUrl();
    }
}
