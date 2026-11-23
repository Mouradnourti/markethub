import { Routes } from '@angular/router';
import { DashboardLayout, NavItem } from '../../layout/dashboard-layout';

const NAV: NavItem[] = [
  { label: 'Overview', icon: 'monitoring', link: '/admin', exact: true },
  { label: 'Sellers & shops', icon: 'storefront', link: '/admin/shops' },
  { label: 'Users', icon: 'group', link: '/admin/users' },
  { label: 'Returns & disputes', icon: 'gavel', link: '/admin/disputes' },
  { label: 'Categories', icon: 'category', link: '/admin/categories' },
  { label: 'Settings', icon: 'tune', link: '/admin/settings' },
];

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: DashboardLayout,
    data: { nav: NAV, area: 'Admin console' },
    children: [
      { path: '', title: 'Admin · Overview', loadComponent: () => import('./admin-dashboard.page').then((m) => m.AdminDashboardPage) },
      { path: 'shops', title: 'Admin · Shops', loadComponent: () => import('./admin-shops.page').then((m) => m.AdminShopsPage) },
      { path: 'users', title: 'Admin · Users', loadComponent: () => import('./admin-users.page').then((m) => m.AdminUsersPage) },
      { path: 'disputes', title: 'Admin · Disputes', loadComponent: () => import('./admin-disputes.page').then((m) => m.AdminDisputesPage) },
      { path: 'categories', title: 'Admin · Categories', loadComponent: () => import('./admin-categories.page').then((m) => m.AdminCategoriesPage) },
      { path: 'settings', title: 'Admin · Settings', loadComponent: () => import('./admin-settings.page').then((m) => m.AdminSettingsPage) },
    ],
  },
];
