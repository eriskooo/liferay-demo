-- Simulace Liferay DB pro testy: tabulka přesně dle Service Builder tables.sql (PostgreSQL dialekt,
-- jak ji Liferay vytvoří: LONG -> bigint, DATE -> timestamp, názvy bez uvozovek = lowercase).
create table demo_task
(
    taskid     bigint not null primary key,
    groupid    bigint,
    companyid  bigint,
    userid     bigint,
    createdate timestamp null,
    title      varchar(75) null,
    done       boolean
);

insert into demo_task values (101, 20117, 20097, 20123, '2026-01-15 10:00:00', 'Legacy task from Liferay', false);
insert into demo_task values (102, 20117, 20097, 20123, null, '   ', true);
