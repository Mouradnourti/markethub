import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router, RouterStateSnapshot, UrlSegment, UrlTree, provideRouter } from '@angular/router';
import { Role } from '../models/api.models';
import { authGuard, guestGuard, roleGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  const role = signal<Role | null>(null);
  const fakeAuth = {
    isLoggedIn: () => role() !== null,
    role,
    homeFor: (r: Role | null) => (r === 'SELLER' ? '/seller' : r === 'ADMIN' ? '/admin' : '/'),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth }],
    });
  });

  const runRole = (required: Role) =>
    TestBed.runInInjectionContext(() => roleGuard(required)({} as Route, [new UrlSegment('seller', {})], {} as never));
  const serialize = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('lets the matching role through', () => {
    role.set('SELLER');
    expect(runRole('SELLER')).toBe(true);
  });

  it('sends other roles to their own home', () => {
    role.set('CUSTOMER');
    expect(serialize(runRole('ADMIN'))).toBe('/');
    role.set('SELLER');
    expect(serialize(runRole('ADMIN'))).toBe('/seller');
  });

  it('sends anonymous visitors to login with a return URL', () => {
    role.set(null);
    expect(serialize(runRole('SELLER'))).toBe('/login?returnUrl=%2Fseller');
  });

  it('authGuard and guestGuard are mirror images', () => {
    const state = { url: '/orders' } as RouterStateSnapshot;
    const route = {} as ActivatedRouteSnapshot;
    role.set(null);
    expect(serialize(TestBed.runInInjectionContext(() => authGuard(route, state)))).toBe('/login?returnUrl=%2Forders');
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state))).toBe(true);
    role.set('ADMIN');
    expect(TestBed.runInInjectionContext(() => authGuard(route, state))).toBe(true);
    expect(serialize(TestBed.runInInjectionContext(() => guestGuard(route, state)))).toBe('/admin');
  });
});
