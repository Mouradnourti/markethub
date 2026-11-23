import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { CartService } from '../../core/cart/cart.service';
import { ConfigService } from '../../core/config/config.service';
import { CartItem } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';

@Component({
  selector: 'app-cart-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MoneyPipe, EmptyState],
  template: `
    <div class="container page">
      <div class="page-title"><div><h1>Your cart</h1><p>{{ cart.count() }} item(s)</p></div></div>

      @if (cart.isEmpty()) {
        <div class="panel">
          <app-empty-state icon="shopping_cart" title="Your cart is empty" message="Browse the catalog and add products you like.">
            <a mat-flat-button routerLink="/products">Start shopping</a>
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
                      <span class="muted">{{ item.unitPrice | money }} each</span>
                      @if (!item.available) {
                        <span class="warn">Only {{ item.availableStock }} left — reduce the quantity</span>
                      }
                    </div>
                    <div class="qty">
                      <button mat-icon-button (click)="update(item, item.quantity - 1)" [disabled]="item.quantity <= 1" aria-label="Decrease"><mat-icon>remove</mat-icon></button>
                      <span>{{ item.quantity }}</span>
                      <button mat-icon-button (click)="update(item, item.quantity + 1)" [disabled]="item.quantity >= item.availableStock" aria-label="Increase"><mat-icon>add</mat-icon></button>
                    </div>
                    <strong class="total">{{ item.lineTotal | money }}</strong>
                    <button mat-icon-button (click)="remove(item)" aria-label="Remove"><mat-icon>delete</mat-icon></button>
                  </div>
                }
              </div>
            }
          </section>

          <aside class="panel summary">
            <h2>Order summary</h2>
            <div class="sum"><span>Subtotal</span><span>{{ cart.cart().subtotal | money }}</span></div>
            <div class="sum"><span>Shipping</span><span>{{ cart.cart().shippingFee ? (cart.cart().shippingFee | money) : 'Free' }}</span></div>
            @if (missingForFree() > 0) {
              <div class="hint"><mat-icon>local_shipping</mat-icon> Add {{ missingForFree() | money }} for free shipping</div>
            }
            <div class="sum total-line"><span>Total</span><span>{{ cart.cart().total | money }}</span></div>
            <a mat-flat-button routerLink="/checkout" class="checkout" [class.disabled]="hasUnavailable()"
               [attr.aria-disabled]="hasUnavailable()">Proceed to checkout</a>
            <a mat-button routerLink="/products" class="full-width">Continue shopping</a>
            <p class="muted small">Items from {{ groups().length }} shop(s) ship separately, one checkout.</p>
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
