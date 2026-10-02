-- Jednorázový import dat z Liferay tabulky (pokud existuje ve stejné DB).
-- Postgres: Liferay tvoří tabulky bez uvozovek -> DEMO_Task = demo_task, taskId = taskid.
-- Bez Liferay tabulky (např. čistá DB v testech) se import přeskočí.
DO
$$
    BEGIN
        IF to_regclass('${liferaySchema}.demo_task') IS NOT NULL THEN
            INSERT INTO task (id, title, done, create_date, group_id, company_id, user_id)
            SELECT taskid,
                   coalesce(nullif(trim(title), ''), '(untitled)'),
                   coalesce(done, false),
                   coalesce(createdate, now() at time zone 'utc'), -- Liferay ukládá UTC
                   groupid,
                   companyid,
                   userid
            FROM ${liferaySchema}.demo_task
            ON CONFLICT (id) DO NOTHING;
        END IF;

        -- Zachováváme původní ID (URL, reference) -> sekvenci posuneme za maximum
        PERFORM setval(pg_get_serial_sequence('task', 'id'),
                       (SELECT coalesce(max(id), 0) + 1 FROM task), false);
    END
$$;
