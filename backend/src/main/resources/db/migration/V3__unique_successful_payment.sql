-- Critical defense-in-depth: Partial unique constraint ensuring at most one successful payment exists per booking
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_successful_payment_booking
ON payments (booking_id)
WHERE status = 'SUCCESS';
