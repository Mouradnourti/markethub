package com.pfe.marketplace.support;

import com.pfe.marketplace.catalog.entity.Category;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderItem;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import java.math.BigDecimal;

/** In-memory entity builders for unit tests. */
public final class Fixtures {

    private Fixtures() {
    }

    public static User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.local");
        user.setFirstName("First" + id);
        user.setLastName("Last" + id);
        user.setRole(role);
        return user;
    }

    public static Shop shop(long id, ShopStatus status) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("Shop " + id);
        shop.setSlug("shop-" + id);
        shop.setStatus(status);
        shop.setOwner(user(100 + id, Role.SELLER));
        return shop;
    }

    public static Category category(long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Category " + id);
        category.setSlug("category-" + id);
        return category;
    }

    public static Product product(long id, Shop shop, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setShop(shop);
        product.setCategory(category(1));
        product.setName("Product " + id);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setActive(true);
        return product;
    }

    public static ShopOrder shopOrder(long id, OrderStatus status, PaymentMethod method, Product... products) {
        Order order = new Order();
        order.setId(1000 + id);
        order.setReference("MH-TEST-" + id);
        order.setPaymentMethod(method);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCustomer(user(1, Role.CUSTOMER));
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setId(id);
        shopOrder.setStatus(status);
        shopOrder.setShop(products.length > 0 ? products[0].getShop() : shop(1, ShopStatus.APPROVED));
        shopOrder.setSubtotal(BigDecimal.TEN);
        shopOrder.setCommissionAmount(BigDecimal.ONE);
        for (Product product : products) {
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(2);
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(product.getPrice().multiply(BigDecimal.TWO));
            shopOrder.addItem(item);
        }
        order.addShopOrder(shopOrder);
        return shopOrder;
    }
}
