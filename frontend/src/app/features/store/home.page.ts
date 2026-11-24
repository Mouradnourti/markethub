import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { OrderApi } from '../../core/api/order.api';
import { AuthService } from '../../core/auth/auth.service';
import { ConfigService } from '../../core/config/config.service';
import { RecentlyViewedService } from '../../core/history/recently-viewed.service';
import { Category, ProductSummary } from '../../core/models/api.models';
import { Price, discountPercent } from '../../shared/ui/price';
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

interface CategoryTile {
  category: Category;
  image?: string;
  icon: string;
}

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, DecimalPipe, MatButtonModule, MatIconModule, ProductCard, Price],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  private readonly catalog = inject(CatalogApi);
  protected readonly config = inject(ConfigService);
  private readonly auth = inject(AuthService);
  private readonly history = inject(RecentlyViewedService);

  private readonly popularPage = toSignal(this.catalog.search({ sort: 'POPULAR', size: 48, inStock: true }));
  private readonly dealsPage = toSignal(this.catalog.search({ onSale: true, sort: 'POPULAR', size: 24, inStock: true }));
  private readonly newestPage = toSignal(this.catalog.search({ sort: 'NEWEST', size: 4 }));
  private readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });
  protected readonly recommended = signal<ProductSummary[]>([]);

  private readonly popularAll = computed(() => this.popularPage()?.content ?? []);
  protected readonly popular = computed(() => this.popularAll().slice(0, 8));
  protected readonly heroProducts = computed(() => this.popularAll().slice(0, 4));
  /** Discounted products that are not already in the best-seller row, so each section shows something new. */
  protected readonly deals = computed(() => {
    const shown = new Set(this.popular().map((p) => p.id));
    return (this.dealsPage()?.content ?? []).filter((p) => !shown.has(p.id)).slice(0, 4);
  });
  protected readonly newest = computed(() => this.newestPage()?.content ?? []);
  protected readonly recentlyViewed = computed(() => this.history.items().slice(0, 4));
  protected readonly catalogSize = computed(() => this.popularPage()?.totalElements ?? 0);
  protected readonly dealCount = computed(() => this.dealsPage()?.totalElements ?? 0);
  protected readonly bestDiscount = computed(() =>
    Math.max(0, ...(this.dealsPage()?.content ?? []).map((p) => discountPercent(p.price, p.compareAtPrice))),
  );
  /** Review-weighted average rating of the best sellers shown on the page. */
  protected readonly bestSellerRating = computed(() => {
    const rated = this.popularAll().filter((p) => p.reviewCount > 0);
    const reviews = rated.reduce((sum, p) => sum + p.reviewCount, 0);
    const rating = reviews ? rated.reduce((sum, p) => sum + p.avgRating * p.reviewCount, 0) / reviews : 0;
    return { rating, reviews };
  });

  protected readonly tiles = computed<CategoryTile[]>(() => {
    const all = this.categories();
    const parentOf = new Map(all.map((c) => [c.id, c.parentId ?? c.id]));
    const imageByTop = new Map<number, string>();
    for (const product of this.popularAll()) {
      const top = parentOf.get(product.categoryId);
      if (top !== undefined && product.imageUrl && !imageByTop.has(top)) {
        imageByTop.set(top, product.imageUrl);
      }
    }
    return all
      .filter((c) => !c.parentId)
      .map((category) => ({
        category,
        image: imageByTop.get(category.id),
        icon: CATEGORY_ICONS[category.slug] ?? 'category',
      }));
  });

  constructor() {
    if (this.auth.role() === 'CUSTOMER') {
      inject(OrderApi)
        .recommendations(8)
        .pipe(
          map((items) => items.slice(0, 4)),
          catchError(() => of([])),
        )
        .subscribe((items) => this.recommended.set(items));
    }
  }
}
