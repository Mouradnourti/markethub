<?php

namespace App\Http\Controllers;

use App\Http\Requests\CartReminderRequest;
use App\Http\Requests\ProductAlertRequest;
use App\Mail\CartReminderMail;
use App\Mail\ProductAlertMail;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;

/** Marketing-style messages triggered by the backend: wishlist alerts and abandoned-cart reminders. */
class CustomerMessageController extends Controller
{
    public function productAlert(ProductAlertRequest $request): JsonResponse
    {
        $payload = $request->validated();
        $recipient = $payload['recipient'];
        Mail::to($recipient['email'], $recipient['name'])->send(new ProductAlertMail($payload));
        Log::info('Product alert sent', ['type' => $payload['type'], 'product' => $payload['product']['name']]);

        return response()->json(['status' => 'sent'], 202);
    }

    public function cartReminder(CartReminderRequest $request): JsonResponse
    {
        $payload = $request->validated();
        $recipient = $payload['recipient'];
        Mail::to($recipient['email'], $recipient['name'])->send(new CartReminderMail($payload));
        Log::info('Cart reminder sent', ['items' => count($payload['cart']['items'])]);

        return response()->json(['status' => 'sent'], 202);
    }
}
