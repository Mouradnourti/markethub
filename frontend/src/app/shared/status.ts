import { OrderStatus } from '../core/models/api.models';

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: 'Awaiting payment',
  PENDING: 'Placed',
  CONFIRMED: 'Confirmed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RETURNED: 'Returned',
};

/** Tone used by the status chip and charts. */
export function statusTone(status: string): 'neutral' | 'info' | 'progress' | 'success' | 'danger' | 'warning' {
  switch (status) {
    case 'DELIVERED':
    case 'PAID':
    case 'APPROVED':
      return 'success';
    case 'SHIPPED':
    case 'CONFIRMED':
      return 'progress';
    case 'PENDING':
    case 'OPEN':
      return 'info';
    case 'AWAITING_PAYMENT':
    case 'SUSPENDED':
    case 'REFUNDED':
      return 'warning';
    case 'CANCELLED':
    case 'RETURNED':
    case 'FAILED':
    case 'REJECTED':
      return 'danger';
    default:
      return 'neutral';
  }
}

export function paymentLabel(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

export function humanize(value: string): string {
  return (ORDER_STATUS_LABELS as Record<string, string>)[value]
    ?? value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ');
}

/** Statuses a seller may move a parcel to, mirroring the backend rules. */
export const SELLER_NEXT_STATUSES: Partial<Record<OrderStatus, OrderStatus[]>> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED', 'RETURNED'],
};
