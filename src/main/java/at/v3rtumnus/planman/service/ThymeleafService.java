package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.dto.finance.PositionVariant;
import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import at.v3rtumnus.planman.entity.finance.Interval;
import at.v3rtumnus.planman.entity.insurance.InsuranceEntryState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
public class ThymeleafService {
    public String formatTransactionType(FinancialTransactionType type){
        switch (type) {
            case BUY -> {
                return "Kauf";
            }
            case SELL -> {
                return "Verkauf";
            }
            case SAVINGS_PLAN -> {
                return "Sparplan";
            }
            case GIFT -> {
                return "Geschenk";
            }
            case SPIN_OFF -> {
                return "Merge";
            }
            case SPLIT -> {
                return "Split";
            }
            case DIVIDEND -> {
                return "Dividende";
            }
            case TAX -> {
                return "Steuer";
            }
        }

        return "-";
    }

    public String formatInterval(Interval interval) {
        switch (interval) {
            case MONTHLY -> {
                return "monatlich";
            }
            case QUARTERLY -> {
                return "quartalsweise";
            }
            case YEARLY -> {
                return "j&auml;hrlich";
            }
        }

        return "-";
    }

    public String formatNumber(BigDecimal number, int decimalDigits, String suffix) {
        if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }

        String result = String.format("%,."+ decimalDigits + "f", number);

        if (!isEmpty(suffix)) {
            return result + " " + suffix;
        }

        return result;
    }

    public String getNumberClass(BigDecimal number) {
        if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
            return "text-center";
        }

        return number.compareTo(BigDecimal.ZERO) > 0 ? "financial-value-positive" : "financial-value-negative";
    }

    public String positionIcon(String label) {
        return switch (label) {
            case "Aktien" -> "bi bi-graph-up-arrow";
            case "Aktienfonds" -> "bi bi-pie-chart-fill";
            case "ETFs" -> "bi bi-bar-chart-line-fill";
            case "Sparen" -> "bi bi-piggy-bank-fill";
            case "Vermögen" -> "bi bi-wallet2";
            case "Kredit" -> "bi bi-credit-card-2-front";
            case "Nettovermögen" -> "bi bi-cash-stack";
            default -> "bi bi-circle";
        };
    }

    public String positionCardClass(PositionVariant variant) {
        return switch (variant) {
            case TOTAL -> "revenue-card";
            case LIABILITY -> "customers-card";
            case ASSET -> "sales-card";
        };
    }

    public String insuranceStateBadgeClass(InsuranceEntryState state) {
        return switch (state) {
            case RECORDED -> "badge bg-secondary";
            case WAITING_FOR_HEALTH_INSURANCE, WAITING_FOR_PRIVATE_INSURANCE -> "badge bg-warning text-dark";
            case HEALH_INSURANCE_RECEIVED -> "badge bg-info text-dark";
            case DONE -> "badge bg-success";
        };
    }
}
