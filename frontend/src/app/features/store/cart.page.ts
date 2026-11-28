import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CartService } from '../../core/cart/cart.service';
import { ConfigService } from '../../core/config/config.service';
import { CartItem } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';

import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { CouponField } from '../../shared/ui/coupon-field';

@Component({
  selector: 'app-cart-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MoneyPipe, EmptyState, CouponField, TranslatePipe],
  template: `
    <div class="container page">
      <div class="page-title"><div><h1>{{ 'Your cart' | t }}</h1><p>{{ '{count} item(s)' | t: { count: cart.count() } }}</p></div></div>

      @if (cart.isEmpty()) {
        <div class="panel">
          <app-empty-state icon="shopping_cart" [title]="'Your cart is empty' | t" [message]="'Browse the catalog and add products you like.' | t">
            <a mat-flat-button class="btn-ink" routerLink="/products">{{ 'Start shopping' | t }}</a>
          </app-empty-state>
        </div>
      } @else {
        <div class="grid-2">
          <section class="panel">
            @for (group of groups(); track group.shopId) {
              <div class="shop-group">
                <h3><mat-icon>storefront</mat-icon> {{ group.shopName }}</h3>
                @for (item of group.items; track item.productId) {
                  <div class="line" [class.unavailable]="!item.available">
                    <a [routerLink]="['/products', item.productId]" class="thumb">
                      @if (item.imageUrl) { <img [src]="item.imageUrl" [alt]="item.productName" /> }
                    </a>
                    <div class="details">
                      <a [routerLink]="['/products', item.productId]" class="name">{{ item.productName }}</a>
                      <span class="muted">{{ '{price} each' | t: { price: (item.unitPrice | money) } }}</span>
                      @if (!item.available) {
                        <span class="warn">{{ 'Only {count} left, reduce the quantity' | t: { count: item.availableStock } }}</span>
                      }
                    </div>
                    <div class="qty">
                      <button mat-icon-button (click)="update(item, item.quantity - 1)" [disabled]="item.quantity <= 1" [attr.aria-label]="'Decrease quantity' | t"><mat-icon>remove</mat-icon></button>
                      <span>{{ item.quantity }}</span>
                      <button mat-icon-button (click)="update(item, item.quantity + 1)" [disabled]="item.quantity >= item.availableStock" [attr.aria-label]="'Increase quantity' | t"><mat-icon>add</mat-icon></button>
                    </div>
                    <strong class="total">{{ item.lineTotal | money }}</strong>
                    <button mat-icon-button (click)="remove(item)" [attr.aria-label]="'Remove' | t"><mat-icon>delete</mat-icon></button>
                  </div>
                }
              </div>
            }
          </section>

          <aside class="panel summary">
            <h2>{{ 'Order summary' | t }}</h2>
            <app-coupon-field class="coupon" />
            <div class="sum"><span>{{ 'Subtotal' | t }}</span><span>{{ cart.cart().subtotal | money }}</span></div>
            @if (cart.cart().discount > 0) {
              <div class="sum discount"><span>{{ 'Discount ({code})' | t: { code: cart.cart().coupon?.code } }}</span><span>−{{ cart.cart().discount | money }}</span></div>
            }
            <div class="sum"><span>{{ 'Shipping' | t }}</span><span>{{ cart.cart().shippingFee ? (cart.cart().shippingFee | money) : ('Free' | t) }}</span></div>
            @if (missingForFree() > 0) {
              <div class="hint"><mat-icon>local_shipping</mat-icon> {{ 'Add {amount} for free shipping' | t: { amount: (missingForFree() | money) } }}</div>
            }
            <div class="sum total-line"><span>{{ 'Total' | t }}</span><span>{{ cart.cart().total | money }}</span></div>
            <a mat-flat-button routerLink="/checkout" class="checkout btn-ink" [class.disabled]="hasUnavailable()"
               [attr.aria-disabled]="hasUnavailable()">{{ 'Proceed to checkout' | t }}</a>
            <a mat-button routerLink="/products" class="full-width">{{ 'Continue shopping' | t }}</a>
            <p class="muted small">{{ 'Items from {count} shop(s) ship separately, one checkout.' | t: { count: groups().length } }}</p>
          </aside>
        </div>
      }
    </div>
  `,
  styleUrl: './cart.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartPage {
  protected readonly cart = inject(CartService);
  private readonly config = inject(ConfigService);

  protected readonly groups = computed(() => {
    const byShop = new Map<number, { shopId: number; shopName: string; items: CartItem[] }>();
    for (const item of this.cart.cart().items) {
      const group = byShop.get(item.shopId) ?? { shopId: item.shopId, shopName: item.shopName, items: [] };
      group.items.push(item);
      byShop.set(item.shopId, group);
    }
    return [...byShop.values()];
  });
  protected readonly hasUnavailable = computed(() => this.cart.cart().items.some((i) => !i.available));
  protected readonly missingForFree = computed(() => {
    const threshold = this.config.config().freeShippingThreshold;
    return threshold > 0 && this.cart.cart().shippingFee > 0 ? threshold - this.cart.cart().subtotal : 0;
  });

  constructor() {
    this.cart.refresh().subscribe({ error: () => undefined });
  }

  protected update(item: CartItem, quantity: number): void {
    this.cart.update(item.productId, quantity).subscribe({ error: () => undefined });
  }

  protected remove(item: CartItem): void {
    this.cart.remove(item.productId).subscribe({ error: () => undefined });
  }
}
