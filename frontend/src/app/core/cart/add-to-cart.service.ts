import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, concat, last, map, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Notifier } from '../ui/notifier.service';
import { CartService } from './cart.service';

/**
 * The one "Add to cart" behaviour used by every button: sends visitors to sign in, refuses staff accounts,
 * and opens the cart drawer on success.
 */
@Injectable({ providedIn: 'root' })
export class AddToCartService {
  private readonly cart = inject(CartService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);

  /** Emits once when the products are in the cart; completes without emitting when the visitor cannot buy. */
  add(productIds: number | number[], quantity = 1, options: { openDrawer?: boolean } = {}): Observable<void> {
    const ids = Array.isArray(productIds) ? productIds : [productIds];
    if (!ids.length || !this.canBuy()) {
      return EMPTY;
    }
    return concat(...ids.map((id) => this.cart.add(id, quantity))).pipe(
      last(),
      tap(() => {
        if (options.openDrawer !== false) {
          this.cart.openDrawer(ids[0]);
        }
      }),
      map(() => undefined),
    );
  }

  private canBuy(): boolean {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return false;
    }
    if (this.auth.role() !== 'CUSTOMER') {
      this.notifier.error('Only customer accounts can shop. Sign in as a customer to buy.');
      return false;
    }
    return true;
  }
}
