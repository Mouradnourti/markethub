import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Facets, Page, ProductQuery, ProductSort, ProductSummary } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductCard } from '../../shared/ui/product-card';

const PAGE_SIZE = 24;

/** Catalog with filters kept in the URL (shareable, back-button friendly). */
@Component({
  selector: 'app-catalog-page',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatRadioModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    ProductCard,
    EmptyState,
    MoneyPipe,
    TranslatePipe,
  ],
  templateUrl: './catalog.page.html',
  styleUrl: './catalog.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  readonly q = input<string>();
  readonly categoryId = input<string>();
  readonly sort = input<string>();
  readonly minPrice = input<string>();
  readonly maxPrice = input<string>();
  readonly minRating = input<string>();
  readonly inStock = input<string>();
  readonly onSale = input<string>();
  readonly page = input<string>();

  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly facets = signal<Facets | null>(null);
  /** Meaning-based matches shown above the results for phrase searches ("gift for a coffee lover"). */
  protected readonly aiPicks = signal<ProductSummary[]>([]);
  protected readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });
  protected readonly result = signal<Page<ProductSummary> | null>(null);
  protected readonly loading = signal(false);

  protected minPriceInput = '';
  protected maxPriceInput = '';

  protected readonly sortOptions = computed<{ value: ProductSort; label: string }[]>(() => [
    ...(this.q() ? [{ value: 'RELEVANCE' as const, label: 'Best match' }] : []),
    { value: 'NEWEST', label: 'Newest' },
    { value: 'POPULAR', label: 'Best sellers' },
    { value: 'PRICE_ASC', label: 'Price: low to high' },
    { value: 'PRICE_DESC', label: 'Price: high to low' },
    { value: 'RATING', label: 'Top rated' },
  ]);
  protected readonly currentSort = computed<ProductSort>(
    () => (this.sort() as ProductSort) || (this.q() ? 'RELEVANCE' : 'NEWEST'),
  );
  private readonly facetById = computed(() => new Map((this.facets()?.categories ?? []).map((c) => [c.id, c.count])));
  protected readonly allCount = computed(() => (this.facets()?.categories ?? []).reduce((sum, c) => sum + c.count, 0));
  protected readonly activeChips = computed(() => {
    const chips: { key: string; label: string; clear: Record<string, null> }[] = [];
    if (this.q()) {
      chips.push({ key: 'q', label: `“${this.q()}”`, clear: { q: null } });
    }
    const category = this.selectedCategory();
    if (category) {
      chips.push({ key: 'cat', label: category.name, clear: { categoryId: null } });
    }
    if (this.minPrice() || this.maxPrice()) {
      chips.push({
        key: 'price',
        label: `${this.minPrice() || '0'} – ${this.maxPrice() || '∞'}`,
        clear: { minPrice: null, maxPrice: null },
      });
    }
    if (this.minRating()) {
      chips.push({ key: 'rating', label: this.i18n.t('{stars}★ & up', { stars: this.minRating() }), clear: { minRating: null } });
    }
    if (this.onSale() === 'true') {
      chips.push({ key: 'sale', label: this.i18n.t('On sale'), clear: { onSale: null } });
    }
    if (this.inStock() === 'true') {
      chips.push({ key: 'stock', label: this.i18n.t('In stock only'), clear: { inStock: null } });
    }
    return chips;
  });

  protected readonly parents = computed(() => this.categories().filter((c) => !c.parentId));
  protected readonly selectedCategory = computed(() => {
    const id = Number(this.categoryId());
    return this.categories().find((c) => c.id === id) ?? null;
  });
  protected readonly childrenOf = (parentId: number) => this.categories().filter((c) => c.parentId === parentId);
  protected readonly heading = computed(() => {
    if (this.q()) {
      return this.i18n.t('Results for “{q}”', { q: this.q() });
    }
    if (this.onSale() === 'true' && !this.selectedCategory()) {
      return this.i18n.t('Deals');
    }
    return this.selectedCategory()?.name ?? this.i18n.t('All products');
  });

  constructor() {
    effect(() => {
      this.minPriceInput = this.minPrice() ?? '';
      this.maxPriceInput = this.maxPrice() ?? '';
      this.load();
    });
  }

  /** A parent category counts its own products and its sub-categories'. */
  protected countFor(categoryId: number): number {
    const own = this.facetById().get(categoryId) ?? 0;
    return own + this.childrenOf(categoryId).reduce((sum, child) => sum + (this.facetById().get(child.id) ?? 0), 0);
  }

  protected setFilter(changes: Record<string, string | number | boolean | null>): void {
    void this.router.navigate([], {
      queryParams: { ...changes, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected applyPrice(): void {
    this.setFilter({ minPrice: this.minPriceInput || null, maxPrice: this.maxPriceInput || null });
  }

  protected clearFilters(): void {
    void this.router.navigate([], { queryParams: { q: this.q() || null } });
  }

  protected changePage(event: PageEvent): void {
    void this.router.navigate([], { queryParams: { page: event.pageIndex || null }, queryParamsHandling: 'merge' });
  }

  private load(): void {
    this.loading.set(true);
    const query: ProductQuery = {
      q: this.q(),
      categoryId: this.num(this.categoryId()),
      sort: this.currentSort(),
      minPrice: this.num(this.minPrice()),
      maxPrice: this.num(this.maxPrice()),
      minRating: this.num(this.minRating()),
      inStock: this.inStock() === 'true' || undefined,
      onSale: this.onSale() === 'true' || undefined,
      page: this.num(this.page()) ?? 0,
      size: PAGE_SIZE,
    };
    this.catalog.facets(query).subscribe({ next: (f) => this.facets.set(f), error: () => this.facets.set(null) });
    const words = (this.q() ?? '').trim().split(/\s+/).filter(Boolean);
    if (words.length >= 3 && !query.page) {
      this.catalog.smartSearch(this.q()!, 4).subscribe({
        next: (r) => this.aiPicks.set(r.strategy === 'semantic' ? r.products : []),
        error: () => this.aiPicks.set([]),
      });
    } else {
      this.aiPicks.set([]);
    }
    this.catalog
      .search(query)
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private num(value: string | undefined): number | undefined {
    return value === undefined || value === '' || Number.isNaN(Number(value)) ? undefined : Number(value);
  }

  protected readonly pageSize = PAGE_SIZE;
}
