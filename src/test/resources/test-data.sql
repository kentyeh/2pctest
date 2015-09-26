CREATE TABLE IF NOT EXISTS member(
  id IDENTITY primary key,
  name varchar(30) not null unique
);

insert into member(name) select 'Rollback' where not exists(select 1 from member where name='Rollback');
--insert into member(name) select 'Sylvia' where not exists(select 1 from member where name='Sylvia');
--insert into member(name) select 'Allison' where not exists(select 1 from member where name='Allison');
--insert into member(name) select 'Conley' where not exists(select 1 from member where name='Conley');
--insert into member(name) select 'Dick' where not exists(select 1 from member where name='Dick');
--insert into member(name) select 'Harry' where not exists(select 1 from member where name='Harry');