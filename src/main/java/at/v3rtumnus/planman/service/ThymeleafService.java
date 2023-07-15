package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.finance.FinancialProductType;
import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import at.v3rtumnus.planman.entity.finance.Interval;
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
}
