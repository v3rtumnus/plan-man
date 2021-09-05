package at.v3rtumnus.planman.dto.credit;

import lombok.Data;

import java.util.List;

@Data
public class SimulationData {
    private InterestChange interestChange;
    private List<AdditionalPayment> additionalPayments;
    private RegularAdditionalPayment regularAdditionalPayment;
}
