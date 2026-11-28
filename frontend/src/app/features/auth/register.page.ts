import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Notifier } from '../../core/ui/notifier.service';
import { Logo } from '../../layout/logo';

import { TranslatePipe } from '../../core/i18n/translate.pipe';
@Component({
  selector: 'app-register-page',
  imports: [TranslatePipe, 
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    Logo,
  ],
  template: `
    <section class="hero">
      <a routerLink="/"><app-logo [light]="true" /></a>
      <div>
        <h1>{{ (form.controls.accountType.value === 'SELLER' ? 'Grow your business with MarketHub.' : 'Join thousands of happy shoppers.') | t }}</h1>
        <p>
          {{ (form.controls.accountType.value === 'SELLER'
            ? 'Open your shop in minutes. Our team validates new sellers, then you manage products, orders and sales from your dashboard.'
            : 'Create your account to order from many shops in one checkout and follow every parcel.') | t }}
        </p>
      </div>
      <small>© 2026 MarketHub</small>
    </section>

    <section class="form-side">
      <div class="card">
        <h2>{{ 'Create your account' | t }}</h2>
        <p>{{ 'It takes less than a minute.' | t }}</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-button-toggle-group formControlName="accountType" class="full-width" [attr.aria-label]="'Account type' | t">
            <mat-button-toggle value="CUSTOMER" class="full-width"><mat-icon>shopping_bag</mat-icon> {{ 'I want to buy' | t }}</mat-button-toggle>
            <mat-button-toggle value="SELLER" class="full-width"><mat-icon>storefront</mat-icon> {{ 'I want to sell' | t }}</mat-button-toggle>
          </mat-button-toggle-group>
          <div class="two">
            <mat-form-field>
              <mat-label>{{ 'First name' | t }}</mat-label>
              <input matInput formControlName="firstName" autocomplete="given-name" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>{{ 'Last name' | t }}</mat-label>
              <input matInput formControlName="lastName" autocomplete="family-name" />
            </mat-form-field>
          </div>
          <mat-form-field>
            <mat-label>{{ 'Email' | t }}</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.hasError('email')) { <mat-error>{{ 'Enter a valid email' | t }}</mat-error> }
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Password' | t }}</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            <mat-hint>{{ 'At least 8 characters with letters and digits' | t }}</mat-hint>
            @if (form.controls.password.invalid && form.controls.password.touched) {
              <mat-error>{{ 'At least 8 characters with letters and digits' | t }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'Phone (optional)' | t }}</mat-label>
            <input matInput formControlName="phone" autocomplete="tel" />
          </mat-form-field>
          @if (form.controls.accountType.value === 'SELLER') {
            <mat-form-field>
              <mat-label>{{ 'Shop name' | t }}</mat-label>
              <input matInput formControlName="shopName" />
              <mat-icon matPrefix>storefront</mat-icon>
            </mat-form-field>
            <mat-form-field>
              <mat-label>{{ 'What do you sell?' | t }}</mat-label>
              <textarea matInput rows="2" formControlName="shopDescription"></textarea>
            </mat-form-field>
          }
          <button mat-flat-button class="submit" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="20" /> } @else { {{ 'Create account' | t }} }
          </button>
        </form>
        <p class="switch">{{ 'Already have an account?' | t }} <a routerLink="/login">{{ 'Sign in' | t }}</a></p>
      </div>
    </section>
  `,
  styleUrl: './auth-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage implements OnInit {
  readonly type = input<string>();
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  protected readonly loading = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    accountType: ['CUSTOMER' as 'CUSTOMER' | 'SELLER'],
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
    phone: [''],
    shopName: [''],
    shopDescription: [''],
  });

  ngOnInit(): void {
    if (this.type() === 'SELLER') {
      this.form.controls.accountType.setValue('SELLER');
    }
  }

  protected submit(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || (value.accountType === 'SELLER' && !value.shopName.trim())) {
      this.form.markAllAsTouched();
      this.notifier.error(value.accountType === 'SELLER' && !value.shopName.trim()
        ? 'Please give your shop a name.' : 'Please fix the highlighted fields.');
      return;
    }
    this.loading.set(true);
    this.auth.register({ ...value, phone: value.phone || undefined }).subscribe({
      next: (user) => {
        this.notifier.success(user.role === 'SELLER'
          ? 'Account created. Your shop is awaiting validation by an administrator.'
          : 'Welcome to MarketHub!');
        void this.router.navigateByUrl(this.auth.homeFor(user.role));
      },
      error: () => this.loading.set(false),
    });
  }
}
