import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AdminApi } from '../../core/api/admin.api';
import { Notifier } from '../../core/ui/notifier.service';

@Component({
  selector: 'app-admin-settings-page',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <div class="page-title"><div><h1>Platform settings</h1><p>Commission and shipping rules applied to new orders.</p></div></div>
    <form class="panel form" [formGroup]="form" (ngSubmit)="save()">
      <mat-form-field>
        <mat-label>Default commission</mat-label>
        <input matInput type="number" step="0.5" min="0" max="100" formControlName="defaultCommissionRate" />
        <span matTextSuffix>%</span>
        <mat-hint>Used for every shop without a specific rate</mat-hint>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Flat shipping fee</mat-label>
        <input matInput type="number" step="0.01" min="0" formControlName="shippingFlatFee" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Free shipping from</mat-label>
        <input matInput type="number" step="1" min="0" formControlName="freeShippingThreshold" />
        <mat-hint>Set 0 to always charge the flat fee</mat-hint>
      </mat-form-field>
      <div><button mat-flat-button [disabled]="saving()"><mat-icon>save</mat-icon> Save settings</button></div>
    </form>
  `,
  styles: `.form { display: flex; flex-direction: column; gap: 16px; max-width: 520px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminSettingsPage {
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  protected readonly saving = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    defaultCommissionRate: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
    shippingFlatFee: [0, [Validators.required, Validators.min(0)]],
    freeShippingThreshold: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.api.settings().subscribe((settings) => this.form.setValue(settings));
  }

  protected save(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.api.updateSettings(this.form.getRawValue()).subscribe({
      next: (settings) => {
        this.form.setValue(settings);
        this.saving.set(false);
        this.notifier.success('Settings saved');
      },
      error: () => this.saving.set(false),
    });
  }
}
