package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;

/**
 * Abstract base class for people in the system.
 * Demonstrates abstraction + encapsulation (private fields, validated setters).
 */
public abstract class Person implements Identifiable {
    private final String id;
    private String name;
    private String email;
    private String phone;

    protected Person(String id, String name, String email, String phone) throws ValidationException {
        this.id = id;
        setName(name);
        setEmail(email);
        this.phone = phone == null ? "" : phone.trim();
    }

    /** Each kind of person describes its own role (polymorphism). */
    public abstract String getRole();

    @Override
    public String getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is required");
        }
        this.name = name.trim();
    }

    public String getEmail() { return email; }

    public void setEmail(String email) throws ValidationException {
        String e = email == null ? "" : email.trim();
        if (!e.isEmpty() && !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Invalid email address: " + e);
        }
        this.email = e;
    }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone == null ? "" : phone.trim(); }

    @Override
    public String toString() {
        return getRole() + "[" + id + ", " + name + "]";
    }
}
