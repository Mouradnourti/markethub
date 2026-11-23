# --- Dependencies ---
FROM composer:2 AS vendor
WORKDIR /app
COPY composer.json composer.lock ./
RUN composer install --no-dev --no-scripts --no-autoloader --prefer-dist --no-interaction --ignore-platform-reqs
COPY . .
RUN composer dump-autoload --optimize --no-dev

# --- Runtime ---
FROM php:8.3-cli-alpine
WORKDIR /app
COPY --from=vendor /app /app
RUN php artisan package:discover --ansi \
    && chown -R www-data:www-data storage bootstrap/cache
USER www-data
ENV PHP_CLI_SERVER_WORKERS=4 \
    LOG_CHANNEL=stderr \
    SESSION_DRIVER=array \
    CACHE_STORE=array \
    QUEUE_CONNECTION=sync
EXPOSE 8001
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://localhost:8001/api/health || exit 1
CMD ["php", "artisan", "serve", "--host=0.0.0.0", "--port=8001"]
