import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { AuthResponse, RegisterRequest, Role, User } from '../models/api.models';
import { TokenStorage } from './token-storage';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokens = inject(TokenStorage);
  private refreshInFlight: Observable<string> | null = null;

  private readonly currentUser = signal<User | null>(null);
  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  readonly role = computed<Role | null>(() => this.currentUser()?.role ?? null);

  /** Restores the session at startup (called by the app initializer). Never fails. */
  init(): Promise<void> {
    if (!this.tokens.accessToken) {
      return Promise.resolve();
    }
    return firstValueFrom(
      this.http.get<User>('/api/auth/me').pipe(
        tap((user) => this.currentUser.set(user)),
        map(() => undefined),
        catchError(() => {
          this.tokens.clear();
          return of(undefined);
        }),
      ),
    );
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(map((response) => this.handleAuth(response)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>('/api/auth/register', request)
      .pipe(map((response) => this.handleAuth(response)));
  }

  logout(redirect = true): void {
    const refreshToken = this.tokens.refreshToken;
    if (refreshToken) {
      this.http.post('/api/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    this.tokens.clear();
    this.currentUser.set(null);
    if (redirect) {
      void this.router.navigateByUrl('/');
    }
  }

  get accessToken(): string | null {
    return this.tokens.accessToken;
  }

  /** Rotates the refresh token. Concurrent callers share a single request. */
  refreshAccessToken(): Observable<string> {
    const refreshToken = this.tokens.refreshToken;
    if (!refreshToken) {
      return new Observable((subscriber) => subscriber.error(new Error('No refresh token')));
    }
    this.refreshInFlight ??= this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken }).pipe(
      map((response) => {
        this.handleAuth(response);
        return response.accessToken;
      }),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  /** Where each role lands after login. */
  homeFor(role: Role | null): string {
    switch (role) {
      case 'SELLER':
        return '/seller';
      case 'ADMIN':
        return '/admin';
      default:
        return '/';
    }
  }

  private handleAuth(response: AuthResponse): User {
    this.tokens.save(response.accessToken, response.refreshToken);
    this.currentUser.set(response.user);
    return response.user;
  }
}
