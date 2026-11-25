package com.clinicflow.model;

import java.util.Objects;

/**
 * Abstract base class representing a human actor in the clinic domain.
 * Demonstrates Inheritance, Encapsulation, and Polymorphism.
 */
public abstract class Person implements Identifiable<Long> {

    private final Long id;
    private String name;
    private Gender gender;
    private int age;

    /**
     * Protected constructor for subclasses.
     *
     * @param id     unique numeric identifier
     * @param name   full name of the person
     * @param gender gender (M/F)
     * @param age    age in years
     */
    protected Person(Long id, String name, Gender gender, int age) {
        if (id == null) {
            throw new IllegalArgumentException("Person ID cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Person name cannot be null or blank");
        }
        if (gender == null) {
            throw new IllegalArgumentException("Gender cannot be null");
        }
        if (age < 0 || age > 125) {
            throw new IllegalArgumentException("Age must be between 0 and 125, got: " + age);
        }
        this.id = id;
        this.name = name.trim();
        this.gender = gender;
        this.age = age;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Person name cannot be null or blank");
        }
        this.name = name.trim();
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("Gender cannot be null");
        }
        this.gender = gender;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        if (age < 0 || age > 125) {
            throw new IllegalArgumentException("Age must be between 0 and 125, got: " + age);
        }
        this.age = age;
    }

    /**
     * Abstract method describing person role in clinic.
     * Demonstrates polymorphism.
     */
    public abstract String getRoleDescription();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(id, person.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", gender=" + gender +
                ", age=" + age +
                '}';
    }
}
