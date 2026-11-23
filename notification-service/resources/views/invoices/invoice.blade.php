@php($money = fn ($v) => \App\Support\Money::format($v, $order['currency']))
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Invoice {{ $invoiceNumber }}</title>
    <style>
        @page { margin: 36px 40px; }
        body { font-family: DejaVu Sans, sans-serif; font-size: 11px; color: #0f172a; }
        .brand { font-size: 22px; font-weight: bold; color: #4f46e5; }
        .muted { color: #64748b; }
        table { width: 100%; border-collapse: collapse; }
        .head td { vertical-align: top; }
        .title { font-size: 20px; font-weight: bold; text-align: right; }
        .box { margin-top: 24px; }
        .items th { background: #eef2ff; color: #3730a3; text-align: left; padding: 7px 8px; font-size: 10px; text-transform: uppercase; }
        .items td { padding: 7px 8px; border-bottom: 1px solid #e2e8f0; }
        .num { text-align: right; }
        .shop td { background: #f8fafc; font-weight: bold; }
        .totals { width: 45%; margin-left: 55%; margin-top: 14px; }
        .totals td { padding: 5px 8px; }
        .grand td { font-size: 14px; font-weight: bold; border-top: 2px solid #4f46e5; }
        .footer { position: fixed; bottom: 0; left: 0; right: 0; font-size: 9px; color: #94a3b8; text-align: center; }
    </style>
</head>
<body>
<table class="head">
    <tr>
        <td>
            <div class="brand">MarketHub</div>
            <div class="muted">Multi-vendor marketplace<br>billing@markethub.test</div>
        </td>
        <td>
            <div class="title">INVOICE</div>
            <div class="num muted">
                N° {{ $invoiceNumber }}<br>
                Order {{ $order['reference'] }}<br>
                Issued {{ $issuedAt->format('d/m/Y') }}<br>
                Order date {{ \Illuminate\Support\Carbon::parse($order['createdAt'])->format('d/m/Y') }}
            </div>
        </td>
    </tr>
</table>

<table class="box">
    <tr>
        <td style="width:50%">
            <strong>Billed to</strong><br>
            {{ $customer['name'] }}<br>
            <span class="muted">{{ $customer['email'] }}</span>
        </td>
        <td style="width:50%">
            <strong>Delivered to</strong><br>
            {{ $order['shippingAddress']['fullName'] }}<br>
            {{ $order['shippingAddress']['addressLine'] }}<br>
            {{ $order['shippingAddress']['postalCode'] }} {{ $order['shippingAddress']['city'] }}, {{ $order['shippingAddress']['country'] }}
        </td>
    </tr>
</table>

<table class="items box">
    <thead>
    <tr><th>Item</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Total</th></tr>
    </thead>
    <tbody>
    @foreach ($order['shopOrders'] as $shopOrder)
        <tr class="shop"><td colspan="4">Sold by {{ $shopOrder['shopName'] }} — {{ ucfirst(strtolower(str_replace('_', ' ', $shopOrder['status']))) }}</td></tr>
        @foreach ($shopOrder['items'] as $item)
            <tr>
                <td>{{ $item['name'] }}</td>
                <td class="num">{{ $item['quantity'] }}</td>
                <td class="num">{{ $money($item['unitPrice']) }}</td>
                <td class="num">{{ $money($item['lineTotal']) }}</td>
            </tr>
        @endforeach
    @endforeach
    </tbody>
</table>

<table class="totals">
    <tr><td class="muted">Subtotal</td><td class="num">{{ $money($order['subtotal']) }}</td></tr>
    <tr><td class="muted">Shipping</td><td class="num">{{ $money($order['shippingFee']) }}</td></tr>
    <tr class="grand"><td>Total</td><td class="num">{{ $money($order['total']) }}</td></tr>
    <tr><td class="muted">Payment</td><td class="num">
        {{ $order['paymentMethod'] === 'COD' ? 'Cash on delivery' : 'Card' }} ({{ strtolower($order['paymentStatus']) }})
    </td></tr>
</table>

<div class="footer">MarketHub — invoice generated automatically. Prices include VAT where applicable. Demo project, not a legal document.</div>
</body>
</html>
