--
-- Soft-delete foundation for plans (V4) — adds a nullable deleted_at marker to plans.
-- Archiving a plan (setting deleted_at) leaves its transactions, categories,
-- memberships and invitations physically intact; read paths filter it out explicitly
-- (no global row-security / @SQLRestriction). Pure SQL only.
--

ALTER TABLE public.plans ADD COLUMN deleted_at timestamp(6) without time zone;
