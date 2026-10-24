package com.utilityresolver.model;

import com.utilityresolver.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The shared apartment. AGGREGATION: an apartment "has" roommates, but a
 * Roommate object can exist on its own (e.g. after moving out it is still kept
 * for old bills).
 */
public class Apartment {
    private String name;
    private String address;
    private final List<Roommate> roommates = new ArrayList<>();

    public Apartment(String name, String address) throws ValidationException {
        update(name, address);
    }

    public final void update(String name, String address) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Apartment name is required");
        }
        this.name = name.trim();
        this.address = address == null ? "" : address.trim();
    }

    public String getName() { return name; }

    public String getAddress() { return address; }

    public void addRoommate(Roommate roommate) {
        roommates.add(roommate);
    }

    public boolean removeRoommate(String roommateId) {
        return roommates.removeIf(r -> r.getId().equals(roommateId));
    }

    public Optional<Roommate> findRoommate(String id) {
        return roommates.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    /** Read-only view so outside code cannot bypass add/remove. */
    public List<Roommate> getRoommates() {
        return Collections.unmodifiableList(roommates);
    }

    public void clearRoommates() {
        roommates.clear();
    }
}
