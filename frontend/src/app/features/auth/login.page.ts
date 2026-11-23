import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
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
  ],
  template: `
    <section class="hero">
      <a routerLink="/"><app-logo [light]="true" /></a>
      <div>
        <h1>One marketplace, hundreds of independent shops.</h1>
        <p>Shop with confidence: secure card payments or cash on delivery, tracked parcels and easy returns.</p>
        <ul>
          <li><span class="material-symbols-outlined">local_shipping</span> Track every parcel, shop by shop</li>
          <li><span class="material-symbols-outlined">auto_awesome</span> Recommendations tailored to you</li>
          <li><span class="material-symbols-outlined">verified_user</span> Sellers validated by our team</li>
        </ul>
      </div>
      <small>© 2026 MarketHub</small>
    </section>

    <section class="form-side">
      <div class="card">
        <h2>Welcome back</h2>
        <p>Sign in to continue to MarketHub.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field>
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            <mat-icon matPrefix>mail</mat-icon>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Password</mat-label>
            <input matInput [type]="showPassword() ? 'text' : 'password'" formControlName="password"
                   autocomplete="current-password" />
            <mat-icon matPrefix>lock</mat-icon>
            <button mat-icon-button matSuffix type="button" (click)="showPassword.set(!showPassword())"
                    [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'">
              <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
          </mat-form-field>
          @if (error()) {
            <div class="error">{{ error() }}</div>
          }
          <button mat-flat-button class="submit" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="20" /> } @else { Sign in }
          </button>
        </form>
        <p class="switch">New here? <a routerLink="/register">Create an account</a></p>

        <div class="demo">
          <strong>Demo accounts</strong>
          <div class="chips">
            <button mat-stroked-button type="button" (click)="fill('customer1@markethub.test', 'Customer123!')">Customer</button>
            <button mat-stroked-button type="button" (click)="fill('seller1@markethub.test', 'Seller123!')">Seller</button>
            <button mat-stroked-button type="button" (click)="fill('admin@markethub.test', 'Admin123!')">Admin</button>
          </div>
        </div>
      </div>
    </section>
  `,
  styleUrl: './auth-shell.scss',
  styles: `.error { padding: 10px 12px; border-radius: 10px; background: var(--mh-danger-bg); color: var(--mh-danger); font-size: 14px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  readonly returnUrl = input<string>();
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected fill(email: string, password: string): void {
    this.form.setValue({ email, password });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (user) => {
        const target = this.returnUrl() && user.role === 'CUSTOMER' ? this.returnUrl()! : this.auth.homeFor(user.role);
        void this.router.navigateByUrl(target);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Invalid email or password, or the account is disabled.');
      },
    });
  }
}
