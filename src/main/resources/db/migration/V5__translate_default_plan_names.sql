--
-- Renames existing default plans seeded in Portuguese by V3 ("Meu plano") to English
-- ("My plan"). V3 itself is left untouched since it has already been applied.
--

UPDATE public.plans SET name = 'My plan' WHERE name = 'Meu plano' AND is_default = true;
