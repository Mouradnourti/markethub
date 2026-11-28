<?php

namespace App\Http\Requests;

/** Validation rules for the order document shared by e-mails and invoices (see docs/api-contract.md). */
final class OrderPayloadRules
{
    public static function order(string $prefix = 'order'): array
    {
        return [
            $prefix => ['required', 'array'],
            "$prefix.reference" => ['required', 'string', 'max:30'],
            "$prefix.createdAt" => ['required', 'date'],
            "$prefix.paymentMethod" => ['required', 'in:COD,CARD'],
            "$prefix.paymentStatus" => ['required', 'string', 'max:20'],
            "$prefix.currency" => ['required', 'string', 'size:3'],
            "$prefix.subtotal" => ['required', 'numeric', 'min:0'],
            "$prefix.discountTotal" => ['nullable', 'numeric', 'min:0'],
            "$prefix.shippingFee" => ['required', 'numeric', 'min:0'],
            "$prefix.total" => ['required', 'numeric', 'min:0'],
            "$prefix.shippingAddress" => ['required', 'array'],
            "$prefix.shippingAddress.fullName" => ['required', 'string', 'max:160'],
            "$prefix.shippingAddress.phone" => ['required', 'string', 'max:30'],
            "$prefix.shippingAddress.addressLine" => ['required', 'string', 'max:255'],
            "$prefix.shippingAddress.city" => ['required', 'string', 'max:100'],
            "$prefix.shippingAddress.postalCode" => ['required', 'string', 'max:20'],
            "$prefix.shippingAddress.country" => ['required', 'string', 'max:80'],
            "$prefix.shopOrders" => ['required', 'array', 'min:1'],
            "$prefix.shopOrders.*.shopName" => ['required', 'string', 'max:120'],
            "$prefix.shopOrders.*.status" => ['required', 'string', 'max:20'],
            "$prefix.shopOrders.*.subtotal" => ['required', 'numeric', 'min:0'],
            "$prefix.shopOrders.*.discountAmount" => ['nullable', 'numeric', 'min:0'],
            "$prefix.shopOrders.*.couponCode" => ['nullable', 'string', 'max:30'],
            "$prefix.shopOrders.*.trackingNumber" => ['nullable', 'string', 'max:60'],
            "$prefix.shopOrders.*.items" => ['required', 'array', 'min:1'],
            "$prefix.shopOrders.*.items.*.name" => ['required', 'string', 'max:200'],
            "$prefix.shopOrders.*.items.*.quantity" => ['required', 'integer', 'min:1'],
            "$prefix.shopOrders.*.items.*.unitPrice" => ['required', 'numeric', 'min:0'],
            "$prefix.shopOrders.*.items.*.lineTotal" => ['required', 'numeric', 'min:0'],
        ];
    }

    public static function person(string $prefix): array
    {
        return [
            $prefix => ['required', 'array'],
            "$prefix.email" => ['required', 'email', 'max:255'],
            "$prefix.name" => ['required', 'string', 'max:160'],
        ];
    }
}
