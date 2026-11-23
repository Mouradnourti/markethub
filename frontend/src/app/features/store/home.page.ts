import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { OrderApi } from '../../core/api/order.api';
import { AuthService } from '../../core/auth/auth.service';
import { ProductSummary } from '../../core/models/api.models';
import { ConfigService } from '../../core/config/config.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ProductCard } from '../../shared/ui/product-card';

const CATEGORY_ICONS: Record<string, string> = {
  electronics: 'devices',
  'home-kitchen': 'chair',
  fashion: 'apparel',
  'beauty-health': 'spa',
  'sports-outdoors': 'sports_tennis',
  'books-stationery': 'menu_book',
  'toys-games': 'toys',
};

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, ProductCard, MoneyPipe],
  template: `
    <section class="hero">
      <div class="container hero-inner">
        <div>
          <span class="eyebrow">New season · up to 30% off</span>
          <h1>Everything you love,<br />from shops you trust.</h1>
          <p>Discover {{ catalogSize() }} products from independent sellers. Pay by card or cash on delivery.</p>
          <div class="row">
            <a mat-flat-button routerLink="/products" class="cta">Start shopping</a>
            <a mat-stroked-button routerLink="/register" [queryParams]="{ type: 'SELLER' }" class="cta-alt">Become a seller</a>
          </div>
        </div>
        <div class="perks">
          <div><mat-icon>local_shipping</mat-icon><strong>Free shipping</strong><span>from {{ config.config().freeShippingThreshold | money }} per order</span></div>
          <div><mat-icon>payments</mat-icon><strong>Cash on delivery</strong><span>or secure card payment</span></div>
          <div><mat-icon>autorenew</mat-icon><strong>Easy returns</strong><span>within 14 days</span></div>
        </div>
      </div>
    </section>

    <div class="container page">
      <section class="block">
        <h2>Shop by category</h2>
        <div class="categories">
          @for (category of topCategories(); track category.id) {
            <a class="category" routerLink="/products" [queryParams]="{ categoryId: category.id }">
              <mat-icon>{{ icon(category.slug) }}</mat-icon>
              <span>{{ category.name }}</span>
            </a>
          }
        </div>
      </section>

      @if (recommended().length) {
        <section class="block">
          <header>
            <h2><mat-icon class="ai">auto_awesome</mat-icon> Recommended for you</h2>
            <span class="muted">Picked by our AI from your purchase history</span>
          </header>
          <div class="grid-products">
            @for (product of recommended(); track product.id) {
              <app-product-card [product]="product" tag="For you" />
            }
          </div>
        </section>
      }

      <section class="block">
        <header>
          <h2>Best sellers</h2>
          <a mat-button routerLink="/products" [queryParams]="{ sort: 'POPULAR' }">See all <mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
        </header>
        <div class="grid-products">
          @for (product of popular(); track product.id) {
            <app-product-card [product]="product" />
          }
        </div>
      </section>

      <section class="block">
        <header>
          <h2>New arrivals</h2>
          <a mat-button routerLink="/products" [queryParams]="{ sort: 'NEWEST' }">See all <mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
        </header>
        <div class="grid-products">
          @for (product of newest(); track product.id) {
            <app-product-card [product]="product" tag="New" />
          }
        </div>
      </section>
    </div>
  `,
  styleUrl: './home.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  private readonly catalog = inject(CatalogApi);
  protected readonly config = inject(ConfigService);
  private readonly auth = inject(AuthService);
  private readonly popularPage = toSignal(this.catalog.search({ sort: 'POPULAR', size: 10, inStock: true }));
  private readonly newestPage = toSignal(this.catalog.search({ sort: 'NEWEST', size: 5 }));
  private readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });
  protected readonly recommended = signal<ProductSummary[]>([]);

  protected readonly popular = computed(() => this.popularPage()?.content ?? []);
  protected readonly newest = computed(() => this.newestPage()?.content ?? []);
  protected readonly catalogSize = computed(() => this.popularPage()?.totalElements ?? '100+');
  protected readonly topCategories = computed(() => this.categories().filter((c) => !c.parentId));

  constructor() {
    if (this.auth.role() === 'CUSTOMER') {
      inject(OrderApi)
        .recommendations(5)
        .pipe(
          map((items) => items.slice(0, 5)),
          catchError(() => of([])),
        )
        .subscribe((items) => this.recommended.set(items));
    }
  }

  protected icon(slug: string): string {
    return CATEGORY_ICONS[slug] ?? 'category';
  }
}
