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
  shippingFee: 4.99,
  total: 24.99,
};

describe('CartService', () => {
  const role = signal<Role | null>(null);
  let service: CartService;
  let backend: HttpTestingController;

  beforeEach(() => {
    role.set(null);
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
});
