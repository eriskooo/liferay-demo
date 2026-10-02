package com.example.tasks.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entita - náhrada za Service Builder model (Task, TaskModelImpl, TaskImpl).
 * Místo generovaných tříd jedna anotovaná třída; schéma řídí Flyway, ne service.xml.
 */
@Entity
@Table(name = "task")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private boolean done;

	// Liferay ukládá data v UTC (JVM běží s -Duser.timezone=GMT) -> Instant + hibernate.jdbc.time_zone=UTC
	@Column(name = "create_date", nullable = false, updatable = false)
	private Instant createDate;

	// Legacy reference na Liferay site / instanci / uživatele (jen u importovaných dat)
	@Column(name = "group_id")
	private Long groupId;

	@Column(name = "company_id")
	private Long companyId;

	@Column(name = "user_id")
	private Long userId;

	protected Task() {
		// pro JPA
	}

	public Task(String title) {
		this.title = title;
		this.createDate = Instant.now();
	}

	/** Přepne stav hotovo/nehotovo (obdoba TaskLocalServiceImpl.toggleDone) */
	public void toggleDone() {
		done = !done;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public Instant getCreateDate() {
		return createDate;
	}

	public Long getGroupId() {
		return groupId;
	}

	public Long getCompanyId() {
		return companyId;
	}

	public Long getUserId() {
		return userId;
	}

}
