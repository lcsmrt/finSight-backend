--
-- Shared Plans (V3) — re-scopes ownership from user_id to plan_id (+ created_by).
-- Data-preserving: provisions one default plan per existing user, backfills the new
-- columns from the old user_id, then constrains and drops the old user_id columns.
-- Pure SQL only (no psql meta-commands).
--

--
-- 1. one default plan per existing user
--
INSERT INTO public.plans (name, created_by, is_default)
    SELECT 'Meu plano', u.id, true FROM public.users u;

--
-- 2. owner membership for each user's default plan
--
INSERT INTO public.plan_memberships (plan_id, user_id, role)
    SELECT p.id, p.created_by, 'OWNER' FROM public.plans p WHERE p.is_default = true;

--
-- 3. financial_transactions: add plan_id + created_by, backfill, then constrain
--
ALTER TABLE public.financial_transactions ADD COLUMN plan_id bigint;
ALTER TABLE public.financial_transactions ADD COLUMN created_by bigint;

UPDATE public.financial_transactions ft
    SET created_by = ft.user_id,
        plan_id = (SELECT p.id FROM public.plans p
                   WHERE p.created_by = ft.user_id AND p.is_default = true);

ALTER TABLE public.financial_transactions ALTER COLUMN plan_id SET NOT NULL;
ALTER TABLE public.financial_transactions ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE ONLY public.financial_transactions
    ADD CONSTRAINT fk_ft_plan FOREIGN KEY (plan_id) REFERENCES public.plans(id);
ALTER TABLE ONLY public.financial_transactions
    ADD CONSTRAINT fk_ft_created_by FOREIGN KEY (created_by) REFERENCES public.users(id);

--
-- 4. financial_transaction_categories: add plan_id, backfill, then constrain
--    (no created_by — categories are owner-managed shared config)
--
ALTER TABLE public.financial_transaction_categories ADD COLUMN plan_id bigint;

UPDATE public.financial_transaction_categories c
    SET plan_id = (SELECT p.id FROM public.plans p
                   WHERE p.created_by = c.user_id AND p.is_default = true);

ALTER TABLE public.financial_transaction_categories ALTER COLUMN plan_id SET NOT NULL;

ALTER TABLE ONLY public.financial_transaction_categories
    ADD CONSTRAINT fk_ftc_plan FOREIGN KEY (plan_id) REFERENCES public.plans(id);

--
-- 5. re-index to plan (keep idx_financial_transactions_start_date)
--
DROP INDEX IF EXISTS idx_financial_transactions_user_id;
DROP INDEX IF EXISTS idx_financial_transactions_user_id_series_id;
DROP INDEX IF EXISTS idx_financial_transactions_user_id_start_date;

CREATE INDEX idx_ft_plan_id ON public.financial_transactions USING btree (plan_id);
CREATE INDEX idx_ft_plan_id_start_date ON public.financial_transactions USING btree (plan_id, start_date);
CREATE INDEX idx_ft_plan_id_series_id ON public.financial_transactions USING btree (plan_id, series_id);

--
-- 6. drop the old user_id FKs + columns
--
ALTER TABLE public.financial_transactions DROP CONSTRAINT fk_financial_transactions_users;
ALTER TABLE public.financial_transactions DROP COLUMN user_id;

ALTER TABLE public.financial_transaction_categories DROP CONSTRAINT fk_financial_transaction_categories_users;
ALTER TABLE public.financial_transaction_categories DROP COLUMN user_id;
