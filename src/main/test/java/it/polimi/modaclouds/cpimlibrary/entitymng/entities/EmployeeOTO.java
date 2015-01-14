package it.polimi.modaclouds.cpimlibrary.entitymng.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@Entity
@Table(name = "EmployeeOTOne", schema = "gae-test@pu")
@NamedQueries({
        @NamedQuery(name = "allEmployees", query = "SELECT e FROM EmployeeOTOne e"),
        @NamedQuery(name = "updateSalary", query = "UPDATE EmployeeOTOne e SET e.salary = :s WHERE e.name = :n")
})
public class EmployeeOTO {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "EMPLOYEE_ID")
    private String id;

    @Column(name = "NAME")
    private String name;

    @Column(name = "SALARY")
    private Long salary;

    /* an employee have one and only one phone */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "PHONE_ID")
    private Phone phone;
}