import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role } from '../models/api.models';
import { Notifier } from '../ui/notifier.service';
import { WishlistService } from './wishlist.service';

describe('WishlistService', () => {
  const role = signal<Role | null>(null);
  const notifier = { error: vi.fn(), success: vi.fn() };
  let service: WishlistService;
  let backend: HttpTestingController;

  beforeEach(() => {
    role.set(null);
    notifier.error.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { role } },
        { provide: Notifier, useValue: notifier },
      ],
    });
    service = TestBed.inject(WishlistService);
    backend = TestBed.inject(HttpTestingController);
  });

  it('loads saved ids for customers and toggles optimistically', () => {
    role.set('CUSTOMER');
    TestBed.tick();
    backend.expectOne('/api/wishlist/ids').flush([4]);
    expect(service.has(4)).toBe(true);

    expect(service.toggle(7)).toBe(true);
    expect(service.has(7)).toBe(true);
    backend.expectOne({ method: 'PUT', url: '/api/wishlist/7' }).flush(null);

    expect(service.toggle(4)).toBe(false);
    backend.expectOne({ method: 'DELETE', url: '/api/wishlist/4' }).flush(null);
    expect(service.count()).toBe(1);
  });

  it('rolls back when the server refuses', () => {
    role.set('CUSTOMER');
    TestBed.tick();
    backend.expectOne('/api/wishlist/ids').flush([]);
    service.toggle(9);
    backend.expectOne('/api/wishlist/9').flush({ detail: 'nope' }, { status: 404, statusText: 'Not Found' });
    expect(service.has(9)).toBe(false);
  });

  it('sends visitors to sign in and refuses staff accounts', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    expect(service.toggle(1)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());

    role.set('SELLER');
    TestBed.tick();
    expect(service.toggle(1)).toBeNull();
    expect(notifier.error).toHaveBeenCalled();
    backend.expectNone('/api/wishlist/1');
  });
});
