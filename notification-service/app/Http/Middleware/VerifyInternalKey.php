<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/** Rejects any call that does not carry the shared service-to-service key (constant-time comparison). */
class VerifyInternalKey
{
    public function handle(Request $request, Closure $next): Response
    {
        $expected = (string) config('services.internal.key');
        $provided = (string) $request->header('X-Internal-Key', '');

        if ($expected === '' || ! hash_equals($expected, $provided)) {
            return response()->json(['message' => 'Invalid or missing internal API key'], 401);
        }

        return $next($request);
    }
}
