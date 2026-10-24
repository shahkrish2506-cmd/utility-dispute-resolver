package com.utilityresolver.storage;

import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.model.Apartment;
import com.utilityresolver.model.Bill;
import com.utilityresolver.model.Dispute;
import com.utilityresolver.model.Payment;
import com.utilityresolver.model.Roommate;
import com.utilityresolver.util.AuditLogger;
import com.utilityresolver.util.IdGenerator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Owns all repositories and files inside the data folder:
 * apartment.properties, roommates.csv, bills.csv, payments.csv, disputes.csv,
 * finalized_months.txt and audit.log.
 */
public class DataStore {
    private final Path dataDir;
    private final Path reportsDir;
    private Apartment apartment;
    private final CsvRepository<Roommate> roommates;
    private final CsvRepository<Bill> bills;
    private final CsvRepository<Payment> payments;
    private final CsvRepository<Dispute> disputes;
    private final Set<YearMonth> finalizedMonths = new TreeSet<>();
    private final AuditLogger audit;

    private final IdGenerator roommateIds = new IdGenerator("R");
    private final IdGenerator billIds = new IdGenerator("B");
    private final IdGenerator paymentIds = new IdGenerator("P");
    private final IdGenerator disputeIds = new IdGenerator("D");

    public DataStore(Path dataDir, Path reportsDir) {
        this.dataDir = dataDir;
        this.reportsDir = reportsDir;
        this.roommates = new CsvRepository<>(dataDir.resolve("roommates.csv"), new Mappers.RoommateMapper(), "Roommate");
        this.bills = new CsvRepository<>(dataDir.resolve("bills.csv"), new Mappers.BillMapper(), "Bill");
        this.payments = new CsvRepository<>(dataDir.resolve("payments.csv"), new Mappers.PaymentMapper(), "Payment");
        this.disputes = new CsvRepository<>(dataDir.resolve("disputes.csv"), new Mappers.DisputeMapper(), "Dispute");
        this.audit = new AuditLogger(dataDir.resolve("audit.log"));
    }

    public void loadAll() throws IOException, ValidationException {
        Files.createDirectories(dataDir);
        Files.createDirectories(reportsDir);
        loadApartment();
        roommates.load();
        bills.load();
        payments.load();
        disputes.load();
        loadFinalizedMonths();
        apartment.clearRoommates();
        for (Roommate r : roommates.findAll()) {
            apartment.addRoommate(r);
        }
        roommateIds.syncWith(roommates.ids());
        billIds.syncWith(bills.ids());
        paymentIds.syncWith(payments.ids());
        disputeIds.syncWith(disputes.ids());
    }

    private void loadApartment() throws IOException, ValidationException {
        Path f = dataDir.resolve("apartment.properties");
        Properties p = new Properties();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        }
        apartment = new Apartment(p.getProperty("name", "My Apartment"), p.getProperty("address", ""));
    }

    public void saveApartment() throws IOException {
        Properties p = new Properties();
        p.setProperty("name", apartment.getName());
        p.setProperty("address", apartment.getAddress());
        try (Writer w = Files.newBufferedWriter(dataDir.resolve("apartment.properties"), StandardCharsets.UTF_8)) {
            p.store(w, "Shared Apartment Utility & Dispute Resolver");
        }
    }

    private void loadFinalizedMonths() throws IOException {
        finalizedMonths.clear();
        Path f = dataDir.resolve("finalized_months.txt");
        if (Files.exists(f)) {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    finalizedMonths.add(YearMonth.parse(line.trim()));
                }
            }
        }
    }

    public void saveFinalizedMonths() throws IOException {
        List<String> lines = new ArrayList<>();
        for (YearMonth m : finalizedMonths) {
            lines.add(m.toString());
        }
        Files.write(dataDir.resolve("finalized_months.txt"), lines, StandardCharsets.UTF_8);
    }

    /** Deletes every record (used by "Reset" and before loading demo data). */
    public void wipe() throws IOException, ValidationException {
        roommates.clear();
        bills.clear();
        payments.clear();
        disputes.clear();
        finalizedMonths.clear();
        saveFinalizedMonths();
        apartment.clearRoommates();
        apartment.update("My Apartment", "");
        saveApartment();
        roommateIds.reset();
        billIds.reset();
        paymentIds.reset();
        disputeIds.reset();
        audit.clear();
    }

    public Apartment getApartment() { return apartment; }

    public CsvRepository<Roommate> roommates() { return roommates; }

    public CsvRepository<Bill> bills() { return bills; }

    public CsvRepository<Payment> payments() { return payments; }

    public CsvRepository<Dispute> disputes() { return disputes; }

    public Set<YearMonth> getFinalizedMonths() { return Collections.unmodifiableSet(finalizedMonths); }

    public Set<YearMonth> finalizedMonthsMutable() { return finalizedMonths; }

    public AuditLogger audit() { return audit; }

    public IdGenerator roommateIds() { return roommateIds; }

    public IdGenerator billIds() { return billIds; }

    public IdGenerator paymentIds() { return paymentIds; }

    public IdGenerator disputeIds() { return disputeIds; }

    public Path getDataDir() { return dataDir; }

    public Path getReportsDir() { return reportsDir; }
}
