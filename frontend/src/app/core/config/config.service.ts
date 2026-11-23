import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of, tap } from 'rxjs';
import { PublicConfig } from '../models/api.models';

const DEFAULT_CONFIG: PublicConfig = {
  currency: 'eur',
  stripeEnabled: false,
  shippingFlatFee: 4.99,
  freeShippingThreshold: 50,
};

/** Public configuration from GET /api/config, loaded once at startup. */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);
  private readonly state = signal<PublicConfig>(DEFAULT_CONFIG);
  readonly config = this.state.asReadonly();

  load(): Promise<unknown> {
    return firstValueFrom(
      this.http.get<PublicConfig>('/api/config').pipe(
        tap((config) => this.state.set(config)),
        catchError(() => of(DEFAULT_CONFIG)),
      ),
    );
  }

  get currencyCode(): string {
    return this.state().currency.toUpperCase();
  }
}
