package com.example.demo.product.dto;

import com.example.demo.common.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class ProductRequest {

	@NotBlank
	private String name;

	private String description;

	@NotNull
	private BigDecimal price;

	private Integer stock;

	@NotNull
	private ProductType type;

	@NotBlank
	private String department;

	private Integer durationMinutes;

	private Boolean requiresSchedule;

	// getters y setters
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public Integer getStock() {
		return stock;
	}

	public void setStock(Integer stock) {
		this.stock = stock;
	}

	public String getDepartment() {
		return department;
	}

	public void setDepartment(String department) {
		this.department = department;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Boolean getRequiresSchedule() {
		return requiresSchedule;
	}

	public void setRequiresSchedule(Boolean requiresSchedule) {
		this.requiresSchedule = requiresSchedule;
	}
}