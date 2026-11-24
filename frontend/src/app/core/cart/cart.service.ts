import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Cart } from '../models/api.models';

const EMPTY_CART: Cart = { items: [], itemCount: 0, subtotal: 0, shippingFee: 0, total: 0 };

/** Server-side cart (persists across devices) exposed as a signal for the header badge and cart page. */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly state = signal<Cart>(EMPTY_CART);

  readonly cart = this.state.asReadonly();
  readonly count = computed(() => this.state().itemCount);
  readonly isEmpty = computed(() => this.state().items.length === 0);

  /** Slide-over cart shown after "Add to cart", so shoppers see the result and the way to checkout. */
  readonly drawerOpen = signal(false);
  /** Product just added, used to suggest complementary items in the drawer. */
  readonly lastAddedId = signal<number | null>(null);

  constructor() {
    effect(() => {
      if (this.auth.role() === 'CUSTOMER') {
        this.refresh().subscribe({ error: () => undefined });
      } else {
        this.state.set(EMPTY_CART);
      }
    });
  }

  refresh(): Observable<Cart> {
    return this.track(this.http.get<Cart>('/api/cart'));
  }

  add(productId: number, quantity = 1): Observable<Cart> {
    return this.track(this.http.post<Cart>('/api/cart/items', { productId, quantity }));
  }

  update(productId: number, quantity: number): Observable<Cart> {
    return this.track(this.http.put<Cart>(`/api/cart/items/${productId}`, { quantity }));
  }

  remove(productId: number): Observable<Cart> {
    return this.track(this.http.delete<Cart>(`/api/cart/items/${productId}`));
  }

  openDrawer(addedProductId?: number): void {
    if (addedProductId !== undefined) {
      this.lastAddedId.set(addedProductId);
    }
    this.drawerOpen.set(true);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  /** Called after checkout: the backend already emptied the cart. */
  reset(): void {
    this.state.set(EMPTY_CART);
  }

  private track(request: Observable<Cart>): Observable<Cart> {
    return request.pipe(tap((cart) => this.state.set(cart)));
  }
}
