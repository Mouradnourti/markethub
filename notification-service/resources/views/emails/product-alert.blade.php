@extends('emails.layout')
@php($money = fn ($v) => \App\Support\Money::format($v, $product['currency']))
@section('title', $type === 'PRICE_DROP' ? 'Price drop' : 'Back in stock')
@section('content')
    <p style="margin:0 0 6px;font-size:13px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:#6d28d9;">
        {{ $type === 'PRICE_DROP' ? 'Price drop on your wishlist' : 'Back in stock' }}
    </p>
    <h1 style="margin:0 0 12px;font-size:24px;line-height:1.2;">{{ $product['name'] }}</h1>
    <p style="margin:0 0 20px;color:#57534e;">
        Hello {{ $recipient['name'] }},
        @if ($type === 'PRICE_DROP')
            a product you saved is now cheaper than when you last heard about it.
        @else
            a product you saved is available again.
        @endif
    </p>
    <div style="padding:16px 18px;border-radius:12px;background:#f5f3ff;margin-bottom:22px;">
        <div style="font-size:13px;color:#78716c;">Sold by {{ $product['shopName'] }}</div>
        <div style="margin-top:4px;font-size:22px;font-weight:800;">
            {{ $money($product['price']) }}
            @if ($type === 'PRICE_DROP' && !empty($product['previousPrice']))
                <span style="font-size:15px;font-weight:500;color:#a8a29e;text-decoration:line-through;">{{ $money($product['previousPrice']) }}</span>
            @endif
        </div>
    </div>
    <a href="{{ $product['url'] }}" style="display:inline-block;padding:13px 26px;border-radius:999px;background:#111111;color:#ffffff;font-weight:700;text-decoration:none;">View the product</a>
@endsection
@section('footer', 'You receive this e-mail because this product is on your MarketHub wishlist. Remove it from your wishlist to stop these alerts.')
