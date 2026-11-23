import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { Product, ProductSummary, Review, ReviewEligibility } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ProductCard } from '../../shared/ui/product-card';
import { RatingStars } from '../../shared/ui/rating-stars';

@Component({
  selector: 'app-product-page',
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    RatingStars,
    ProductCard,
  ],
  templateUrl: './product.page.html',
  styleUrl: './product.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPage {
  readonly id = input.required<string>();
  private readonly catalog = inject(CatalogApi);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  protected readonly auth = inject(AuthService);

  protected readonly product = signal<Product | null>(null);
  protected readonly notFound = signal(false);
  protected readonly activeImage = signal(0);
  protected readonly quantity = signal(1);
  protected readonly adding = signal(false);
  protected readonly similar = signal<ProductSummary[]>([]);
  protected readonly reviews = signal<Review[]>([]);
  protected readonly reviewTotal = signal(0);
  protected readonly eligibility = signal<ReviewEligibility | null>(null);
  protected readonly newRating = signal(5);
  protected newComment = '';
  protected readonly submitting = signal(false);

  protected readonly image = computed(() => this.product()?.images[this.activeImage()] ?? null);
  protected readonly maxQuantity = computed(() => Math.min(this.product()?.stock ?? 0, 10));

  constructor() {
    effect(() => this.load(Number(this.id())));
  }

  protected changeQuantity(delta: number): void {
    this.quantity.update((q) => Math.min(Math.max(1, q + delta), Math.max(1, this.maxQuantity())));
  }

  protected addToCart(buyNow = false): void {
    const product = this.product();
    if (!product) {
      return;
    }
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.auth.role() !== 'CUSTOMER') {
      this.notifier.error('Only customer accounts can shop. Sign in as a customer to buy.');
      return;
    }
    this.adding.set(true);
    this.cart.add(product.id, this.quantity()).subscribe({
      next: () => {
        this.adding.set(false);
        if (buyNow) {
          void this.router.navigateByUrl('/cart');
        } else {
          this.notifier.success(`${this.quantity()} × ${product.name} added to your cart`);
        }
      },
      error: () => this.adding.set(false),
    });
  }

  protected submitReview(): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.submitting.set(true);
    this.catalog.addReview(product.id, this.newRating(), this.newComment.trim()).subscribe({
      next: () => {
        this.notifier.success('Thank you for your review!');
        this.newComment = '';
        this.submitting.set(false);
        this.load(product.id);
      },
      error: () => this.submitting.set(false),
    });
  }

  private load(id: number): void {
    this.notFound.set(false);
    this.activeImage.set(0);
    this.quantity.set(1);
    this.catalog.product(id).subscribe({
      next: (product) => this.product.set(product),
      error: () => this.notFound.set(true),
    });
    this.catalog.similar(id, 5).subscribe((items) => this.similar.set(items));
    this.catalog.reviews(id, 0, 6).subscribe((page) => {
      this.reviews.set(page.content);
      this.reviewTotal.set(page.totalElements);
    });
    if (this.auth.role() === 'CUSTOMER') {
      this.catalog.reviewEligibility(id).subscribe((e) => this.eligibility.set(e));
    }
  }
}
