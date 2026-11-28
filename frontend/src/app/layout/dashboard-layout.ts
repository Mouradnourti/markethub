import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { LanguagePicker } from './language-picker';
import { Logo } from './logo';
import { NotificationBell } from './notification-bell';

export interface NavItem {
  label: string;
  icon: string;
  link: string;
  exact?: boolean;
}

/** Back-office shell for sellers and admins; nav items come from the route data. */
@Component({
  selector: 'app-dashboard-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    Logo,
    NotificationBell,
    LanguagePicker,
    TranslatePipe,
  ],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav #drawer [mode]="mobile() ? 'over' : 'side'" [opened]="!mobile()" class="side">
        <a routerLink="/" class="brand"><app-logo /></a>
        <span class="area">{{ area() | t }}</span>
        <mat-nav-list>
          @for (item of nav(); track item.link) {
            <a mat-list-item [routerLink]="item.link" routerLinkActive="active"
               [routerLinkActiveOptions]="{ exact: item.exact ?? false }" (click)="mobile() && drawer.close()">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label | t }}</span>
            </a>
          }
        </mat-nav-list>
        <div class="side-footer">
          <a mat-stroked-button routerLink="/" class="full-width"><mat-icon>storefront</mat-icon> {{ 'View store' | t }}</a>
        </div>
      </mat-sidenav>
      <mat-sidenav-content>
        <mat-toolbar class="toolbar">
          @if (mobile()) {
            <button mat-icon-button (click)="drawer.toggle()" [attr.aria-label]="'Menu' | t"><mat-icon>menu</mat-icon></button>
          }
          <span class="spacer"></span>
          <app-language-picker />
          <app-notification-bell />
          @if (auth.user(); as user) {
            <button mat-button [matMenuTriggerFor]="menu">
              <span class="avatar">{{ initials() }}</span> {{ user.firstName }} {{ user.lastName }}
              <mat-icon>expand_more</mat-icon>
            </button>
            <mat-menu #menu="matMenu" xPosition="before">
              <span class="menu-email">{{ user.email }}</span>
              <a mat-menu-item [routerLink]="securityLink()"><mat-icon>shield_lock</mat-icon>{{ 'Sign-in & security' | t }}</a>
              <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>{{ 'Sign out' | t }}</button>
            </mat-menu>
          }
        </mat-toolbar>
        <div class="content"><router-outlet /></div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .shell { min-height: 100vh; background: var(--mh-bg); }
    .side { width: 250px; padding: 18px 10px; border-inline-end: 1px solid var(--mh-border); background: var(--mh-surface); }
    .brand { display: block; padding: 0 12px 4px; text-decoration: none; }
    .area { display: block; padding: 0 12px 14px; font-size: 12px; font-weight: 600; letter-spacing: 0.08em;
      text-transform: uppercase; color: var(--mh-text-muted); }
    .active { background: var(--mh-accent-soft) !important; }
    .active mat-icon, .active [matListItemTitle] { color: var(--mh-accent) !important; }
    mat-nav-list a { border-radius: 10px; margin-bottom: 2px; }
    .side-footer { padding: 18px 12px; }
    .toolbar { position: sticky; top: 0; z-index: 5; background: var(--mh-surface); border-bottom: 1px solid var(--mh-border); }
    .toolbar app-language-picker { margin-inline-end: 6px; }
    .avatar { display: inline-grid; place-items: center; width: 28px; height: 28px; margin-inline-end: 6px; border-radius: 50%;
      background: var(--mh-accent-soft); color: var(--mh-accent); font-weight: 700; font-size: 13px; }
    .menu-email { display: block; padding: 8px 16px; font-size: 12px; color: var(--mh-text-muted); }
    .content { padding: 24px 28px 48px; max-width: 1400px; }
    @media (max-width: 600px) { .content { padding: 16px; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardLayout {
  readonly nav = input<NavItem[]>([]);
  readonly area = input('');
  protected readonly auth = inject(AuthService);
  protected readonly mobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly securityLink = computed(() => (this.auth.role() === 'ADMIN' ? '/admin/security' : '/seller/security'));
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return user ? user.firstName.charAt(0) + user.lastName.charAt(0) : '';
  });
}
