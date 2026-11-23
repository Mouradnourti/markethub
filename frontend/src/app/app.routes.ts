import { Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/auth/auth.guards';
import { StoreLayout } from './layout/store-layout';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · MarketHub',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · MarketHub',
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'seller',
    canMatch: [roleGuard('SELLER')],
    loadChildren: () => import('./features/seller/seller.routes').then((m) => m.SELLER_ROUTES),
  },
  {
    path: 'admin',
    canMatch: [roleGuard('ADMIN')],
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '',
    component: StoreLayout,
    loadChildren: () => import('./features/store/store.routes').then((m) => m.STORE_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
