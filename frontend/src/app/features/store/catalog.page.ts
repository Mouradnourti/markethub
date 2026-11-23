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
import { Page, ProductSort, ProductSummary } from '../../core/models/api.models';
import { EmptyState } from '../../shared/ui/empty-state';
import { ProductCard } from '../../shared/ui/product-card';

const PAGE_SIZE = 12;

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
  readonly page = input<string>();

  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  protected readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });
  protected readonly result = signal<Page<ProductSummary> | null>(null);
  protected readonly loading = signal(false);

  protected minPriceInput = '';
  protected maxPriceInput = '';

  protected readonly sortOptions: { value: ProductSort; label: string }[] = [
    { value: 'NEWEST', label: 'Newest' },
    { value: 'POPULAR', label: 'Best sellers' },
    { value: 'PRICE_ASC', label: 'Price: low to high' },
    { value: 'PRICE_DESC', label: 'Price: high to low' },
    { value: 'RATING', label: 'Top rated' },
  ];

  protected readonly parents = computed(() => this.categories().filter((c) => !c.parentId));
  protected readonly selectedCategory = computed(() => {
    const id = Number(this.categoryId());
    return this.categories().find((c) => c.id === id) ?? null;
  });
  protected readonly childrenOf = (parentId: number) => this.categories().filter((c) => c.parentId === parentId);
  protected readonly heading = computed(() => {
    if (this.q()) {
      return `Results for “${this.q()}”`;
    }
    return this.selectedCategory()?.name ?? 'All products';
  });

  constructor() {
    effect(() => {
      this.minPriceInput = this.minPrice() ?? '';
      this.maxPriceInput = this.maxPrice() ?? '';
      this.load();
    });
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
    this.catalog
      .search({
        q: this.q(),
        categoryId: this.num(this.categoryId()),
        sort: (this.sort() as ProductSort) || 'NEWEST',
        minPrice: this.num(this.minPrice()),
        maxPrice: this.num(this.maxPrice()),
        minRating: this.num(this.minRating()),
        inStock: this.inStock() === 'true' || undefined,
        page: this.num(this.page()) ?? 0,
        size: PAGE_SIZE,
      })
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
