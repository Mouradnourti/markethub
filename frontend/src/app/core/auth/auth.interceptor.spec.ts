import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { TokenStorage } from './token-storage';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let tokens: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenStorage);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API calls only', () => {
    tokens.save('access-1', 'refresh-1');

    http.get('/api/cart').subscribe();
    http.get('/assets/logo.svg').subscribe();

    expect(backend.expectOne('/api/cart').request.headers.get('Authorization')).toBe('Bearer access-1');
    expect(backend.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
  });

  it('never sends the token to the login endpoint', () => {
    tokens.save('access-1', 'refresh-1');
    http.post('/api/auth/login', {}).subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes the token once on 401 and replays the request', () => {
    tokens.save('expired', 'refresh-1');
    let body: unknown;
    http.get('/api/orders').subscribe((response) => (body = response));

    backend.expectOne('/api/orders').flush(null, { status: 401, statusText: 'Unauthorized' });
    const refresh = backend.expectOne('/api/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh.flush({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 900, user: { role: 'CUSTOMER' } });

    const replay = backend.expectOne('/api/orders');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer access-2');
    replay.flush({ ok: true });
    expect(body).toEqual({ ok: true });
    expect(tokens.refreshToken).toBe('refresh-2');
  });

  it('logs out and redirects to login when the refresh fails', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tokens.save('expired', 'stale');
    let failed = false;
    http.get('/api/orders').subscribe({ error: () => (failed = true) });

    backend.expectOne('/api/orders').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.match('/api/auth/logout').forEach((request) => request.flush(null));

    expect(failed).toBe(true);
    expect(tokens.accessToken).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });
});
