@php($money = fn ($v) => \App\Support\Money::format($v, $order['currency']))
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>{{ $headline }}</title></head>
<body style="margin:0;padding:0;background:#f6f7fb;font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#0f172a;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f6f7fb;padding:24px 0;">
    <tr><td align="center">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;">
            <tr><td style="background:linear-gradient(135deg,#4338ca,#7c3aed);background-color:#4f46e5;padding:24px 28px;color:#ffffff;">
                <div style="font-size:20px;font-weight:700;">Market<span style="color:#c7d2fe;">Hub</span></div>
            </td></tr>
            <tr><td style="padding:28px;">
                <h1 style="margin:0 0 8px;font-size:24px;">{{ $headline }}</h1>
                <p style="margin:0 0 16px;color:#475569;">Hello {{ $recipient['name'] }}, {{ $intro }}</p>

                <p style="margin:0 0 20px;font-size:14px;color:#64748b;">
                    Order <strong style="color:#0f172a;">{{ $order['reference'] }}</strong>
                    · {{ \Illuminate\Support\Carbon::parse($order['createdAt'])->format('d M Y') }}
                    · {{ $order['paymentMethod'] === 'COD' ? 'Cash on delivery' : 'Card' }}
                </p>

                @if ($parcel)
                    <div style="padding:14px 16px;border-radius:12px;background:#eef2ff;margin-bottom:20px;">
                        <strong>{{ $parcel['shopName'] }}</strong> — {{ ucfirst(strtolower($parcel['status'])) }}
                        @if (!empty($parcel['trackingNumber']))
                            <br><span style="font-size:14px;">Tracking number: <strong>{{ $parcel['trackingNumber'] }}</strong></span>
                        @endif
                    </div>
                @endif

                @foreach ($order['shopOrders'] as $shopOrder)
                    <div style="font-size:13px;font-weight:700;text-transform:uppercase;color:#64748b;margin:16px 0 6px;">{{ $shopOrder['shopName'] }}</div>
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:14px;">
                        @foreach ($shopOrder['items'] as $item)
                            <tr>
                                <td style="padding:6px 0;border-top:1px solid #e2e8f0;">{{ $item['quantity'] }} × {{ $item['name'] }}</td>
                                <td style="padding:6px 0;border-top:1px solid #e2e8f0;text-align:right;">{{ $money($item['lineTotal']) }}</td>
                            </tr>
                        @endforeach
                    </table>
                @endforeach

                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:14px;margin-top:16px;">
                    <tr><td style="padding:4px 0;color:#64748b;">Subtotal</td><td style="text-align:right;">{{ $money($order['subtotal']) }}</td></tr>
                    <tr><td style="padding:4px 0;color:#64748b;">Shipping</td><td style="text-align:right;">{{ (float) $order['shippingFee'] > 0 ? $money($order['shippingFee']) : 'Free' }}</td></tr>
                    <tr><td style="padding:8px 0;font-weight:700;font-size:16px;border-top:1px solid #e2e8f0;">Total</td>
                        <td style="padding:8px 0;font-weight:700;font-size:16px;text-align:right;border-top:1px solid #e2e8f0;">{{ $money($order['total']) }}</td></tr>
                </table>

                <p style="margin:24px 0 0;font-size:14px;color:#475569;">
                    Delivery to {{ $order['shippingAddress']['fullName'] }}, {{ $order['shippingAddress']['addressLine'] }},
                    {{ $order['shippingAddress']['postalCode'] }} {{ $order['shippingAddress']['city'] }}.
                </p>
            </td></tr>
            <tr><td style="padding:16px 28px;background:#f8fafc;font-size:12px;color:#94a3b8;">
                You receive this e-mail because you placed an order on MarketHub. This is an automated message.
            </td></tr>
        </table>
    </td></tr>
</table>
</body>
</html>
