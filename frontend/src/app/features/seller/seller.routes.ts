import { Routes } from '@angular/router';
import { DashboardLayout, NavItem } from '../../layout/dashboard-layout';

const NAV: NavItem[] = [
  { label: 'Overview', icon: 'space_dashboard', link: '/seller', exact: true },
  { label: 'Orders', icon: 'local_shipping', link: '/seller/orders' },
  { label: 'Products', icon: 'inventory_2', link: '/seller/products' },
  { label: 'My shop', icon: 'storefront', link: '/seller/shop' },
];

export const SELLER_ROUTES: Routes = [
  {
    path: '',
    component: DashboardLayout,
    data: { nav: NAV, area: 'Seller center' },
    children: [
      { path: '', title: 'Seller dashboard', loadComponent: () => import('./seller-dashboard.page').then((m) => m.SellerDashboardPage) },
      { path: 'orders', title: 'Orders · Seller', loadComponent: () => import('./seller-orders.page').then((m) => m.SellerOrdersPage) },
      { path: 'products', title: 'Products · Seller', loadComponent: () => import('./seller-products.page').then((m) => m.SellerProductsPage) },
      { path: 'products/new', title: 'New product', loadComponent: () => import('./product-form.page').then((m) => m.ProductFormPage) },
      { path: 'products/:id', title: 'Edit product', loadComponent: () => import('./product-form.page').then((m) => m.ProductFormPage) },
      { path: 'shop', title: 'My shop', loadComponent: () => import('./shop-settings.page').then((m) => m.ShopSettingsPage) },
    ],
  },
];
