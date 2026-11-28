import { Clipboard } from '@angular/cdk/clipboard';
import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { AddToCartService } from '../../core/cart/add-to-cart.service';
import { ConfigService } from '../../core/config/config.service';
import { RecentlyViewedService } from '../../core/history/recently-viewed.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Offer, Product, ProductSummary } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { deliveryWindow } from '../../shared/delivery';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { Price, discountPercent } from '../../shared/ui/price';
import { BESTSELLER_THRESHOLD, ProductCard } from '../../shared/ui/product-card';
import { RatingStars } from '../../shared/ui/rating-stars';
import { WishlistButton } from '../../shared/ui/wishlist-button';
import { ProductReviews } from './product-reviews';

@Component({
  selector: 'app-product-page',
  imports: [
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    Price,
    RatingStars,
    ProductCard,
    ProductReviews,
    WishlistButton,
    TranslatePipe,
  ],
  templateUrl: './product.page.html',
  styleUrl: './product.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPage {
  readonly id = input.required<string>();
  private readonly catalog = inject(CatalogApi);
  private readonly addToCartService = inject(AddToCartService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly history = inject(RecentlyViewedService);
  protected readonly config = inject(ConfigService);
  protected readonly auth = inject(AuthService);
  private readonly clipboard = inject(Clipboard);
  private readonly i18n = inject(I18nService);

  protected readonly product = signal<Product | null>(null);
  protected readonly notFound = signal(false);
  protected readonly activeImage = signal(0);
  protected readonly quantity = signal(1);
  protected readonly adding = signal(false);
  protected readonly similar = signal<ProductSummary[]>([]);
  protected readonly together = signal<ProductSummary[]>([]);
  /** Ids of the "frequently bought together" products the shopper kept ticked. */
  protected readonly bundleSelection = signal<Set<number>>(new Set());
  protected readonly bundleAdding = signal(false);
  /** Featured discount codes of the seller, shown under the price. */
  protected readonly offers = signal<Offer[]>([]);
  /** True once the main buy button has scrolled out of view, to show the sticky buy bar. */
  protected readonly showStickyBar = signal(false);
  protected readonly delivery = deliveryWindow();

  private readonly buyBox = viewChild<ElementRef<HTMLElement>>('buyBox');

  protected readonly image = computed(() => this.product()?.images[this.activeImage()] ?? null);
  protected readonly maxQuantity = computed(() => Math.min(this.product()?.stock ?? 0, 10));
  protected readonly discount = computed(() => {
    const p = this.product();
    return p ? discountPercent(p.price, p.compareAtPrice) : 0;
  });
  protected readonly savings = computed(() => {
    const p = this.product();
    return p?.compareAtPrice ? p.compareAtPrice - p.price : 0;
  });
  protected readonly bestseller = computed(() => (this.product()?.soldCount ?? 0) >= BESTSELLER_THRESHOLD);
  protected readonly paragraphs = computed(() =>
    (this.product()?.description ?? '').split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean),
  );
  protected readonly bundleItems = computed(() => this.together().filter((p) => this.bundleSelection().has(p.id)));
  protected readonly bundleTotal = computed(
    () => (this.product()?.price ?? 0) + this.bundleItems().reduce((sum, p) => sum + p.price, 0),
  );
  protected readonly recentlyViewed = computed(() => this.history.others(this.product()?.id).slice(0, 4));

  constructor() {
    effect(() => this.load(Number(this.id())));

    effect((onCleanup) => {
      const element = this.buyBox()?.nativeElement;
      if (!element || typeof IntersectionObserver === 'undefined') {
        return;
      }
      const observer = new IntersectionObserver(([entry]) =>
        this.showStickyBar.set(!entry.isIntersecting && entry.boundingClientRect.top < 0),
      );
      observer.observe(element);
      onCleanup(() => observer.disconnect());
    });
    inject(DestroyRef).onDestroy(() => this.showStickyBar.set(false));
  }

  protected changeQuantity(delta: number): void {
    this.quantity.update((q) => Math.min(Math.max(1, q + delta), Math.max(1, this.maxQuantity())));
  }

  protected addToCart(buyNow = false): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.adding.set(true);
    this.addToCartService.add(product.id, this.quantity(), { openDrawer: !buyNow }).subscribe({
      next: () => {
        if (buyNow) {
          void this.router.navigateByUrl('/checkout');
        }
      },
      error: () => this.adding.set(false),
      complete: () => this.adding.set(false),
    });
  }

  protected toggleBundle(id: number, checked: boolean): void {
    this.bundleSelection.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  protected addBundle(): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.bundleAdding.set(true);
    this.addToCartService.add([product.id, ...this.bundleItems().map((p) => p.id)]).subscribe({
      error: () => this.bundleAdding.set(false),
      complete: () => this.bundleAdding.set(false),
    });
  }

  protected copyCode(code: string): void {
    this.clipboard.copy(code);
    this.notifier.success(this.i18n.t('Code {code} copied. Paste it in your cart.', { code }));
  }

  private load(id: number): void {
    this.notFound.set(false);
    this.activeImage.set(0);
    this.quantity.set(1);
    this.catalog.product(id).subscribe({
      next: (product) => {
        this.product.set(product);
        this.history.track(product);
        this.catalog.offers(product.shopId).subscribe({ next: (offers) => this.offers.set(offers), error: () => undefined });
      },
      error: () => this.notFound.set(true),
    });
    this.catalog.similar(id, 4).subscribe((items) => this.similar.set(items));
    this.catalog
      .boughtTogether(id, 2)
      .pipe(catchError(() => of([])))
      .subscribe((items) => {
        const inStock = items.filter((p) => p.stock > 0);
        this.together.set(inStock);
        this.bundleSelection.set(new Set(inStock.map((p) => p.id)));
      });
  }
}
