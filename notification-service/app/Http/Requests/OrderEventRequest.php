<?php

namespace App\Http\Requests;

use App\Mail\OrderEventMail;
use Illuminate\Foundation\Http\FormRequest;

class OrderEventRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true; // authenticated by the internal.key middleware
    }

    public function rules(): array
    {
        return [
            'event' => ['required', 'in:'.implode(',', OrderEventMail::EVENTS)],
            'parcelIndex' => ['nullable', 'integer', 'min:0'],
            ...OrderPayloadRules::person('recipient'),
            ...OrderPayloadRules::order(),
        ];
    }
}
