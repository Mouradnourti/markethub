/** TypeScript mirror of the backend DTOs (backend dto packages, documented in docs/api-contract.md). */

export type Role = 'CUSTOMER' | 'SELLER' | 'ADMIN';
export type ShopStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type OrderStatus =
  | 'AWAITING_PAYMENT'
  | 'PENDING'
  | 'CONFIRMED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED';
export type PaymentMethod = 'COD' | 'CARD';
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type DisputeStatus = 'OPEN' | 'APPROVED' | 'REJECTED';
export type DisputeType = 'RETURN' | 'DISPUTE';
export type ProductSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'RATING' | 'POPULAR';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
  accountType: 'CUSTOMER' | 'SELLER';
  shopName?: string;
  shopDescription?: string;
}

export interface PublicConfig {
  currency: string;
  stripeEnabled: boolean;
  stripePublishableKey?: string;
  shippingFlatFee: number;
  freeShippingThreshold: number;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
  parentId?: number;
}

export interface ProductSpec {
  label: string;
  value: string;
}

export interface ProductSummary {
  id: number;
  name: string;
  price: number;
  /** Original price, set while the product is discounted. */
  compareAtPrice?: number;
  stock: number;
  avgRating: number;
  reviewCount: number;
  soldCount: number;
  imageUrl?: string;
  hoverImageUrl?: string;
  shopId: number;
  shopName: string;
  categoryId: number;
  categoryName: string;
  active: boolean;
}

export interface Product {
  id: number;
  name: string;
  tagline?: string;
  description?: string;
  highlights: string[];
  specs: ProductSpec[];
  price: number;
  compareAtPrice?: number;
  stock: number;
  active: boolean;
  avgRating: number;
  reviewCount: number;
  soldCount: number;
  categoryId: number;
  categoryName: string;
  shopId: number;
  shopName: string;
  shopSlug: string;
  images: string[];
  createdAt: string;
}

export interface ProductRequest {
  name: string;
  tagline?: string;
  description?: string;
  highlights?: string[];
  specs?: ProductSpec[];
  price: number;
  compareAtPrice?: number | null;
  stock: number;
  categoryId: number;
  active: boolean;
  imageUrls?: string[];
}

export type CopyTone = 'friendly' | 'premium' | 'technical';

export interface CopyDraftRequest {
  name: string;
  categoryId?: number;
  keywords: string[];
  tone: CopyTone;
  variant: number;
}

/** Draft written by the AI description writer; the seller reviews it before saving. */
export interface CopyDraft {
  tagline: string;
  description: string;
  highlights: string[];
  specs: ProductSpec[];
  suggestedSpecLabels: string[];
  categoryProfile: string;
}

export interface ProductQuery {
  q?: string;
  categoryId?: number;
  shopId?: number;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  inStock?: boolean;
  onSale?: boolean;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

export interface Shop {
  id: number;
  name: string;
  slug: string;
  description?: string;
  logoUrl?: string;
  status: ShopStatus;
  commissionRate?: number;
  createdAt: string;
}

export interface ShopAdmin {
  id: number;
  name: string;
  slug: string;
  status: ShopStatus;
  commissionRate?: number;
  ownerId: number;
  ownerEmail: string;
  ownerName: string;
  productCount: number;
  createdAt: string;
}

export interface Review {
  id: number;
  rating: number;
  comment?: string;
  authorName: string;
  createdAt: string;
}

export interface ReviewEligibility {
  canReview: boolean;
  reason?: string;
}

export interface CartItem {
  productId: number;
  productName: string;
  imageUrl?: string;
  shopId: number;
  shopName: string;
  unitPrice: number;
  quantity: number;
  availableStock: number;
  available: boolean;
  lineTotal: number;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  shippingFee: number;
  total: number;
}

export interface ShippingAddress {
  fullName: string;
  phone: string;
  addressLine: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface OrderItem {
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface StatusChange {
  status: OrderStatus;
  note?: string;
  changedAt: string;
}

export interface ShopOrder {
  id: number;
  shopId: number;
  shopName: string;
  status: OrderStatus;
  subtotal: number;
  trackingNumber?: string;
  items: OrderItem[];
  history: StatusChange[];
  returnRequested: boolean;
  updatedAt: string;
}

export interface Order {
  id: number;
  reference: string;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  subtotal: number;
  shippingFee: number;
  total: number;
  shippingAddress: ShippingAddress;
  returnRiskScore?: number;
  shopOrders: ShopOrder[];
  createdAt: string;
}

export interface PaymentInit {
  provider: 'STRIPE' | 'STRIPE_MOCK';
  clientSecret: string;
  publishableKey?: string;
  amount: number;
  currency: string;
}

export interface CheckoutResponse {
  order: Order;
  payment?: PaymentInit;
}

export interface SellerOrder {
  id: number;
  orderReference: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  subtotal: number;
  commissionAmount: number;
  netAmount: number;
  trackingNumber?: string;
  customerName: string;
  shippingAddress: ShippingAddress;
  returnRiskScore?: number;
  items: OrderItem[];
  history: StatusChange[];
  createdAt: string;
}

export interface Dispute {
  id: number;
  type: DisputeType;
  status: DisputeStatus;
  reason: string;
  adminNote?: string;
  shopOrderId: number;
  shopOrderStatus: OrderStatus;
  orderReference: string;
  shopName: string;
  amount: number;
  customerId: number;
  customerName: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface MonthlyPoint {
  month: string;
  revenue: number;
  commission: number;
  orders: number;
}

export interface LabelValue {
  label: string;
  value: number;
}

export interface TopProduct {
  productId: number;
  name: string;
  units: number;
  revenue: number;
}

export interface LowStockProduct {
  productId: number;
  name: string;
  stock: number;
}

export interface SellerStats {
  grossRevenue: number;
  netRevenue: number;
  pipelineRevenue: number;
  totalOrders: number;
  ordersToProcess: number;
  returnedOrders: number;
  returnRate: number;
  productCount: number;
  lowStockCount: number;
  averageRating: number;
  ordersByStatus: Record<string, number>;
  monthly: MonthlyPoint[];
  topProducts: TopProduct[];
  lowStock: LowStockProduct[];
}

export interface AdminStats {
  usersByRole: Record<string, number>;
  shopsByStatus: Record<string, number>;
  totalOrders: number;
  grossMerchandiseValue: number;
  commissionEarned: number;
  averageOrderValue: number;
  openDisputes: number;
  paymentMethods: Record<string, number>;
  ordersByStatus: Record<string, number>;
  monthly: MonthlyPoint[];
  topCategories: LabelValue[];
  topShops: LabelValue[];
}

export interface PlatformSettings {
  defaultCommissionRate: number;
  shippingFlatFee: number;
  freeShippingThreshold: number;
}

export interface ForecastPoint {
  date: string;
  revenue: number;
  lower?: number;
  upper?: number;
}

export interface SalesForecast {
  available: boolean;
  history: ForecastPoint[];
  forecast: ForecastPoint[];
  model?: string;
  message?: string;
}
