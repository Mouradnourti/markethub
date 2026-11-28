<?php

namespace App\Http\Requests;

use App\Mail\ProductAlertMail;
use Illuminate\Foundation\Http\FormRequest;

class ProductAlertRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true; // authenticated by the internal.key middleware
    }

    public function rules(): array
    {
        return [
            'type' => ['required', 'in:'.implode(',', ProductAlertMail::TYPES)],
            ...OrderPayloadRules::person('recipient'),
            'product' => ['required', 'array'],
            'product.name' => ['required', 'string', 'max:200'],
            'product.shopName' => ['required', 'string', 'max:120'],
            'product.currency' => ['required', 'string', 'size:3'],
            'product.price' => ['required', 'numeric', 'min:0'],
            'product.previousPrice' => ['nullable', 'numeric', 'min:0', 'required_if:type,PRICE_DROP'],
            'product.url' => ['required', 'url', 'max:500'],
        ];
    }
}
