package com.pfe.marketplace.order.mapper;

import com.pfe.marketplace.order.dto.OrderDtos.OrderDto;
import com.pfe.marketplace.order.dto.OrderDtos.OrderItemDto;
import com.pfe.marketplace.order.dto.OrderDtos.SellerOrderDto;
import com.pfe.marketplace.order.dto.OrderDtos.ShippingAddress;
import com.pfe.marketplace.order.dto.OrderDtos.ShopOrderDto;
import com.pfe.marketplace.order.dto.OrderDtos.StatusChangeDto;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderItem;
import com.pfe.marketplace.order.entity.OrderStatusHistory;
import com.pfe.marketplace.order.entity.ShopOrder;
import java.util.Set;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface OrderMapper {

    @Mapping(target = "shippingAddress", expression = "java(address(order))")
    OrderDto toDto(Order order, @Context Set<Long> shopOrdersWithOpenReturn);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "returnRequested", expression = "java(shopOrdersWithOpenReturn.contains(shopOrder.getId()))")
    ShopOrderDto toDto(ShopOrder shopOrder, @Context Set<Long> shopOrdersWithOpenReturn);

    @Mapping(target = "orderReference", source = "order.reference")
    @Mapping(target = "paymentMethod", source = "order.paymentMethod")
    @Mapping(target = "paymentStatus", source = "order.paymentStatus")
    @Mapping(target = "customerName", source = "order.shippingFullName")
    @Mapping(target = "returnRiskScore", source = "order.returnRiskScore")
    @Mapping(target = "netAmount", expression = "java(shopOrder.getNetAmount())")
    @Mapping(target = "shippingAddress", expression = "java(address(shopOrder.getOrder()))")
    SellerOrderDto toSellerDto(ShopOrder shopOrder);

    @Mapping(target = "productId", source = "product.id")
    OrderItemDto toDto(OrderItem item);

    StatusChangeDto toDto(OrderStatusHistory history);

    default ShippingAddress address(Order order) {
        return new ShippingAddress(order.getShippingFullName(), order.getShippingPhone(),
                order.getShippingAddressLine(), order.getShippingCity(), order.getShippingPostalCode(),
                order.getShippingCountry());
    }
}
