import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { NavigationStart, Router, RouterLink } from '@angular/router';
import { Observable, catchError, filter, of } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import { AddToCartService } from '../core/cart/add-to-cart.service';
import { CartService } from '../core/cart/cart.service';
import { ConfigService } from '../core/config/config.service';
import { CartItem, ProductSummary } from '../core/models/api.models';
import { MoneyPipe } from '../shared/pipes/money.pipe';
import { Price } from '../shared/ui/price';

/** How far the cart is from free shipping, used by the drawer and the cart page. */
export function freeShippingProgress(subtotal: number, threshold: number): { remaining: number; percent: number } {
  if (threshold <= 0) {
    return { remaining: 0, percent: 100 };
  }
  const remaining = Math.max(0, Math.round((threshold - subtotal) * 100) / 100);
  return { remaining, percent: Math.min(100, Math.round((subtotal / threshold) * 100)) };
}

@Component({
  selector: 'app-cart-drawer',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MoneyPipe, Price],
  template: `
    <div class="backdrop" [class.open]="cart.drawerOpen()" (click)="cart.closeDrawer()" aria-hidden="true"></div>
    <aside class="drawer" [class.open]="cart.drawerOpen()" role="dialog" aria-modal="true" aria-label="Shopping cart"
           [attr.aria-hidden]="!cart.drawerOpen()" (keydown.escape)="cart.closeDrawer()">
      <header>
        <h2>Your cart <span class="muted">({{ cart.count() }})</span></h2>
        <button mat-icon-button (click)="cart.closeDrawer()" aria-label="Close cart"><mat-icon>close</mat-icon></button>
      </header>

      @if (cart.isEmpty()) {
        <div class="empty">
          <mat-icon>shopping_bag</mat-icon>
          <h3>Your cart is empty</h3>
          <p class="muted">Discover products from independent shops.</p>
          <a mat-flat-button class="btn-ink" routerLink="/products">Start shopping</a>
        </div>
      } @else {
        <div class="shipping" [class.done]="shipping().remaining === 0">
          @if (shipping().remaining === 0) {
            <p><mat-icon>check_circle</mat-icon> You've unlocked <strong>free shipping</strong></p>
          } @else {
            <p>Add <strong>{{ shipping().remaining | money }}</strong> more for <strong>free shipping</strong></p>
          }
          <mat-progress-bar mode="determinate" [value]="shipping().percent" />
        </div>

        <ul class="items">
          @for (item of cart.cart().items; track item.productId) {
            <li [class.unavailable]="!item.available">
              <a [routerLink]="['/products', item.productId]" class="thumb">
                @if (item.imageUrl) {
                  <img [src]="item.imageUrl" [alt]="item.productName" />
                }
              </a>
              <div class="info">
                <a [routerLink]="['/products', item.productId]" class="name">{{ item.productName }}</a>
                <span class="muted small">Sold by {{ item.shopName }}</span>
                @if (!item.available) {
                  <span class="warn small">Only {{ item.availableStock }} available</span>
                }
                <div class="row">
                  <div class="qty">
                    <button (click)="change(item, -1)" [disabled]="busy()" aria-label="Decrease quantity"><mat-icon>remove</mat-icon></button>
                    <span>{{ item.quantity }}</span>
                    <button (click)="change(item, 1)" [disabled]="busy() || item.quantity >= item.availableStock"
                            aria-label="Increase quantity"><mat-icon>add</mat-icon></button>
                  </div>
                  <button class="remove" (click)="remove(item)" [disabled]="busy()">Remove</button>
                </div>
              </div>
              <strong class="line">{{ item.lineTotal | money }}</strong>
            </li>
          }
        </ul>

        @if (suggestions().length) {
          <section class="upsell">
            <h3>Pairs well with</h3>
            @for (product of suggestions(); track product.id) {
              <div class="suggestion">
                <a [routerLink]="['/products', product.id]" class="thumb small-thumb">
                  @if (product.imageUrl) {
                    <img [src]="product.imageUrl" [alt]="product.name" />
                  }
                </a>
                <div class="info">
                  <a [routerLink]="['/products', product.id]" class="name">{{ product.name }}</a>
                  <app-price [price]="product.price" [compareAtPrice]="product.compareAtPrice" />
                </div>
                <button mat-stroked-button class="add" (click)="addSuggestion(product)" [disabled]="busy()">
                  <mat-icon>add</mat-icon> Add
                </button>
              </div>
            }
          </section>
        }

        <footer>
          <div class="totals">
            <span>Subtotal</span><strong>{{ cart.cart().subtotal | money }}</strong>
          </div>
          <p class="muted small">
            Shipping: {{ cart.cart().shippingFee === 0 ? 'free' : (cart.cart().shippingFee | money) }} ·
            pay by card or cash on delivery
          </p>
          <a mat-flat-button class="btn-ink checkout" routerLink="/checkout"><mat-icon>lock</mat-icon> Checkout</a>
          <a class="view-cart" routerLink="/cart">View cart</a>
        </footer>
      }
    </aside>
  `,
  styleUrl: './cart-drawer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartDrawer {
  protected readonly cart = inject(CartService);
  private readonly config = inject(ConfigService);
  private readonly catalog = inject(CatalogApi);
  private readonly addToCart = inject(AddToCartService);
  protected readonly busy = signal(false);
  private readonly pairings = signal<ProductSummary[]>([]);

  protected readonly shipping = computed(() =>
    freeShippingProgress(this.cart.cart().subtotal, this.config.config().freeShippingThreshold),
  );
  protected readonly suggestions = computed(() => {
    const inCart = new Set(this.cart.cart().items.map((i) => i.productId));
    return this.pairings().filter((p) => !inCart.has(p.id) && p.stock > 0).slice(0, 2);
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationStart), takeUntilDestroyed(destroyRef))
      .subscribe(() => this.cart.closeDrawer());

    effect(() => {
      const id = this.cart.lastAddedId();
      if (id === null) {
        return;
      }
      this.catalog
        .boughtTogether(id, 4)
        .pipe(catchError(() => of([])))
        .subscribe((items) => this.pairings.set(items));
    });

    // Keep the page behind the drawer from scrolling.
    effect(() => {
      document.body.style.overflow = this.cart.drawerOpen() ? 'hidden' : '';
    });
  }

  protected change(item: CartItem, delta: number): void {
    const quantity = item.quantity + delta;
    this.run(quantity <= 0 ? this.cart.remove(item.productId) : this.cart.update(item.productId, quantity));
  }

  protected remove(item: CartItem): void {
    this.run(this.cart.remove(item.productId));
  }

  protected addSuggestion(product: ProductSummary): void {
    this.run(this.addToCart.add(product.id, 1, { openDrawer: false }));
  }

  private run(request: Observable<unknown>): void {
    this.busy.set(true);
    request.subscribe({ error: () => this.busy.set(false), complete: () => this.busy.set(false) });
  }
}
