<?php

use Illuminate\Support\Facades\Route;

Route::get('/', fn () => response()->json([
    'service' => 'markethub-notification-service',
    'endpoints' => ['GET /api/health', 'POST /api/notifications/order-event', 'POST /api/invoices'],
]));
