package org.ost.advertisement.entyties;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("user_information")
@Getter
@Setter
public class User {

	@Id
	private Long id;
	private String name;
	private Instant createdAt;
	private Instant updatedAt;

}
