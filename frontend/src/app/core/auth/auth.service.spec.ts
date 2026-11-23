import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

const USER = { id: 1, email: 'a@b.c', firstName: 'Ada', lastName: 'L', role: 'SELLER', enabled: true, createdAt: '' };

describe('AuthService', () => {
  let service: AuthService;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  it('stores tokens and exposes the user after login', () => {
    service.login('a@b.c', 'secret').subscribe();
    backend.expectOne('/api/auth/login').flush({ accessToken: 'at', refreshToken: 'rt', expiresIn: 900, user: USER });

    expect(service.isLoggedIn()).toBe(true);
    expect(service.role()).toBe('SELLER');
    expect(TestBed.inject(TokenStorage).accessToken).toBe('at');
    expect(service.homeFor(service.role())).toBe('/seller');
  });

  it('restores the session from storage at startup', async () => {
    TestBed.inject(TokenStorage).save('at', 'rt');
    const init = service.init();
    backend.expectOne('/api/auth/me').flush(USER);
    await init;
    expect(service.user()?.email).toBe('a@b.c');
  });

  it('drops an invalid stored session without failing startup', async () => {
    TestBed.inject(TokenStorage).save('bad', 'bad');
    const init = service.init();
    backend.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await init;
    expect(service.isLoggedIn()).toBe(false);
    expect(TestBed.inject(TokenStorage).accessToken).toBeNull();
  });

  it('shares one refresh request between concurrent callers', () => {
    TestBed.inject(TokenStorage).save('at', 'rt');
    const results: string[] = [];
    service.refreshAccessToken().subscribe((t) => results.push(t));
    service.refreshAccessToken().subscribe((t) => results.push(t));

    backend.expectOne('/api/auth/refresh').flush({ accessToken: 'new', refreshToken: 'rt2', expiresIn: 900, user: USER });
    expect(results).toEqual(['new', 'new']);
  });

  it('clears the session on logout', () => {
    TestBed.inject(TokenStorage).save('at', 'rt');
    service.logout(false);
    backend.expectOne('/api/auth/logout').flush(null);
    expect(service.isLoggedIn()).toBe(false);
    expect(TestBed.inject(TokenStorage).refreshToken).toBeNull();
  });
});
