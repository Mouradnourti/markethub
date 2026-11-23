import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { DatePipe } from '@angular/common';
import { CatalogApi } from '../../core/api/catalog.api';
import { Page, ProductSummary, Shop } from '../../core/models/api.models';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductCard } from '../../shared/ui/product-card';

@Component({
  selector: 'app-shop-page',
  imports: [MatIconModule, MatPaginatorModule, DatePipe, ProductCard, EmptyState],
  template: `
    @if (shop(); as s) {
      <section class="banner">
        <div class="container inner">
          <div class="logo">{{ s.name.charAt(0) }}</div>
          <div>
            <h1>{{ s.name }}</h1>
            <p>{{ s.description }}</p>
            <span class="since"><mat-icon>verified</mat-icon> Verified seller since {{ s.createdAt | date: 'MMMM yyyy' }}</span>
          </div>
        </div>
      </section>
      <div class="container page">
        @if (products(); as page) {
          <h2>{{ page.totalElements }} products</h2>
          <div class="grid-products">
            @for (product of page.content; track product.id) {
              <app-product-card [product]="product" />
            }
          </div>
          @if (page.totalPages > 1) {
            <mat-paginator [length]="page.totalElements" [pageIndex]="page.page" [pageSize]="12" [hidePageSize]="true"
                           (page)="changePage($event)" />
          }
        }
      </div>
    } @else if (missing()) {
      <div class="container page">
        <app-empty-state icon="storefront" title="Shop not found" message="This shop does not exist or is not open yet." />
      </div>
    }
  `,
  styles: `
    .banner { background: linear-gradient(135deg, #1e1b4b, #4338ca); color: #fff; }
    .inner { display: flex; gap: 24px; align-items: center; padding-top: 36px; padding-bottom: 36px; }
    .logo { display: grid; place-items: center; width: 88px; height: 88px; border-radius: 24px; background: #fff;
      color: #4338ca; font-size: 40px; font-weight: 800; flex-shrink: 0; }
    h1 { margin: 0; font-size: 32px; }
    p { margin: 6px 0 10px; color: #c7d2fe; max-width: 640px; }
    .since { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; color: #a5b4fc; }
    h2 { font-size: 18px; color: var(--mh-text-muted); }
    mat-paginator { margin-top: 20px; background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopPage {
  readonly slug = input.required<string>();
  private readonly catalog = inject(CatalogApi);
  protected readonly shop = signal<Shop | null>(null);
  protected readonly products = signal<Page<ProductSummary> | null>(null);
  protected readonly missing = signal(false);

  constructor() {
    effect(() => {
      this.catalog.shop(this.slug()).subscribe({
        next: (shop) => {
          this.shop.set(shop);
          this.loadProducts(0);
        },
        error: () => this.missing.set(true),
      });
    });
  }

  protected changePage(event: PageEvent): void {
    this.loadProducts(event.pageIndex);
  }

  private loadProducts(page: number): void {
    const shop = this.shop();
    if (shop) {
      this.catalog.search({ shopId: shop.id, sort: 'POPULAR', page, size: 12 }).subscribe((p) => this.products.set(p));
    }
  }
}
