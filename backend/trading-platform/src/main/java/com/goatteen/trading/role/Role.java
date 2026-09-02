package com.goatteen.trading.role;


import jakarta.persistence.*;


@Entity
@Table(name = "roles")
public class Role {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private String name;


    public Long getId() {
        return id;
    }


    public String getName() {
        return name;
    }
}