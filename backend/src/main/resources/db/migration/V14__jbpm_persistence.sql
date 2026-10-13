-- Aşama 17: tables of the jBPM (Apache KIE 10.2.0) persistence add-ons, so process instances and user tasks
-- survive a restart (docs/adr/0017-service-catalog.md).
-- Statements copied unchanged (license headers dropped) from the add-ons kie-flyway scripts (Apache License 2.0), in order:
--   org.kie:kie-addons-persistence-jdbc:10.2.0       kie-flyway/db/persistence-jdbc/postgresql/
--   org.jbpm:jbpm-addons-usertask-storage-jpa:10.2.0 kie-flyway/db/user-tasks/postgresql/
-- kie.flyway stays disabled; when upgrading KIE, add the add-ons' new scripts as a new migration here.

-- kie-addons-persistence-jdbc: V1.35.0__create_runtime_PostgreSQL.sql
-- To be used with kie-addons-quarkus-persistence-jdbc for Quarkus or kie-addons-springboot-persistence-jdbc for SpringBoot
CREATE TABLE process_instances
(
    id              character(36)     NOT NULL,
    payload         bytea             NOT NULL,
    process_id      character varying NOT NULL,
    version         bigint,
    process_version character varying,
    CONSTRAINT process_instances_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_process_instances_process_id ON process_instances (process_id, id, process_version);

CREATE TABLE correlation_instances
(
    id                     character(36)         NOT NULL,
    encoded_correlation_id character varying(36) NOT NULL UNIQUE,
    correlated_id          character varying(36) NOT NULL,
    correlation            json                  NOT NULL,
    version                bigint,
    CONSTRAINT correlation_instances_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_correlation_instances_encoded_id ON correlation_instances (encoded_correlation_id);
CREATE INDEX idx_correlation_instances_correlated_id ON correlation_instances (correlated_id);

-- kie-addons-persistence-jdbc: V10.0.0__add_business_key_PostgreSQL.sql
CREATE TABLE business_key_mapping (
     business_key character (255) NOT NULL,
     process_instance_id character (36) NOT NULL,
     PRIMARY KEY (business_key),
     CONSTRAINT fk_process_instances 
     FOREIGN KEY (process_instance_id)
     REFERENCES process_instances(id)
     ON DELETE CASCADE
);


CREATE INDEX idx_business_key_process_instance_id ON business_key_mapping (process_instance_id);

-- kie-addons-persistence-jdbc: V10.0.1__alter_correlation_PostgreSQL.sql
ALTER TABLE correlation_instances
ALTER COLUMN correlation TYPE character varying;

-- kie-addons-persistence-jdbc: V10.0.2__create_event_table.sql
CREATE TABLE event_types
(
    process_instance_id character(36) NOT NULL,
    event_type character varying(256) NOT NULL,

    CONSTRAINT event_types_pk PRIMARY KEY (process_instance_id, event_type)
);

-- jbpm-addons-usertask-storage-jpa: V1.0.0__jBPM_user_task_create.sql
create table jbpm_user_tasks (
    id                    varchar(50) not null,
    user_task_id          varchar(255),
    task_priority         varchar(50),
    actual_owner          varchar(255),
    task_description      varchar(255),
    status                varchar(255),
    termination_type      varchar(255),
    external_reference_id varchar(255),
    task_name             varchar(255),
    primary key (id)
);

create table jbpm_user_tasks_potential_users(
    task_id varchar(50) not null,
    user_id varchar(255) not null,
    primary key (task_id, user_id)
);

create table jbpm_user_tasks_potential_groups(
    task_id  varchar(50) not null,
    group_id varchar(255) not null,
    primary key (task_id, group_id)
);

create table jbpm_user_tasks_admin_users (
    task_id varchar(50) not null,
    user_id varchar(255) not null,
    primary key (task_id, user_id)
);

create table jbpm_user_tasks_admin_groups (
    task_id  varchar(50) not null,
    group_id varchar(255) not null,
    primary key (task_id, group_id)
);

create table jbpm_user_tasks_excluded_users(
    task_id varchar(50) not null,
    user_id varchar(255) not null,
    primary key (task_id, user_id)
);

create table jbpm_user_tasks_attachments (
    id          varchar(50) not null,
    name        varchar(255),
    updated_by  varchar(255),
    updated_at  timestamp(6),
    url         varchar(255),
    task_id     varchar(50) not null,
    primary key (id)
);

create table jbpm_user_tasks_comments (
    id         varchar(50) not null,
    updated_by varchar(255),
    updated_at timestamp(6),
    comment    varchar(255),
    task_id    varchar(50) not null,
    primary key (id)
);

create table jbpm_user_tasks_inputs (
    task_id         varchar(50) not null,
    input_name      varchar(255) not null,
    input_value     bytea,
    java_type       varchar(255),
    primary key (task_id, input_name)
);

create table jbpm_user_tasks_outputs (
    task_id         varchar(50) not null,
    output_name     varchar(255) not null,
    output_value    bytea,
    java_type       varchar(255),
    primary key (task_id, output_name)
);

create table jbpm_user_tasks_metadata (
    task_id        varchar(50),
    metadata_name  varchar(255) not null,
    metadata_value varchar(512),
    java_type      varchar(255),
    primary key (task_id, metadata_name)
);

alter table if exists jbpm_user_tasks_potential_users
drop constraint if exists fk_jbpm_user_tasks_potential_users_tid cascade;

alter table if exists jbpm_user_tasks_potential_users
add constraint fk_jbpm_user_fk_tasks_potential_users_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_potential_groups
drop constraint if exists fk_jbpm_user_tasks_potential_groups_tid cascade;

alter table if exists jbpm_user_tasks_potential_groups
add constraint fk_jbpm_user_tasks_potential_groups_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_admin_users
drop constraint if exists fk_jbpm_user_tasks_admin_users_tid cascade;

alter table if exists jbpm_user_tasks_admin_users
add constraint fk_jbpm_user_tasks_admin_users_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_admin_groups
drop constraint if exists fk_jbpm_user_tasks_admin_groups_tid cascade;

alter table if exists jbpm_user_tasks_admin_groups
add constraint fk_jbpm_user_tasks_admin_groups_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_excluded_users
drop constraint if exists fk_jbpm_user_tasks_excluded_users_tid cascade;

alter table if exists jbpm_user_tasks_excluded_users
add constraint fk_jbpm_user_tasks_excluded_users_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_attachments
drop constraint if exists fk_user_tasks_attachments_tid cascade;

alter table if exists jbpm_user_tasks_attachments
add constraint fk_user_tasks_attachments_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_comments
drop constraint if exists fk_user_tasks_comments_tid cascade;

alter table if exists jbpm_user_tasks_comments
add constraint fk_user_tasks_comments_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_inputs
drop constraint if exists fk_jbpm_user_tasks_inputs_tid cascade;

alter table if exists jbpm_user_tasks_inputs
add constraint fk_jbpm_user_tasks_inputs_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_outputs
drop constraint if exists fk_jbpm_user_tasks_outputs_tid cascade;

alter table if exists jbpm_user_tasks_outputs
add constraint fk_jbpm_user_tasks_outputs_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_metadata
drop constraint if exists fk_jbpm_user_tasks_metadata_tid cascade;

alter table if exists jbpm_user_tasks_metadata
add constraint fk_jbpm_user_tasks_metadata_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

-- jbpm-addons-usertask-storage-jpa: V1.0.1__jBPM_user_task_timers.sql
create table jbpm_user_tasks_deadline (
    id                  int,
    task_id             varchar(50) not null,
    notification_type   varchar(255) not null,
    notification_value  bytea,
    java_type           varchar(255),
    primary key (id)
);


create table jbpm_user_tasks_reassignment (
    id                  int,
    task_id             varchar(50) not null,
    reassignment_type   varchar(255) not null,
    reassignment_value  bytea,
    java_type           varchar(255),
    primary key (id)
);


create table jbpm_user_tasks_deadline_timer (
    task_id             varchar(50) not null,
    notification_job_id varchar(255) not null,
    notification_type   varchar(255) not null,
    notification_value  bytea,
    java_type           varchar(255),
    primary key (task_id, notification_job_id)
);



create table jbpm_user_tasks_reassignment_timer (
    task_id             varchar(50) not null,
    reassignment_job_id varchar(255) not null,
    reassignment_type   varchar(255) not null,
    reassignment_value  bytea,
    java_type           varchar(255),
    primary key (task_id, reassignment_job_id)
);

CREATE SEQUENCE jbpm_user_tasks_deadline_seq INCREMENT BY 50 OWNED BY jbpm_user_tasks_deadline.id;
CREATE SEQUENCE jbpm_user_tasks_reassignment_seq INCREMENT BY 50 OWNED BY jbpm_user_tasks_reassignment.id;

alter table if exists jbpm_user_tasks_deadline
add constraint fk_jbpm_user_tasks_deadline_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_reassignment
add constraint fk_jbpm_user_tasks_reassignment_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_deadline_timer
add constraint fk_jbpm_user_tasks_deadline_timer_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

alter table if exists jbpm_user_tasks_reassignment_timer
add constraint fk_jbpm_user_tasks_reassignment_timer_tid foreign key (task_id) references jbpm_user_tasks(id) on delete cascade;

-- jbpm-addons-usertask-storage-jpa: V1.0.2__jBPM_user_tasks_comments_increase_comment_size.sql
ALTER TABLE jbpm_user_tasks_comments ALTER COLUMN comment TYPE VARCHAR(1000);

-- jbpm-addons-usertask-storage-jpa: V1.0.3__jBPM_user_tasks_additional_indexes.sql
create index if not exists idx_usertasks_tid on jbpm_user_tasks(user_task_id);
create index if not exists idx_jbpm_user_tasks_deadline_tid on jbpm_user_tasks_deadline(task_id);
create index if not exists idx_jbpm_user_tasks_inputs_tid on jbpm_user_tasks_inputs(task_id);
create index if not exists idx_jbpm_user_tasks_outputs_tid on jbpm_user_tasks_outputs(task_id);
create index if not exists idx_jbpm_user_tasks_metadata_tid on jbpm_user_tasks_metadata(task_id);
create index if not exists idx_jbpm_user_tasks_admin_groups_tid on jbpm_user_tasks_admin_groups(task_id);
create index if not exists idx_jbpm_user_tasks_admin_groups_gid on jbpm_user_tasks_admin_groups(group_id);
create index if not exists idx_jbpm_user_tasks_admin_users_tid on jbpm_user_tasks_admin_users(task_id);
create index if not exists idx_jbpm_user_tasks_admin_users_uid on jbpm_user_tasks_admin_users(user_id);
create index if not exists idx_jbpm_user_tasks_attachments_tid on jbpm_user_tasks_attachments(task_id);
create index if not exists idx_jbpm_user_tasks_potential_groups_tid on jbpm_user_tasks_potential_groups(task_id);
create index if not exists idx_jbpm_user_tasks_potential_groups_gid on jbpm_user_tasks_potential_groups(group_id);
create index if not exists idx_jbpm_user_tasks_potential_users_tid on jbpm_user_tasks_potential_users(task_id);
create index if not exists idx_jbpm_user_tasks_potential_users_uid on jbpm_user_tasks_potential_users(user_id);
create index if not exists idx_jbpm_user_tasks_excluded_users_uid on jbpm_user_tasks_excluded_users(user_id);
create index if not exists idx_jbpm_user_tasks_excluded_users_tid on jbpm_user_tasks_excluded_users(task_id);

-- jbpm-addons-usertask-storage-jpa: V1.0.4__jBPM_usertaks_missing_indexes.sql
create index if not exists idx_jbpm_user_tasks_comments_tid on jbpm_user_tasks_comments(task_id);
create index if not exists idx_jbpm_user_tasks_reassignment_tid on jbpm_user_tasks_reassignment(task_id);
create index if not exists idx_jbpm_user_tasks_deadline_timer_tid on jbpm_user_tasks_deadline_timer(task_id);
create index if not exists idx_jbpm_user_tasks_deadline_timer_jid on jbpm_user_tasks_deadline_timer(notification_job_id);
create index if not exists idx_jbpm_user_tasks_reassignment_timer_tid on jbpm_user_tasks_reassignment_timer(task_id);
create index if not exists idx_jbpm_user_tasks_reassignment_timer_jid on jbpm_user_tasks_reassignment_timer(reassignment_job_id);

-- jbpm-addons-usertask-storage-jpa: V1.0.5__jBPM_usertasks_add_process_info.sql
ALTER TABLE jbpm_user_tasks ADD COLUMN process_instance_id VARCHAR(50);
ALTER TABLE jbpm_user_tasks ADD COLUMN process_id VARCHAR(255);
ALTER TABLE jbpm_user_tasks ADD COLUMN process_version VARCHAR(255);
ALTER TABLE jbpm_user_tasks ADD COLUMN parent_process_instance_id VARCHAR(50);
ALTER TABLE jbpm_user_tasks ADD COLUMN root_process_instance_id VARCHAR(50);
ALTER TABLE jbpm_user_tasks ADD COLUMN root_process_id VARCHAR(255);

UPDATE jbpm_user_tasks t
SET process_instance_id = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'ProcessInstanceId'
    AND m.metadata_value IS NOT NULL;

UPDATE jbpm_user_tasks t
SET process_id = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'ProcessId'
    AND m.metadata_value IS NOT NULL;

UPDATE jbpm_user_tasks t
SET process_version = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'ProcessVersion'
  AND m.metadata_value IS NOT NULL;

UPDATE jbpm_user_tasks t
SET parent_process_instance_id = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'ParentProcessInstanceId'
  AND m.metadata_value IS NOT NULL;

UPDATE jbpm_user_tasks t
SET root_process_instance_id = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'RootProcessInstanceId'
  AND m.metadata_value IS NOT NULL;

UPDATE jbpm_user_tasks t
SET root_process_id = TRIM(BOTH '"' FROM m.metadata_value)
FROM jbpm_user_tasks_metadata m
WHERE t.id = m.task_id AND m.metadata_name = 'RootProcessId'
  AND m.metadata_value IS NOT NULL;
