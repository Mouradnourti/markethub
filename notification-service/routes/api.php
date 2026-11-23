<?php

use App\Http\Controllers\InvoiceController;
use App\Http\Controllers\OrderEventController;
use Illuminate\Support\Facades\Route;

Route::get('/health', fn () => response()->json(['status' => 'UP']));

// Called only by the MarketHub backend, authenticated with the shared X-Internal-Key header.
Route::middleware('internal.key')->group(function () {
    Route::post('/notifications/order-event', OrderEventController::class);
    Route::post('/invoices', InvoiceController::class);
});
