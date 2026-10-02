create table DEMO_Task (
	taskId LONG not null primary key,
	groupId LONG,
	companyId LONG,
	userId LONG,
	createDate DATE null,
	title VARCHAR(75) null,
	done BOOLEAN
);