import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AddToCartService } from '../../core/cart/add-to-cart.service';
import { ProductSummary } from '../../core/models/api.models';
import { Price, discountPercent } from './price';
import { RatingStars } from './rating-stars';

/** Units sold from which a product is labelled a best seller. */
export const BESTSELLER_THRESHOLD = 60;

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, MatIconModule, Price, RatingStars],
  template: `
    @let p = product();
    <article class="card" [class.sold-out]="p.stock === 0">
      <div class="media">
        <a [routerLink]="['/products', p.id]" [attr.aria-label]="p.name" class="media-link">
          @if (p.imageUrl) {
            <img class="primary" [src]="p.imageUrl" [alt]="p.name" loading="lazy" />
            @if (p.hoverImageUrl) {
              <img class="secondary" [src]="p.hoverImageUrl" alt="" loading="lazy" aria-hidden="true" />
            }
          } @else {
            <div class="placeholder"><mat-icon>image</mat-icon></div>
          }
        </a>
        <div class="badges">
          @if (p.stock === 0) {
            <span class="pill dark">Sold out</span>
          } @else if (discount()) {
            <span class="pill sale">-{{ discount() }}%</span>
          }
          @if (tag()) {
            <span class="pill accent">{{ tag() }}</span>
          } @else if (p.soldCount >= bestsellerThreshold) {
            <span class="pill">Best seller</span>
          }
        </div>
        @if (p.stock > 0) {
          <button type="button" class="quick-add" (click)="addToCart()" [disabled]="adding()"
                  [attr.aria-label]="'Add ' + p.name + ' to cart'">
            <mat-icon>add_shopping_cart</mat-icon><span>{{ adding() ? 'Adding…' : 'Add to cart' }}</span>
          </button>
        }
      </div>
      <div class="body">
        <span class="shop">{{ p.shopName }}</span>
        <a class="name" [routerLink]="['/products', p.id]">{{ p.name }}</a>
        @if (p.reviewCount > 0) {
          <app-rating-stars [value]="p.avgRating" [count]="p.reviewCount" />
        }
        <app-price [price]="p.price" [compareAtPrice]="p.compareAtPrice" />
        @if (p.stock > 0 && p.stock < 5) {
          <span class="urgency"><mat-icon>local_fire_department</mat-icon>Only {{ p.stock }} left</span>
        }
      </div>
    </article>
  `,
  styleUrl: './product-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCard {
  readonly product = input.required<ProductSummary>();
  readonly tag = input<string>();
  protected readonly bestsellerThreshold = BESTSELLER_THRESHOLD;
  protected readonly adding = signal(false);
  protected readonly discount = computed(() => discountPercent(this.product().price, this.product().compareAtPrice));
  private readonly addToCartService = inject(AddToCartService);

  protected addToCart(): void {
    this.adding.set(true);
    this.addToCartService.add(this.product().id).subscribe({
      error: () => this.adding.set(false),
      complete: () => this.adding.set(false),
    });
  }
}
