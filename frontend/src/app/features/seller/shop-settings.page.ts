import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { SellerApi } from '../../core/api/seller.api';
import { Shop } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { StatusChip } from '../../shared/ui/status-chip';
import { ShopStatusBanner } from './shop-status-banner';

@Component({
  selector: 'app-shop-settings-page',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, StatusChip, ShopStatusBanner],
  template: `
    <div class="page-title">
      <div><h1>My shop</h1><p>How customers see your shop.</p></div>
      @if (shop(); as s) {
        @if (s.status === 'APPROVED') {
          <a mat-stroked-button [routerLink]="['/shops', s.slug]"><mat-icon>open_in_new</mat-icon> View public page</a>
        }
      }
    </div>
    <app-shop-status-banner [shop]="shop()" />
    <form class="panel form" [formGroup]="form" (ngSubmit)="save()">
      @if (shop(); as s) {
        <div class="row"><span class="muted">Status</span> <app-status-chip [status]="s.status" />
          <span class="muted">· Commission</span> <strong>{{ s.commissionRate != null ? s.commissionRate + ' %' : 'platform default' }}</strong>
        </div>
      }
      <mat-form-field><mat-label>Shop name</mat-label><input matInput formControlName="name" maxlength="120" /></mat-form-field>
      <mat-form-field><mat-label>Description</mat-label><textarea matInput rows="4" formControlName="description" maxlength="2000"></textarea></mat-form-field>
      <mat-form-field><mat-label>Logo URL (optional)</mat-label><input matInput formControlName="logoUrl" maxlength="500" /></mat-form-field>
      <div><button mat-flat-button [disabled]="saving()"><mat-icon>save</mat-icon> Save</button></div>
    </form>
  `,
  styles: `.form { display: flex; flex-direction: column; gap: 14px; max-width: 720px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopSettingsPage {
  private readonly api = inject(SellerApi);
  private readonly notifier = inject(Notifier);
  protected readonly shop = signal<Shop | null>(null);
  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    logoUrl: [''],
  });

  constructor() {
    this.api.shop().subscribe((shop) => {
      this.shop.set(shop);
      this.form.patchValue({ name: shop.name, description: shop.description ?? '', logoUrl: shop.logoUrl ?? '' });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    this.api.updateShop({ ...value, logoUrl: value.logoUrl || undefined }).subscribe({
      next: (shop) => {
        this.shop.set(shop);
        this.saving.set(false);
        this.notifier.success('Shop updated');
      },
      error: () => this.saving.set(false),
    });
  }
}
