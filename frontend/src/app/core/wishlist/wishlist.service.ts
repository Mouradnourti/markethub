import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { SILENT_ERRORS } from '../http/error.interceptor';
import { I18nService } from '../i18n/i18n.service';
import { WishlistItem } from '../models/api.models';
import { Notifier } from '../ui/notifier.service';

/** Saved products of the signed-in customer. Hearts update instantly and roll back if the server refuses. */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly i18n = inject(I18nService);
  private readonly ids = signal<ReadonlySet<number>>(new Set());

  readonly count = computed(() => this.ids().size);

  constructor() {
    effect(() => {
      if (this.auth.role() === 'CUSTOMER') {
        this.http
          .get<number[]>('/api/wishlist/ids', { context: new HttpContext().set(SILENT_ERRORS, true) })
          .subscribe({ next: (ids) => this.ids.set(new Set(ids)), error: () => undefined });
      } else {
        this.ids.set(new Set());
      }
    });
  }

  has(productId: number): boolean {
    return this.ids().has(productId);
  }

  list(): Observable<WishlistItem[]> {
    return this.http.get<WishlistItem[]>('/api/wishlist');
  }

  /** Returns the new state, or null when the shopper first has to sign in. */
  toggle(productId: number): boolean | null {
    const role = this.auth.role();
    if (!role) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return null;
    }
    if (role !== 'CUSTOMER') {
      this.notifier.error(this.i18n.t('Wishlists are for customer accounts.'));
      return null;
    }
    const saved = !this.has(productId);
    this.set(productId, saved);
    const request = saved
      ? this.http.put<void>(`/api/wishlist/${productId}`, null)
      : this.http.delete<void>(`/api/wishlist/${productId}`);
    request.subscribe({ error: () => this.set(productId, !saved) });
    return saved;
  }

  private set(productId: number, saved: boolean): void {
    this.ids.update((current) => {
      const next = new Set(current);
      if (saved) {
        next.add(productId);
      } else {
        next.delete(productId);
      }
      return next;
    });
  }
}
