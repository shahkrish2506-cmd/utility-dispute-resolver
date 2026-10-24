package com.utilityresolver.service;

import com.utilityresolver.exception.AppException;
import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.storage.DataStore;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Add / edit / remove roommates and answer occupancy questions. */
public class RoommateService {
    private final DataStore store;

    public RoommateService(DataStore store) {
        this.store = store;
    }

    public List<Roommate> all() {
        return store.roommates().findAll().stream()
                .sorted(Comparator.comparing(Roommate::getJoinDate).thenComparing(Roommate::getName))
                .collect(Collectors.toList());
    }

    /** Roommates who lived in the flat for at least one day of the month. */
    public List<Roommate> presentIn(YearMonth month) {
        return all().stream().filter(r -> r.daysPresentIn(month) > 0).collect(Collectors.toList());
    }

    public Roommate get(String id) throws AppException {
        return store.roommates().getOrThrow(id);
    }

    public String nameOf(String id) {
        return store.roommates().findById(id).map(Roommate::getName).orElse(id);
    }

    public Roommate add(String name, String email, String phone, LocalDate join, LocalDate leave)
            throws AppException, IOException {
        checkDuplicateName(name, null);
        Roommate r = new Roommate(store.roommateIds().next(), name, email, phone, join, leave);
        store.roommates().save(r);
        store.getApartment().addRoommate(r);
        store.audit().log("ROOMMATE_ADDED", r.getId() + " " + r.getName() + " from " + join);
        return r;
    }

    public Roommate update(String id, String name, String email, String phone, LocalDate join, LocalDate leave)
            throws AppException, IOException {
        Roommate r = get(id);
        checkDuplicateName(name, id);
        r.setName(name);
        r.setEmail(email);
        r.setPhone(phone);
        r.setOccupancy(join, leave);
        store.roommates().flush();
        store.audit().log("ROOMMATE_UPDATED", id + " " + r.getName() + " (" + join + " to "
                + (leave == null ? "present" : leave) + ")");
        return r;
    }

    public void remove(String id) throws AppException, IOException {
        Roommate r = get(id);
        boolean used = !store.bills().findWhere(b -> b.involves(id)).isEmpty()
                || !store.payments().findWhere(p -> p.getFromId().equals(id) || p.getToId().equals(id)).isEmpty()
                || !store.disputes().findWhere(d -> d.getRaisedById().equals(id)).isEmpty();
        if (used) {
            throw new ValidationException(r.getName() + " appears in existing bills or payments. "
                    + "Set a move-out date instead of deleting, so history stays correct.");
        }
        store.roommates().delete(id);
        store.getApartment().removeRoommate(id);
        store.audit().log("ROOMMATE_REMOVED", id + " " + r.getName());
    }

    private void checkDuplicateName(String name, String exceptId) throws ValidationException {
        if (name == null) {
            return;
        }
        for (Roommate r : store.roommates().findAll()) {
            if (r.getName().equalsIgnoreCase(name.trim()) && !r.getId().equals(exceptId)) {
                throw new ValidationException("A roommate named '" + name.trim() + "' already exists");
            }
        }
    }
}
