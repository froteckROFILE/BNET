-- BNET v0.7: profils, répertoire privé, partage de contacts et notes vocales.
-- À exécuter une seule fois dans Supabase > SQL Editor.

alter table public.profiles add column if not exists display_name text;
alter table public.profiles add column if not exists avatar_path text;

create table if not exists public.contacts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  contact_number text not null references public.profiles(bnet_number) on update cascade,
  nickname text,
  created_at timestamptz not null default now(),
  unique(owner_id, contact_number)
);

alter table public.contacts enable row level security;
drop policy if exists "users manage own contacts" on public.contacts;
create policy "users manage own contacts" on public.contacts
for all to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());

alter table public.internet_messages add column if not exists message_type text not null default 'text';
alter table public.internet_messages add column if not exists media_path text;
alter table public.internet_messages add column if not exists shared_contact_number text;

alter table public.internet_messages drop constraint if exists internet_messages_body_check;
alter table public.internet_messages add constraint internet_messages_body_check
check (
  (message_type = 'text' and char_length(body) between 1 and 4000)
  or (message_type = 'voice' and media_path is not null)
  or (message_type = 'contact' and shared_contact_number is not null)
);

insert into storage.buckets (id, name, public)
values ('bnet-private', 'bnet-private', false)
on conflict (id) do update set public = false;

drop policy if exists "users upload own BNET media" on storage.objects;
create policy "users upload own BNET media" on storage.objects
for insert to authenticated with check (
  bucket_id = 'bnet-private' and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "participants read BNET media" on storage.objects;
create policy "participants read BNET media" on storage.objects
for select to authenticated using (
  bucket_id = 'bnet-private' and (
    (storage.foldername(name))[1] = auth.uid()::text
    or (storage.foldername(name))[2] = (
      select bnet_number from public.profiles where id = auth.uid()
    )
  )
);

drop policy if exists "owners delete BNET media" on storage.objects;
create policy "owners delete BNET media" on storage.objects
for delete to authenticated using (
  bucket_id = 'bnet-private' and (storage.foldername(name))[1] = auth.uid()::text
);

create index if not exists contacts_owner_idx on public.contacts(owner_id, created_at desc);
