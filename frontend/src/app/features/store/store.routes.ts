import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guards';

export const STORE_ROUTES: Routes = [
  { path: '', title: 'MarketHub', loadComponent: () => import('./home.page').then((m) => m.HomePage) },
  {
    path: 'products',
    title: 'Products · MarketHub',
    loadComponent: () => import('./catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: 'products/:id',
    title: 'Product · MarketHub',
    loadComponent: () => import('./product.page').then((m) => m.ProductPage),
  },
  {
    path: 'shops/:slug',
    title: 'Shop · MarketHub',
    loadComponent: () => import('./shop.page').then((m) => m.ShopPage),
  },
  {
    path: 'cart',
    canActivate: [authGuard],
    title: 'Cart · MarketHub',
    loadComponent: () => import('./cart.page').then((m) => m.CartPage),
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    title: 'Checkout · MarketHub',
    loadComponent: () => import('./checkout.page').then((m) => m.CheckoutPage),
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    title: 'My orders · MarketHub',
    loadComponent: () => import('./orders.page').then((m) => m.OrdersPage),
  },
  {
    path: 'orders/:id',
    canActivate: [authGuard],
    title: 'Order · MarketHub',
    loadComponent: () => import('./order-detail.page').then((m) => m.OrderDetailPage),
  },
];
