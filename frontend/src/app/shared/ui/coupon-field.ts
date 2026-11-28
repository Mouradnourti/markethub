import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { CartService } from '../../core/cart/cart.service';
import { problemMessage } from '../../core/http/error.interceptor';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { MoneyPipe } from '../pipes/money.pipe';

/** "Discount code" box of the cart and checkout. Refusals are shown inline with the reason from the server. */
@Component({
  selector: 'app-coupon-field',
  imports: [FormsModule, MatIconModule, MoneyPipe, TranslatePipe],
  template: `
    @if (cart.cart().coupon; as coupon) {
      <div class="applied">
        <mat-icon>sell</mat-icon>
        <div>
          <strong>{{ coupon.code }}</strong>
          <span>{{ '{label} on {shop}' | t: { label: coupon.label, shop: coupon.shopName } }} · −{{ coupon.discount | money }}</span>
        </div>
        <button type="button" (click)="remove()" [attr.aria-label]="'Remove code {code}' | t: { code: coupon.code }">
          <mat-icon>close</mat-icon>
        </button>
      </div>
    } @else {
      <form class="field" (ngSubmit)="apply()">
        <label class="sr-only" for="coupon">{{ 'Discount code' | t }}</label>
        <input id="coupon" name="coupon" [(ngModel)]="code" [placeholder]="'Discount code' | t" autocomplete="off"
               maxlength="30" [attr.aria-invalid]="!!error()" [attr.aria-describedby]="error() ? 'coupon-error' : null" />
        <button type="submit" [disabled]="busy() || !code.trim()">{{ 'Apply' | t }}</button>
      </form>
      @if (error() || cart.couponError(); as message) {
        <p class="error" id="coupon-error" role="alert"><mat-icon>error</mat-icon> {{ message }}</p>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .field { display: flex; gap: 8px; }
    input { flex: 1; min-width: 0; height: 44px; padding: 0 14px; border: 1.5px solid var(--mh-border); border-radius: 12px;
      background: var(--mh-surface); font: inherit; text-transform: uppercase; letter-spacing: .04em; color: var(--mh-text); }
    input::placeholder { text-transform: none; letter-spacing: 0; }
    input:focus { border-color: var(--mh-ink); outline: none; }
    button[type='submit'] { height: 44px; padding: 0 18px; border: 0; border-radius: 12px; background: var(--mh-ink); color: #fff;
      font: 600 14px var(--mh-font); cursor: pointer; }
    button[type='submit']:disabled { opacity: .4; cursor: default; }
    .applied { display: flex; gap: 10px; align-items: center; padding: 10px 12px; border: 1px dashed var(--mh-success);
      border-radius: 12px; background: var(--mh-success-bg); color: var(--mh-success); }
    .applied div { display: flex; flex: 1; flex-direction: column; font-size: 13px; }
    .applied strong { letter-spacing: .06em; }
    .applied button { display: grid; place-items: center; width: 32px; height: 32px; border: 0; border-radius: 50%;
      background: transparent; color: inherit; cursor: pointer; }
    .error { display: flex; gap: 6px; align-items: center; margin: 8px 0 0; color: var(--mh-danger); font-size: 13px; }
    .error mat-icon { width: 18px; height: 18px; font-size: 18px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CouponField {
  protected readonly cart = inject(CartService);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected code = '';

  protected apply(): void {
    this.busy.set(true);
    this.error.set(null);
    this.cart.applyCoupon(this.code).subscribe({
      next: () => {
        this.busy.set(false);
        this.code = '';
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }

  protected remove(): void {
    this.cart.removeCoupon().subscribe({ error: () => undefined });
  }
}
