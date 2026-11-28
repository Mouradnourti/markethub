import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { WishlistItem } from '../../core/models/api.models';
import { WishlistService } from '../../core/wishlist/wishlist.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductCard } from '../../shared/ui/product-card';

@Component({
  selector: 'app-wishlist-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, ProductCard, EmptyState, MoneyPipe,
    TranslatePipe],
  template: `
    <div class="container page">
      <div class="section-head">
        <div>
          <h1>{{ 'My wishlist' | t }}</h1>
          <p class="muted">{{ 'We will let you know when a saved product is back in stock or its price drops.' | t }}</p>
        </div>
      </div>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40" /></div>
      } @else if (visible().length === 0) {
        <app-empty-state icon="favorite" [title]="'Your wishlist is empty' | t"
                         [message]="'Tap the heart on any product to save it for later.' | t">
          <a mat-flat-button class="btn-ink" routerLink="/products">{{ 'Discover products' | t }}</a>
        </app-empty-state>
      } @else {
        @if (drops() > 0) {
          <div class="banner"><mat-icon>trending_down</mat-icon>
            {{ (drops() === 1 ? '1 saved product is cheaper than when you saved it.' : '{count} saved products are cheaper than when you saved them.') | t: { count: drops() } }}
          </div>
        }
        <div class="grid-4">
          @for (item of visible(); track item.product.id) {
            <div class="cell">
              <app-product-card [product]="item.product" />
              @if (item.product.price < item.priceWhenAdded) {
                <span class="drop">{{ 'Was {price} when you saved it' | t: { price: (item.priceWhenAdded | money) } }}</span>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    h1 { margin: 0 0 6px; font-size: 32px; }
    .banner { display: flex; gap: 10px; align-items: center; margin-bottom: 20px; padding: 14px 18px; border-radius: 14px;
      background: var(--mh-success-bg); color: var(--mh-success); font-weight: 600; }
    .cell { display: flex; flex-direction: column; gap: 6px; }
    .drop { padding: 6px 10px; border-radius: 10px; background: var(--mh-success-bg); color: var(--mh-success); font-size: 13px; font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WishlistPage {
  private readonly wishlist = inject(WishlistService);
  protected readonly loading = signal(true);
  private readonly items = signal<WishlistItem[]>([]);
  /** Hides items un-hearted on this page without reloading. */
  protected readonly visible = computed(() => this.items().filter((i) => this.wishlist.has(i.product.id)));
  protected readonly drops = computed(() => this.visible().filter((i) => i.product.price < i.priceWhenAdded).length);

  constructor() {
    this.wishlist.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
