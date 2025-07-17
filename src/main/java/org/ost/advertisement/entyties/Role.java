package org.ost.advertisement.entyties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("role")
@Getter
@Setter
public class Role {

	@Id
	private Long id;
	private String code;
	private String name;
	private String description;
}
