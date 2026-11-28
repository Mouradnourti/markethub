@extends('emails.layout')
@php($money = fn ($v) => \App\Support\Money::format($v, $cart['currency']))
@section('title', 'Your cart is waiting')
@section('content')
    <h1 style="margin:0 0 12px;font-size:24px;line-height:1.2;">Your cart is waiting</h1>
    <p style="margin:0 0 20px;color:#57534e;">Hello {{ $recipient['name'] }}, you left these items in your cart. They are still available.</p>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:14px;">
        @foreach ($cart['items'] as $item)
            <tr>
                <td style="padding:8px 0;border-top:1px solid #e7e5e4;">{{ $item['quantity'] }} × {{ $item['name'] }}</td>
                <td style="padding:8px 0;border-top:1px solid #e7e5e4;text-align:right;">{{ $money($item['lineTotal']) }}</td>
            </tr>
        @endforeach
        <tr>
            <td style="padding:10px 0;border-top:1px solid #e7e5e4;font-weight:700;">Subtotal</td>
            <td style="padding:10px 0;border-top:1px solid #e7e5e4;text-align:right;font-weight:700;">{{ $money($cart['subtotal']) }}</td>
        </tr>
    </table>
    <p style="margin:24px 0 0;">
        <a href="{{ $cart['url'] }}" style="display:inline-block;padding:13px 26px;border-radius:999px;background:#111111;color:#ffffff;font-weight:700;text-decoration:none;">Back to my cart</a>
    </p>
@endsection
