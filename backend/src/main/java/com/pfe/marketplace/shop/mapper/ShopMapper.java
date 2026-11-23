package com.pfe.marketplace.shop.mapper;

import com.pfe.marketplace.shop.dto.ShopDtos.ShopAdminDto;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopDto;
import com.pfe.marketplace.shop.entity.Shop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ShopMapper {

    ShopDto toDto(Shop shop);

    @Mapping(target = "ownerId", source = "shop.owner.id")
    @Mapping(target = "ownerEmail", source = "shop.owner.email")
    @Mapping(target = "ownerName", source = "shop.owner.fullName")
    ShopAdminDto toAdminDto(Shop shop, long productCount);
}
