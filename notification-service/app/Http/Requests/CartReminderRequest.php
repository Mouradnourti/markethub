<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class CartReminderRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true; // authenticated by the internal.key middleware
    }

    public function rules(): array
    {
        return [
            ...OrderPayloadRules::person('recipient'),
            'cart' => ['required', 'array'],
            'cart.currency' => ['required', 'string', 'size:3'],
            'cart.subtotal' => ['required', 'numeric', 'min:0'],
            'cart.url' => ['required', 'url', 'max:500'],
            'cart.items' => ['required', 'array', 'min:1', 'max:50'],
            'cart.items.*.name' => ['required', 'string', 'max:200'],
            'cart.items.*.quantity' => ['required', 'integer', 'min:1'],
            'cart.items.*.unitPrice' => ['required', 'numeric', 'min:0'],
            'cart.items.*.lineTotal' => ['required', 'numeric', 'min:0'],
        ];
    }
}
