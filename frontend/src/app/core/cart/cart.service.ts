import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { SILENT_ERRORS, problemMessage } from '../http/error.interceptor';
import { Cart } from '../models/api.models';

const EMPTY_CART: Cart = { items: [], itemCount: 0, subtotal: 0, discount: 0, shippingFee: 0, total: 0 };
const COUPON_KEY = 'mh.coupon';

function storedCoupon(): string | null {
  try {
    return sessionStorage.getItem(COUPON_KEY);
  } catch {
    return null;
  }
}

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
  /** Discount code kept for the session and re-applied after every cart change. */
  readonly couponCode = signal<string | null>(storedCoupon());
  /** Why the code stopped applying (e.g. its shop's items were removed). */
  readonly couponError = signal<string | null>(null);

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

  /** Checks the code against the cart; the observable errors with the reason when it does not apply. */
  applyCoupon(code: string): Observable<Cart> {
    const normalized = code.trim().toUpperCase();
    return this.fetchWithCoupon(normalized).pipe(
      tap(() => {
        this.saveCoupon(normalized);
        this.couponError.set(null);
      }),
    );
  }

  removeCoupon(): Observable<Cart> {
    this.saveCoupon(null);
    this.couponError.set(null);
    return this.refresh();
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

  /** Called after checkout: the backend already emptied the cart and used the code. */
  reset(): void {
    this.state.set(EMPTY_CART);
    this.saveCoupon(null);
  }

  private track(request: Observable<Cart>): Observable<Cart> {
    return request.pipe(
      switchMap((cart) => {
        const code = this.couponCode();
        if (!code || cart.items.length === 0) {
          this.state.set(cart);
          return of(cart);
        }
        return this.fetchWithCoupon(code).pipe(
          catchError((error: unknown) => {
            this.saveCoupon(null);
            this.couponError.set(problemMessage(error));
            this.state.set(cart);
            return of(cart);
          }),
        );
      }),
    );
  }

  private fetchWithCoupon(code: string): Observable<Cart> {
    return this.http
      .get<Cart>('/api/cart', { params: { coupon: code }, context: new HttpContext().set(SILENT_ERRORS, true) })
      .pipe(tap((cart) => this.state.set(cart)));
  }

  private saveCoupon(code: string | null): void {
    this.couponCode.set(code);
    try {
      if (code) {
        sessionStorage.setItem(COUPON_KEY, code);
      } else {
        sessionStorage.removeItem(COUPON_KEY);
      }
    } catch {
      // Storage blocked: the code still applies until the page is reloaded.
    }
  }
}
