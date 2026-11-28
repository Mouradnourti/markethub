import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { SellerApi } from '../../core/api/seller.api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Coupon, CouponRequest, CouponStatus, CouponType } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';

const STATUS_CLASS: Record<CouponStatus, string> = {
  ACTIVE: 'ok',
  SCHEDULED: 'info',
  EXPIRED: 'muted',
  USED_UP: 'warn',
  PAUSED: 'muted',
};

const STATUS_LABEL: Record<CouponStatus, string> = {
  ACTIVE: 'Active',
  SCHEDULED: 'Scheduled',
  EXPIRED: 'Expired',
  USED_UP: 'Used up',
  PAUSED: 'Paused',
};

/** Discount codes of the seller's shop: create, edit, pause, feature on product pages. */
@Component({
  selector: 'app-seller-coupons-page',
  imports: [ReactiveFormsModule, DatePipe, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatSlideToggleModule, MoneyPipe, EmptyState, TranslatePipe],
  template: `
    <div class="page-title">
      <div>
        <h1>{{ 'Discount codes' | t }}</h1>
        <p>{{ 'Codes only discount products from your shop. The discount comes off your revenue, before commission.' | t }}</p>
      </div>
      @if (!editing()) {
        <button mat-flat-button class="btn-ink" (click)="startNew()"><mat-icon>add</mat-icon> {{ 'New code' | t }}</button>
      }
    </div>

    @if (editing()) {
      <form class="panel editor" [formGroup]="form" (ngSubmit)="save()">
        <h2>{{ (editingId() ? 'Edit code' : 'New discount code') | t }}</h2>
        <div class="fields">
          <mat-form-field>
            <mat-label>{{ 'Code' | t }}</mat-label>
            <input matInput formControlName="code" class="upper" maxlength="30" />
            <mat-hint>{{ 'Letters, digits, - and _ (3 to 30)' | t }}</mat-hint>
          </mat-form-field>
          <div class="type">
            <span class="label">{{ 'Type' | t }}</span>
            <mat-button-toggle-group formControlName="type">
              <mat-button-toggle value="PERCENT">{{ 'Percentage' | t }}</mat-button-toggle>
              <mat-button-toggle value="FIXED">{{ 'Fixed amount' | t }}</mat-button-toggle>
            </mat-button-toggle-group>
          </div>
          <mat-form-field>
            <mat-label>{{ (form.controls.type.value === 'PERCENT' ? 'Discount (%)' : 'Discount amount') | t }}</mat-label>
            <input matInput type="number" formControlName="value" min="0.01" step="0.01" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Minimum spend in your shop' | t }}</mat-label>
            <input matInput type="number" formControlName="minSubtotal" min="0" step="0.01" />
            <mat-hint>{{ 'Optional' | t }}</mat-hint>
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Starts' | t }}</mat-label>
            <input matInput type="datetime-local" formControlName="startsAt" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Ends' | t }}</mat-label>
            <input matInput type="datetime-local" formControlName="endsAt" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Usage limit' | t }}</mat-label>
            <input matInput type="number" formControlName="maxUses" min="1" />
            <mat-hint>{{ 'Empty = unlimited' | t }}</mat-hint>
          </mat-form-field>
        </div>
        <div class="toggles">
          <mat-slide-toggle formControlName="oncePerCustomer">{{ 'One use per customer' | t }}</mat-slide-toggle>
          <mat-slide-toggle formControlName="featured">{{ 'Show on my product pages' | t }}</mat-slide-toggle>
          <mat-slide-toggle formControlName="active">{{ 'Active' | t }}</mat-slide-toggle>
        </div>
        <div class="row">
          <button mat-flat-button class="btn-ink" [disabled]="saving() || form.invalid">{{ 'Save code' | t }}</button>
          <button mat-button type="button" (click)="cancel()">{{ 'Cancel' | t }}</button>
        </div>
      </form>
    }

    <section class="panel">
      @if (coupons().length) {
        <div class="table-scroll">
          <table class="coupons">
            <thead>
              <tr>
                <th>{{ 'Code' | t }}</th><th>{{ 'Discount' | t }}</th><th>{{ 'Conditions' | t }}</th>
                <th>{{ 'Uses' | t }}</th><th>{{ 'Status' | t }}</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of coupons(); track c.id) {
                <tr>
                  <td><span class="code">{{ c.code }}</span> @if (c.featured) { <mat-icon class="star" [attr.title]="'Shown on product pages' | t">campaign</mat-icon> }</td>
                  <td><strong>{{ c.label }}</strong></td>
                  <td class="muted small">
                    @if (c.minSubtotal) { {{ 'Over {amount}' | t: { amount: (c.minSubtotal | money) } }} · }
                    @if (c.endsAt) { {{ 'until {date}' | t: { date: (c.endsAt | date: 'd MMM y') } }} · }
                    {{ (c.oncePerCustomer ? 'once per customer' : 'reusable') | t }}
                  </td>
                  <td>{{ c.usedCount }}@if (c.maxUses) { / {{ c.maxUses }} }</td>
                  <td><span class="status" [class]="'status ' + statusClass(c.status)">{{ statusLabel(c.status) | t }}</span></td>
                  <td class="actions">
                    <button mat-icon-button (click)="edit(c)" [attr.aria-label]="'Edit' | t"><mat-icon>edit</mat-icon></button>
                    <button mat-icon-button (click)="remove(c)" [attr.aria-label]="'Delete' | t"><mat-icon>delete</mat-icon></button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <app-empty-state icon="sell" [title]="'No discount codes yet' | t"
                         [message]="'A welcome code shown on your product pages is a simple way to win a first order.' | t" />
      }
    </section>
  `,
  styles: `
    .editor { margin-bottom: 20px; padding: 24px; }
    .editor h2 { margin: 0 0 16px; font-size: 20px; }
    .fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 4px 16px; align-items: start; }
    .type { display: flex; flex-direction: column; gap: 6px; }
    .type .label { color: var(--mh-text-muted); font-size: 12px; }
    .upper { text-transform: uppercase; }
    .toggles { display: flex; flex-wrap: wrap; gap: 24px; margin: 12px 0 18px; }
    .table-scroll { overflow-x: auto; }
    table.coupons { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { padding: 10px 12px; border-bottom: 1px solid var(--mh-border); color: var(--mh-text-muted); font-size: 12px;
      font-weight: 600; text-align: start; text-transform: uppercase; letter-spacing: .04em; }
    td { padding: 12px; border-bottom: 1px solid var(--mh-border); vertical-align: middle; }
    .code { padding: 4px 10px; border-radius: 8px; background: var(--mh-surface-2); font-weight: 700; letter-spacing: .06em; }
    .star { width: 18px; height: 18px; color: var(--mh-accent); font-size: 18px; vertical-align: middle; }
    .small { font-size: 13px; }
    .status { padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
    .status.ok { background: var(--mh-success-bg); color: var(--mh-success); }
    .status.info { background: var(--mh-info-bg); color: var(--mh-info); }
    .status.warn { background: var(--mh-warning-bg); color: var(--mh-warning); }
    .status.muted { background: var(--mh-surface-2); color: var(--mh-text-muted); }
    .actions { white-space: nowrap; text-align: end; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerCouponsPage {
  private readonly api = inject(SellerApi);
  private readonly notifier = inject(Notifier);
  private readonly i18n = inject(I18nService);
  protected readonly coupons = signal<Coupon[]>([]);
  protected readonly editing = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]{3,30}$/)]],
    type: ['PERCENT' as CouponType, Validators.required],
    value: [10 as number | null, [Validators.required, Validators.min(0.01)]],
    minSubtotal: [null as number | null],
    startsAt: [''],
    endsAt: [''],
    maxUses: [null as number | null],
    oncePerCustomer: [true],
    featured: [false],
    active: [true],
  });

  constructor() {
    this.reload();
  }

  protected statusClass(status: CouponStatus): string {
    return STATUS_CLASS[status];
  }

  protected statusLabel(status: CouponStatus): string {
    return STATUS_LABEL[status];
  }

  protected startNew(): void {
    this.form.reset({ type: 'PERCENT', value: 10, oncePerCustomer: true, featured: false, active: true, code: '' });
    this.editingId.set(null);
    this.editing.set(true);
  }

  protected edit(c: Coupon): void {
    this.form.reset({
      code: c.code,
      type: c.type,
      value: c.value,
      minSubtotal: c.minSubtotal ?? null,
      startsAt: toLocalInput(c.startsAt),
      endsAt: toLocalInput(c.endsAt),
      maxUses: c.maxUses ?? null,
      oncePerCustomer: c.oncePerCustomer,
      featured: c.featured,
      active: c.active,
    });
    this.editingId.set(c.id);
    this.editing.set(true);
  }

  protected cancel(): void {
    this.editing.set(false);
  }

  protected save(): void {
    const v = this.form.getRawValue();
    const body: CouponRequest = {
      code: (v.code ?? '').trim().toUpperCase(),
      type: v.type ?? 'PERCENT',
      value: Number(v.value),
      minSubtotal: v.minSubtotal || null,
      startsAt: v.startsAt ? new Date(v.startsAt).toISOString() : null,
      endsAt: v.endsAt ? new Date(v.endsAt).toISOString() : null,
      maxUses: v.maxUses || null,
      oncePerCustomer: !!v.oncePerCustomer,
      featured: !!v.featured,
      active: !!v.active,
    };
    const id = this.editingId();
    this.saving.set(true);
    (id ? this.api.updateCoupon(id, body) : this.api.createCoupon(body)).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        this.notifier.success(this.i18n.t('Code {code} saved.', { code: body.code }));
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }

  protected remove(c: Coupon): void {
    if (!confirm(this.i18n.t('Delete the code {code}? Past orders keep their discount.', { code: c.code }))) {
      return;
    }
    this.api.deleteCoupon(c.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.coupons().subscribe((list) => this.coupons.set(list));
  }
}

function toLocalInput(iso?: string): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
