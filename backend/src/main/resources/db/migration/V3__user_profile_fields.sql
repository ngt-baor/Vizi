-- Profile fields for account settings (phone, address).
alter table users add column phone varchar(40);
alter table users add column address varchar(500);
