<?php

namespace App\Http\Controllers;

use App\Http\Requests\OrderEventRequest;
use App\Mail\OrderEventMail;
use App\Services\InvoiceRenderer;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;

class OrderEventController extends Controller
{
    public function __invoke(OrderEventRequest $request, InvoiceRenderer $invoices): JsonResponse
    {
        $payload = $request->validated();
        $recipient = $payload['recipient'];

        // The order confirmation carries the PDF invoice as an attachment.
        $invoice = $payload['event'] === 'ORDER_PLACED'
            ? $invoices->render($payload['order'], $recipient)
            : null;

        Mail::to($recipient['email'], $recipient['name'])->send(new OrderEventMail($payload, $invoice));
        Log::info('Order e-mail sent', ['event' => $payload['event'], 'order' => $payload['order']['reference']]);

        return response()->json(['status' => 'sent'], 202);
    }
}
