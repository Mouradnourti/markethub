<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class InvoiceRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true; // authenticated by the internal.key middleware
    }

    public function rules(): array
    {
        return [
            ...OrderPayloadRules::person('customer'),
            ...OrderPayloadRules::order(),
        ];
    }
}
