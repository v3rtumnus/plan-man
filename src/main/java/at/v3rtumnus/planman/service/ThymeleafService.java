package at.v3rtumnus.planman.service;

import at.v3rtumnus.planman.entity.finance.FinancialTransactionType;
import org.springframework.stereotype.Service;

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
}
