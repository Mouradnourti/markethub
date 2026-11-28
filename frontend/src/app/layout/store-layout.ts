import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogApi } from '../core/api/catalog.api';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { WishlistService } from '../core/wishlist/wishlist.service';
import { LanguagePicker } from './language-picker';
import { NotificationBell } from './notification-bell';
import { SearchBox } from './search-box';
import { AuthService } from '../core/auth/auth.service';
import { CartService } from '../core/cart/cart.service';
import { ConfigService } from '../core/config/config.service';
import { MoneyPipe } from '../shared/pipes/money.pipe';
import { CartDrawer } from './cart-drawer';
import { Logo } from './logo';

const ANNOUNCEMENT_INTERVAL_MS = 5000;

@Component({
  selector: 'app-store-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    MoneyPipe,
    Logo,
    CartDrawer,
    SearchBox,
    NotificationBell,
    LanguagePicker,
    TranslatePipe,
  ],
  templateUrl: './store-layout.html',
  styleUrl: './store-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StoreLayout {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);
  protected readonly config = inject(ConfigService);
  protected readonly wishlist = inject(WishlistService);
  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  /** Index of the benefit shown in the announcement bar on small screens. */
  protected readonly announcement = signal(0);
  protected query = '';

  constructor() {
    const timer = setInterval(() => this.announcement.update((i) => (i + 1) % 3), ANNOUNCEMENT_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

}
