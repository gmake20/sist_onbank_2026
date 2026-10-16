package org.doit.onbank.employee.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.doit.onbank.employee.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

// 공용 DB의 직원 시드 데이터(E0001~E0003, data-team1-mysql.sql)를 읽기만 한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployeeRepositoryTests {

	@Autowired
	private EmployeeRepository employeeRepository;

	@Test
	void findLargestEmployeeNo() {
		Optional<Employee> last = employeeRepository.findTopByEmployeeNoStartingWithOrderByEmployeeNoDesc("E");

		assertThat(last).isPresent();
		assertThat(last.get().getEmployeeNo()).matches("E\\d{4}");
		assertThat(last.get().getEmployeeNo()).isGreaterThanOrEqualTo("E0003");
	}

	@Test
	void existsByEmail() {
		String email = employeeRepository.findTopByEmployeeNoStartingWithOrderByEmployeeNoDesc("E").orElseThrow().getEmail();

		assertThat(employeeRepository.existsByEmail(email)).isTrue();
		assertThat(employeeRepository.existsByEmail("nobody-" + System.nanoTime() + "@onbank.test")).isFalse();
	}
}
