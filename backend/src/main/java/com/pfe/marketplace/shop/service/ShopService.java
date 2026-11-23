package com.pfe.marketplace.shop.service;

import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ForbiddenException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.shop.dto.ShopDtos.CommissionRequest;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopAdminDto;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopDto;
import com.pfe.marketplace.shop.dto.ShopDtos.UpdateShopRequest;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.shop.mapper.ShopMapper;
import com.pfe.marketplace.shop.repository.ShopRepository;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ShopMapper mapper;

    @Transactional(readOnly = true)
    public ShopDto getOwnShop(Long sellerId) {
        return mapper.toDto(findByOwner(sellerId));
    }

    @Transactional
    public ShopDto updateOwnShop(Long sellerId, UpdateShopRequest request) {
        Shop shop = findByOwner(sellerId);
        shop.setName(request.name().trim());
        shop.setDescription(request.description());
        shop.setLogoUrl(request.logoUrl());
        return mapper.toDto(shop);
    }

    @Transactional(readOnly = true)
    public ShopDto getPublicBySlug(String slug) {
        return shopRepository.findBySlug(slug)
                .filter(Shop::isApproved)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("Shop", slug));
    }

    /** The seller's shop, which must be approved before the seller can sell or process orders. */
    @Transactional(readOnly = true)
    public Shop requireApprovedShop(Long sellerId) {
        Shop shop = findByOwner(sellerId);
        if (!shop.isApproved()) {
            throw new ForbiddenException("Your shop is " + shop.getStatus().name().toLowerCase()
                    + ". It must be approved by an administrator first.");
        }
        return shop;
    }

    @Transactional(readOnly = true)
    public PageResponse<ShopAdminDto> listForAdmin(ShopStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by("createdAt").descending());
        Page<Shop> shops = status == null ? shopRepository.findAllBy(pageable) : shopRepository.findByStatus(status, pageable);
        return PageResponse.of(shops, shop -> mapper.toAdminDto(shop, productRepository.countByShopId(shop.getId())));
    }

    @Transactional
    public ShopAdminDto changeStatus(Long shopId, ShopStatus status) {
        Shop shop = find(shopId);
        if (status == ShopStatus.PENDING) {
            throw new BusinessException("A shop cannot be moved back to PENDING");
        }
        shop.setStatus(status);
        return mapper.toAdminDto(shop, productRepository.countByShopId(shopId));
    }

    @Transactional
    public ShopAdminDto changeCommission(Long shopId, CommissionRequest request) {
        Shop shop = find(shopId);
        shop.setCommissionRate(request.commissionRate() == null ? null
                : request.commissionRate().setScale(2, RoundingMode.HALF_UP));
        return mapper.toAdminDto(shop, productRepository.countByShopId(shopId));
    }

    private Shop findByOwner(Long sellerId) {
        return shopRepository.findByOwnerId(sellerId)
                .orElseThrow(() -> new NotFoundException("No shop found for this seller"));
    }

    private Shop find(Long shopId) {
        return shopRepository.findById(shopId).orElseThrow(() -> new NotFoundException("Shop", shopId));
    }
}
