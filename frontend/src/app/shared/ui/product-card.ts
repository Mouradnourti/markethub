import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { ProductSummary } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../pipes/money.pipe';
import { RatingStars } from './rating-stars';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, MatButtonModule, MatIconModule, MoneyPipe, RatingStars],
  template: `
    @let p = product();
    <article class="card">
      <a class="media" [routerLink]="['/products', p.id]">
        @if (p.imageUrl) {
          <img [src]="p.imageUrl" [alt]="p.name" loading="lazy" />
        } @else {
          <div class="placeholder"><mat-icon>image</mat-icon></div>
        }
        @if (p.stock === 0) {
          <span class="badge sold-out">Sold out</span>
        } @else if (p.stock < 5) {
          <span class="badge low">Only {{ p.stock }} left</span>
        }
        @if (tag()) {
          <span class="badge tag">{{ tag() }}</span>
        }
      </a>
      <div class="body">
        <span class="shop">{{ p.shopName }}</span>
        <a class="name" [routerLink]="['/products', p.id]">{{ p.name }}</a>
        <app-rating-stars [value]="p.avgRating" [count]="p.reviewCount" />
        <div class="footer">
          <span class="price">{{ p.price | money }}</span>
          <button mat-icon-button class="add" [disabled]="p.stock === 0 || adding()" (click)="addToCart()"
                  aria-label="Add to cart">
            <mat-icon>add_shopping_cart</mat-icon>
          </button>
        </div>
      </div>
    </article>
  `,
  styleUrl: './product-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCard {
  readonly product = input.required<ProductSummary>();
  readonly tag = input<string>();
  protected readonly adding = signal(false);
  private readonly cart = inject(CartService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);

  protected addToCart(): void {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.auth.role() !== 'CUSTOMER') {
      this.notifier.error('Only customer accounts can shop. Sign in as a customer to buy.');
      return;
    }
    this.adding.set(true);
    this.cart.add(this.product().id).subscribe({
      next: () => this.notifier.success('Added to your cart'),
      error: () => this.adding.set(false),
      complete: () => this.adding.set(false),
    });
  }
}
