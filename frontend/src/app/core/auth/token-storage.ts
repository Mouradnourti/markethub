import { Injectable } from '@angular/core';

const ACCESS_KEY = 'mh.accessToken';
const REFRESH_KEY = 'mh.refreshToken';

/**
 * Persists the token pair in localStorage so a page reload keeps the session.
 * Trade-off: simpler than httpOnly cookies for a SPA + separate API; XSS exposure is mitigated by
 * Angular's template sanitization and the 15-minute access-token lifetime.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  get accessToken(): string | null {
    return this.read(ACCESS_KEY);
  }

  get refreshToken(): string | null {
    return this.read(REFRESH_KEY);
  }

  save(accessToken: string, refreshToken: string): void {
    try {
      localStorage.setItem(ACCESS_KEY, accessToken);
      localStorage.setItem(REFRESH_KEY, refreshToken);
    } catch {
      /* storage unavailable (private mode): session lasts until reload */
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      /* ignore */
    }
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
}
