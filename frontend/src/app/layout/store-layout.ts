import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogApi } from '../core/api/catalog.api';
import { AuthService } from '../core/auth/auth.service';
import { CartService } from '../core/cart/cart.service';
import { Logo } from './logo';

@Component({
  selector: 'app-store-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatBadgeModule,
    MatMenuModule,
    MatDividerModule,
    Logo,
  ],
  templateUrl: './store-layout.html',
  styleUrl: './store-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StoreLayout {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);
  private readonly router = inject(Router);
  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  protected query = '';

  protected search(): void {
    void this.router.navigate(['/products'], { queryParams: { q: this.query.trim() || null } });
  }
}
