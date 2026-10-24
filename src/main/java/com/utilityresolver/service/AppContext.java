package com.utilityresolver.service;

import com.utilityresolver.exception.ValidationException;
import com.utilityresolver.storage.DataStore;

import java.io.IOException;
import java.nio.file.Path;

/** Creates and wires every service together (one place to build the whole application). */
public class AppContext {
    private final DataStore store;
    private final PeriodService periods;
    private final RoommateService roommates;
    private final BillingService billing;
    private final PaymentService payments;
    private final DisputeService disputes;
    private final SettlementService settlement;
    private final ReportService reports;
    private final DemoDataService demo;

    public AppContext(Path dataDir, Path reportsDir) throws IOException, ValidationException {
        store = new DataStore(dataDir, reportsDir);
        store.loadAll();
        periods = new PeriodService(store);
        roommates = new RoommateService(store);
        billing = new BillingService(store, roommates, periods);
        payments = new PaymentService(store, roommates, periods);
        disputes = new DisputeService(store, roommates, periods);
        periods.setDisputeService(disputes);
        settlement = new SettlementService(roommates, billing, payments, disputes, periods);
        reports = new ReportService(store, settlement, roommates);
        demo = new DemoDataService(this);
    }

    public DataStore store() { return store; }

    public PeriodService periods() { return periods; }

    public RoommateService roommates() { return roommates; }

    public BillingService billing() { return billing; }

    public PaymentService payments() { return payments; }

    public DisputeService disputes() { return disputes; }

    public SettlementService settlement() { return settlement; }

    public ReportService reports() { return reports; }

    public DemoDataService demo() { return demo; }
}
