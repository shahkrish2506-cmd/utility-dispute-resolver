package com.utilityresolver.exception;

/** Thrown when an id does not match any stored roommate, bill, payment or dispute. */
public class EntityNotFoundException extends AppException {
    public EntityNotFoundException(String entity, String id) {
        super(entity + " with id '" + id + "' was not found");
    }
}
