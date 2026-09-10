-- BNET v0.8 : signalisation des appels WebRTC. Exécuter une fois dans Supabase SQL Editor.
create table if not exists public.call_signals (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users(id) on delete cascade,
  sender_number text not null,
  recipient_number text not null,
  call_id uuid not null,
  signal_type text not null check (signal_type in ('offer','answer','candidate','hangup','busy')),
  payload jsonb not null default '{}'::jsonb,
  processed boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.call_signals enable row level security;
drop policy if exists "call participants read signals" on public.call_signals;
create policy "call participants read signals" on public.call_signals for select to authenticated using (
  sender_id = auth.uid() or recipient_number = (select bnet_number from public.profiles where id = auth.uid())
);
drop policy if exists "users send own signals" on public.call_signals;
create policy "users send own signals" on public.call_signals for insert to authenticated with check (
  sender_id = auth.uid() and sender_number = (select bnet_number from public.profiles where id = auth.uid())
);
drop policy if exists "recipients process signals" on public.call_signals;
create policy "recipients process signals" on public.call_signals for update to authenticated using (
  recipient_number = (select bnet_number from public.profiles where id = auth.uid())
) with check (
  recipient_number = (select bnet_number from public.profiles where id = auth.uid())
);
create index if not exists call_signals_recipient_idx on public.call_signals(recipient_number, processed, created_at);
