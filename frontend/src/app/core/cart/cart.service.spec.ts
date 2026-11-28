import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { Cart, Role } from '../models/api.models';
import { CartService } from './cart.service';

const CART: Cart = {
  items: [{ productId: 3, productName: 'Lamp', shopId: 1, shopName: 'S', unitPrice: 10, quantity: 2, availableStock: 5, available: true, lineTotal: 20 }],
  itemCount: 2,
  subtotal: 20,
  discount: 0,
  shippingFee: 4.99,
  total: 24.99,
};

describe('CartService', () => {
  const role = signal<Role | null>(null);
  let service: CartService;
  let backend: HttpTestingController;

  beforeEach(() => {
    role.set(null);
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { role } }],
    });
    service = TestBed.inject(CartService);
    backend = TestBed.inject(HttpTestingController);
  });

  it('loads the cart when a customer signs in and empties it on sign-out', () => {
    role.set('CUSTOMER');
    TestBed.tick();
    backend.expectOne('/api/cart').flush(CART);
    expect(service.count()).toBe(2);

    role.set(null);
    TestBed.tick();
    expect(service.isEmpty()).toBe(true);
  });

  it('does not load a cart for sellers', () => {
    role.set('SELLER');
    TestBed.tick();
    backend.expectNone('/api/cart');
  });

  it('updates the shared state from every mutation', () => {
    service.add(3, 2).subscribe();
    const add = backend.expectOne('/api/cart/items');
    expect(add.request.body).toEqual({ productId: 3, quantity: 2 });
    add.flush(CART);
    expect(service.cart().total).toBe(24.99);

    service.remove(3).subscribe();
    backend.expectOne({ method: 'DELETE', url: '/api/cart/items/3' }).flush({ ...CART, items: [], itemCount: 0 });
    expect(service.count()).toBe(0);
  });

  it('applies a discount code, keeps it for the session and re-applies it after changes', () => {
    service.applyCoupon(' spring10 ').subscribe();
    const apply = backend.expectOne((r) => r.url === '/api/cart' && r.params.get('coupon') === 'SPRING10');
    apply.flush({ ...CART, discount: 2, total: 22.99 });
    expect(service.couponCode()).toBe('SPRING10');
    expect(sessionStorage.getItem('mh.coupon')).toBe('SPRING10');
    expect(service.cart().discount).toBe(2);

    service.update(3, 3).subscribe();
    backend.expectOne('/api/cart/items/3').flush(CART);
    backend.expectOne((r) => r.params.get('coupon') === 'SPRING10').flush({ ...CART, discount: 3, total: 21.99 });
    expect(service.cart().discount).toBe(3);
  });

  it('drops a code that stops applying and says why, keeping the cart', () => {
    service.couponCode.set('SPRING10');
    service.remove(3).subscribe();
    backend.expectOne({ method: 'DELETE', url: '/api/cart/items/3' }).flush(CART);
    backend
      .expectOne((r) => r.params.get('coupon') === 'SPRING10')
      .flush({ detail: 'The code SPRING10 only applies to products sold by Lamp Shop' }, { status: 422, statusText: 'Unprocessable' });
    expect(service.couponCode()).toBeNull();
    expect(service.couponError()).toContain('only applies');
    expect(service.cart().total).toBe(24.99);
  });

  it('forgets the code after checkout', () => {
    service.couponCode.set('SPRING10');
    service.reset();
    expect(service.couponCode()).toBeNull();
    expect(service.isEmpty()).toBe(true);
  });
});
