import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { RecentlyViewedService } from '../history/recently-viewed.service';
import { Cart, Product, Role } from '../models/api.models';
import { Notifier } from '../ui/notifier.service';
import { AddToCartService } from './add-to-cart.service';
import { CartService } from './cart.service';

const CART: Cart = { items: [], itemCount: 1, subtotal: 10, discount: 0, shippingFee: 4.99, total: 14.99 };

describe('AddToCartService', () => {
  const role = signal<Role | null>('CUSTOMER');
  const navigate = vi.fn();
  const error = vi.fn();
  let service: AddToCartService;
  let cart: CartService;
  let backend: HttpTestingController;

  beforeEach(() => {
    role.set('CUSTOMER');
    navigate.mockReset();
    error.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { role, isLoggedIn: () => role() !== null } },
        { provide: Router, useValue: { navigate, url: '/products/7' } },
        { provide: Notifier, useValue: { error, success: vi.fn() } },
      ],
    });
    service = TestBed.inject(AddToCartService);
    cart = TestBed.inject(CartService);
    backend = TestBed.inject(HttpTestingController);
    TestBed.tick();
    backend.match('/api/cart').forEach((r) => r.flush(CART));
  });

  it('adds the product and opens the drawer on the product just added', () => {
    let done = false;
    service.add(7, 2).subscribe(() => (done = true));
    const request = backend.expectOne('/api/cart/items');
    expect(request.request.body).toEqual({ productId: 7, quantity: 2 });
    request.flush(CART);
    expect(done).toBe(true);
    expect(cart.drawerOpen()).toBe(true);
    expect(cart.lastAddedId()).toBe(7);
  });

  it('adds a bundle one product after another', () => {
    service.add([1, 2]).subscribe();
    backend.expectOne((r) => r.url === '/api/cart/items' && r.body.productId === 1).flush(CART);
    backend.expectOne((r) => r.url === '/api/cart/items' && r.body.productId === 2).flush(CART);
    expect(cart.drawerOpen()).toBe(true);
    expect(cart.lastAddedId()).toBe(1);
  });

  it('can add without opening the drawer', () => {
    service.add(3, 1, { openDrawer: false }).subscribe();
    backend.expectOne('/api/cart/items').flush(CART);
    expect(cart.drawerOpen()).toBe(false);
  });

  it('sends visitors to sign in and comes back afterwards', () => {
    role.set(null);
    service.add(7).subscribe();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/products/7' } });
    backend.expectNone('/api/cart/items');
  });

  it('refuses staff accounts', () => {
    role.set('SELLER');
    service.add(7).subscribe();
    expect(error).toHaveBeenCalled();
    backend.expectNone('/api/cart/items');
  });
});

describe('RecentlyViewedService', () => {
  const product = (id: number): Product => ({
    id,
    name: `P${id}`,
    highlights: [],
    specs: [],
    price: 10,
    stock: 3,
    active: true,
    avgRating: 4,
    reviewCount: 1,
    soldCount: 2,
    categoryId: 1,
    categoryName: 'C',
    shopId: 1,
    shopName: 'S',
    shopSlug: 's',
    images: ['/a.svg', '/b.svg'],
    createdAt: '2026-01-01',
  });

  beforeEach(() => localStorage.clear());

  it('keeps the latest views first, without duplicates, and survives a reload', () => {
    const history = new RecentlyViewedService();
    history.track(product(1));
    history.track(product(2));
    history.track(product(1));
    expect(history.items().map((p) => p.id)).toEqual([1, 2]);
    expect(history.items()[0].hoverImageUrl).toBe('/b.svg');
    expect(history.others(1).map((p) => p.id)).toEqual([2]);

    expect(new RecentlyViewedService().items().map((p) => p.id)).toEqual([1, 2]);
  });

  it('ignores corrupted storage', () => {
    localStorage.setItem('mh.recentlyViewed', '{not json');
    expect(new RecentlyViewedService().items()).toEqual([]);
  });
});
