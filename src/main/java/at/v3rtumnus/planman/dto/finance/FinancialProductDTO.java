package at.v3rtumnus.planman.dto.finance;

import at.v3rtumnus.planman.entity.finance.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class FinancialProductDTO {
    private String isin;

    private String symbol;
    private String name;
    private FinancialProductType type;

    private boolean gift;

    private BigDecimal currentQuantity;
    private BigDecimal currentPrice;
    private BigDecimal currentAmount;

    private BigDecimal combinedPurchasePrice;
    private BigDecimal dividendTotal;

    private BigDecimal changeToday;
    private BigDecimal changeTotal;
    private BigDecimal percentChangeToday;
    private BigDecimal percentChangeTotal;
    private BigDecimal percentChangeYearly;

    private LocalDate purchaseDate;
    private LocalDate sellDate;

    private List<FinancialTransactionDTO> transactions;

    public FinancialProductDTO(String isin, String name, FinancialProductType type) {
        this.isin = isin;
        this.name = name;
        this.type = type;
    }

    public static FinancialProductDTO fromEntity(FinancialProduct product) {
        FinancialProductDTO financialProductDTO = new FinancialProductDTO();
        BeanUtils.copyProperties(product, financialProductDTO);

        financialProductDTO.setTransactions(product.getTransactions().stream().map(transaction -> {
            FinancialTransactionDTO transactionDTO = new FinancialTransactionDTO();
            BeanUtils.copyProperties(transaction, transactionDTO);

            return transactionDTO;
        }).collect(Collectors.toList()));

        financialProductDTO.setGift(product.getTransactions().stream().anyMatch(
                p -> p.getTransactionType().equals(FinancialTransactionType.GIFT)
        ));

        financialProductDTO.setDividendTotal(
                BigDecimal.valueOf(product.getDividends().stream().map(
                                Dividend::getAmount
                        ).mapToDouble(BigDecimal::doubleValue)
                        .sum()));


        //go through the transactions and calculate current qauntity and combined purchase price
        BigDecimal currentQuantity = BigDecimal.ZERO;
        BigDecimal combinedPurchasePriceCounter = BigDecimal.ZERO;
        BigDecimal combinedPurchasePriceDenominator = BigDecimal.ZERO;

        financialProductDTO.setSellDate(LocalDate.now());

        for (FinancialTransaction transaction : product.getTransactions()) {
            if (transaction.getTransactionType() == FinancialTransactionType.SELL) {
                currentQuantity = currentQuantity.subtract(transaction.getQuantity());

                if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    financialProductDTO.setSellDate(transaction.getTransactionDate());
                }
            } else {
                if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    financialProductDTO.setPurchaseDate(transaction.getTransactionDate());
                    financialProductDTO.setSellDate(LocalDate.now());
                    combinedPurchasePriceCounter = BigDecimal.ZERO;
                    combinedPurchasePriceDenominator = BigDecimal.ZERO;
                }
                currentQuantity = currentQuantity.add(transaction.getQuantity());

                combinedPurchasePriceCounter = combinedPurchasePriceCounter.add(transaction.getAmount());
                combinedPurchasePriceDenominator = combinedPurchasePriceDenominator.add(transaction.getQuantity());
            }
        }

        financialProductDTO.setCurrentQuantity(currentQuantity);
        financialProductDTO.setCombinedPurchasePrice(combinedPurchasePriceCounter.divide(combinedPurchasePriceDenominator, RoundingMode.HALF_UP));


        return financialProductDTO;
    }
}
