import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/http/error.interceptor';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { User } from '../../core/models/api.models';
import { LanguagePicker } from '../../layout/language-picker';
import { Logo } from '../../layout/logo';

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    Logo,
    LanguagePicker,
    TranslatePipe,
  ],
  template: `
    <section class="hero">
      <a routerLink="/"><app-logo [light]="true" /></a>
      <div>
        <h1>{{ 'One marketplace, hundreds of independent shops.' | t }}</h1>
        <p>{{ 'Shop with confidence: secure card payments or cash on delivery, tracked parcels and easy returns.' | t }}</p>
        <ul>
          <li><span class="material-symbols-outlined">local_shipping</span> {{ 'Track every parcel, shop by shop' | t }}</li>
          <li><span class="material-symbols-outlined">auto_awesome</span> {{ 'Recommendations tailored to you' | t }}</li>
          <li><span class="material-symbols-outlined">verified_user</span> {{ 'Sellers validated by our team' | t }}</li>
        </ul>
      </div>
      <small>© 2026 MarketHub</small>
    </section>

    <section class="form-side">
      <div class="lang"><app-language-picker /></div>
      <div class="card">
        @if (challenge(); as token) {
          <span class="step-icon"><mat-icon>shield_lock</mat-icon></span>
          <h2>{{ 'Two-step verification' | t }}</h2>
          <p>{{ 'Enter the 6-digit code from your authenticator app.' | t }}</p>
          <form [formGroup]="codeForm" (ngSubmit)="verify(token)">
            <mat-form-field>
              <mat-label>{{ 'Verification code' | t }}</mat-label>
              <input matInput formControlName="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
                     class="otp" />
              <mat-icon matPrefix>pin</mat-icon>
            </mat-form-field>
            @if (error()) {
              <div class="error" role="alert">{{ error() }}</div>
            }
            <button mat-flat-button class="submit" [disabled]="loading()">
              @if (loading()) { <mat-spinner diameter="20" /> } @else { {{ 'Verify and sign in' | t }} }
            </button>
          </form>
          <p class="switch"><a href="" (click)="$event.preventDefault(); back()">{{ 'Use another account' | t }}</a></p>
        } @else {
          <h2>{{ 'Welcome back' | t }}</h2>
          <p>{{ 'Sign in to continue to MarketHub.' | t }}</p>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field>
              <mat-label>{{ 'Email' | t }}</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" />
              <mat-icon matPrefix>mail</mat-icon>
            </mat-form-field>
            <mat-form-field>
              <mat-label>{{ 'Password' | t }}</mat-label>
              <input matInput [type]="showPassword() ? 'text' : 'password'" formControlName="password"
                     autocomplete="current-password" />
              <mat-icon matPrefix>lock</mat-icon>
              <button mat-icon-button matSuffix type="button" (click)="showPassword.set(!showPassword())"
                      [attr.aria-label]="(showPassword() ? 'Hide password' : 'Show password') | t">
                <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>
            @if (error()) {
              <div class="error" role="alert">{{ error() }}</div>
            }
            <button mat-flat-button class="submit" [disabled]="loading()">
              @if (loading()) { <mat-spinner diameter="20" /> } @else { {{ 'Sign in' | t }} }
            </button>
          </form>
          <p class="switch">{{ 'New here?' | t }} <a routerLink="/register">{{ 'Create an account' | t }}</a></p>

          <div class="demo">
            <strong>{{ 'Demo accounts' | t }}</strong>
            <div class="chips">
              <button mat-stroked-button type="button" (click)="fill('customer1@markethub.test', 'Customer123!')">{{ 'Customer' | t }}</button>
              <button mat-stroked-button type="button" (click)="fill('seller1@markethub.test', 'Seller123!')">{{ 'Seller' | t }}</button>
              <button mat-stroked-button type="button" (click)="fill('admin@markethub.test', 'Admin123!')">{{ 'Admin' | t }}</button>
            </div>
          </div>
        }
      </div>
    </section>
  `,
  styleUrl: './auth-shell.scss',
  styles: `
    .error { padding: 10px 12px; border-radius: 10px; background: var(--mh-danger-bg); color: var(--mh-danger); font-size: 14px; }
    .lang { position: absolute; top: 20px; inset-inline-end: 24px; }
    .form-side { position: relative; }
    .step-icon { display: grid; place-items: center; width: 52px; height: 52px; margin-bottom: 14px; border-radius: 14px;
      background: var(--mh-accent-soft); color: var(--mh-accent); }
    .otp { font-size: 22px; font-weight: 700; letter-spacing: 0.4em; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  readonly returnUrl = input<string>();
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly challenge = signal<string | null>(null);
  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly codeForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\s*\d{6}\s*$/)]],
  });

  protected fill(email: string, password: string): void {
    this.form.setValue({ email, password });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.start();
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (result) => {
        if (result.challengeToken) {
          this.loading.set(false);
          this.challenge.set(result.challengeToken);
        } else {
          this.done(result.user!);
        }
      },
      error: (error: unknown) => this.fail(error, 'Invalid email or password, or the account is disabled.'),
    });
  }

  protected verify(challengeToken: string): void {
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.start();
    this.auth.completeTwoFactor(challengeToken, this.codeForm.getRawValue().code.trim()).subscribe({
      next: (user) => this.done(user),
      error: (error: unknown) => this.fail(error, 'This code is not valid.'),
    });
  }

  protected back(): void {
    this.challenge.set(null);
    this.codeForm.reset();
    this.error.set(null);
  }

  private start(): void {
    this.loading.set(true);
    this.error.set(null);
  }

  private done(user: User): void {
    const target = this.returnUrl() && user.role === 'CUSTOMER' ? this.returnUrl()! : this.auth.homeFor(user.role);
    void this.router.navigateByUrl(target);
  }

  /** Lockouts and expired challenges carry a precise message; other failures stay generic. */
  private fail(error: unknown, fallback: string): void {
    this.loading.set(false);
    const precise = error instanceof HttpErrorResponse && (error.status === 429 || error.status === 0);
    this.error.set(precise ? problemMessage(error) : this.i18n.t(fallback));
  }
}
