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
export type ProductSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'RATING' | 'POPULAR' | 'RELEVANCE';

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
  twoFactorEnabled?: boolean;
  createdAt: string;
}

/** With two-factor sign-in, the password step only returns {@link challengeToken}. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
  twoFactorRequired?: boolean;
  challengeToken?: string;
}

export interface TwoFactorSetup {
  secret: string;
  otpauthUri: string;
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
  /** "rules" or the language model that wrote the text. */
  engine?: string;
}

export interface PriceHint {
  low: number;
  typical: number;
  high: number;
  suggested: number;
  confidence: 'low' | 'medium' | 'high';
  comparables: { productId: number; name: string; price: number; similarity: number }[];
}

export interface Suggestions {
  products: ProductSummary[];
  categories: Category[];
}

export interface Facets {
  total: number;
  inStock: number;
  onSale: number;
  minPrice?: number;
  maxPrice?: number;
  categories: { id: number; count: number }[];
}

export interface SmartSearchResult {
  products: ProductSummary[];
  strategy: 'semantic' | 'keyword';
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

export type ReviewSort = 'RECENT' | 'HELPFUL' | 'HIGHEST' | 'LOWEST';

export interface Review {
  id: number;
  rating: number;
  comment?: string;
  authorName: string;
  createdAt: string;
  helpfulCount: number;
  votedHelpful: boolean;
  mine: boolean;
  photoUrls: string[];
  sellerReply?: string;
  sellerRepliedAt?: string;
}

/** counts[0] = number of 1-star reviews ... counts[4] = 5-star. */
export interface ReviewSummary {
  average: number;
  total: number;
  counts: number[];
  withPhotos: number;
}

export interface SellerReview {
  id: number;
  productId: number;
  productName: string;
  productImageUrl?: string;
  rating: number;
  comment?: string;
  authorName: string;
  createdAt: string;
  helpfulCount: number;
  photoUrls: string[];
  sellerReply?: string;
  sellerRepliedAt?: string;
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

export interface AppliedCoupon {
  code: string;
  shopId: number;
  shopName: string;
  label: string;
  discount: number;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  discount: number;
  coupon?: AppliedCoupon;
  shippingFee: number;
  total: number;
}

export type CouponType = 'PERCENT' | 'FIXED';
export type CouponStatus = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'USED_UP' | 'PAUSED';

export interface Coupon {
  id: number;
  code: string;
  type: CouponType;
  value: number;
  minSubtotal?: number;
  startsAt?: string;
  endsAt?: string;
  maxUses?: number;
  usedCount: number;
  oncePerCustomer: boolean;
  featured: boolean;
  active: boolean;
  status: CouponStatus;
  label: string;
  createdAt: string;
}

export interface CouponRequest {
  code: string;
  type: CouponType;
  value: number;
  minSubtotal?: number | null;
  startsAt?: string | null;
  endsAt?: string | null;
  maxUses?: number | null;
  oncePerCustomer: boolean;
  featured: boolean;
  active: boolean;
}

export interface Offer {
  code: string;
  label: string;
  shopName: string;
  minSubtotal?: number;
  endsAt?: string;
}

export interface WishlistItem {
  product: ProductSummary;
  priceWhenAdded: number;
  addedAt: string;
}

export type NotificationType =
  | 'ORDER_UPDATE'
  | 'NEW_ORDER'
  | 'BACK_IN_STOCK'
  | 'PRICE_DROP'
  | 'REVIEW_REPLY'
  | 'CART_REMINDER';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  body?: string;
  link?: string;
  read: boolean;
  createdAt: string;
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
  /** What the customer pays for the parcel's items, after {@link discountAmount}. */
  subtotal: number;
  discountAmount: number;
  couponCode?: string;
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
  discountTotal: number;
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
  discountAmount: number;
  couponCode?: string;
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

export interface AnalyticsDay {
  date: string;
  views: number;
  orders: number;
  revenue: number;
}

export interface ProductPerformance {
  productId: number;
  name: string;
  imageUrl?: string;
  stock: number;
  rating: number;
  views: number;
  orders: number;
  units: number;
  revenue: number;
  conversionRate: number;
  returns: number;
  returnRate: number;
}

export interface SellerAnalytics {
  days: number;
  views: number;
  orders: number;
  units: number;
  revenue: number;
  conversionRate: number;
  averageOrderValue: number;
  returnRate: number;
  wishlistSaves: number;
  daily: AnalyticsDay[];
  products: ProductPerformance[];
}
